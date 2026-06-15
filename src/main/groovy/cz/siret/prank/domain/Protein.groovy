package cz.siret.prank.domain

import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.AlternateChainReducer
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.SecondaryStructureUtils
import cz.siret.prank.geom.Struct
import cz.siret.prank.geom.Surface
import cz.siret.prank.geom.transform.GeometricTransformation
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Structure
import org.biojava.nbio.structure.secstruc.SecStrucType

import javax.annotation.Nullable

import static cz.siret.prank.features.implementation.conservation.ConservationScore.CONSERV_LOADED_KEY
import static cz.siret.prank.features.implementation.conservation.ConservationScore.CONSERV_SCORE_KEY
import static cz.siret.prank.geom.Struct.residueChainsFromStructure
import static cz.siret.prank.utils.Cutils.nextInList
import static cz.siret.prank.utils.Cutils.previousInList

/**
 * Encapsulates protein structure with ligands.
 */
@Slf4j
@CompileStatic
class Protein implements Parametrized {

    String name
    String fileName
    String shortFileName
    Structure structure

    /**
     * name before any transformation (e.g. random rotation)
     * null for non-transformed proteins
     * use getOriginalName() to access
     **/
    @Nullable
    String originalName

    LoaderParams loaderParams
    
    /**
     * unreduced structure (when structure was reduced to single chain)
     * in case of multi model structures this refers to structure reduced to model 0
     */
    Structure fullStructure 

    /** all atoms of structure indexed by id */
    Atoms allAtoms

    /* protein heavy atoms from chains */
    Atoms proteinAtoms

    /** solvent exposed atoms */
    Atoms exposedAtoms

//===========================================================================================================//

    /**
     * Cache of solvent-accessible surfaces keyed by (solventRadius, tessellationLevel). The atom set is
     * fixed per protein, so a SAS surface is fully determined by those two params; every requester
     * (prediction sampling, train / train-negatives sampling, energy-probe features) shares one entry per
     * distinct (radius, tessellation), so the same surface is never computed twice for the same params.
     */
    private final Map<String, Surface> surfaceCache = new HashMap<>()

//===========================================================================================================//

    boolean apoStructure = false

    /**
     * Ligands from the structure if this structure is HOLO,
     * ot ligands from paired HOLO structure if this structure is APO.
     */
    Ligands ligands = new Ligands()

    /**
     * Ground-truth binding sites for evaluation (either ligand-defined or explicit residue-based).
     * Populated via {@link #populateSitesFromLigands()} or from explicit site definitions.
     */
    List<BindingSite> sites = new ArrayList<>()

    /**
     * Original ligands from the structure if this structure is APO.
     */
    @Nullable Ligands apoLigands = null

    List<ResidueChain> peptides = new ArrayList<>()

    /**
     * Populate sites from relevant ligands when no explicit sites are defined.
     */
    void populateSitesFromLigands() {
        if (sites.isEmpty()) {
            sites.addAll(ligands.relevantLigands)
        }
    }

//===========================================================================================================//

    private List<ResidueChain> residueChains
    private Map<String, ResidueChain> residueChainsByAuthorId 
    private Residues residues
    private Residues exposedResidues

//===========================================================================================================//

    /**
     * secondary data calculated by feature calculators (see FeatureCalculator)
     * serves a s a temporary cache, may be cleared between experiment runs
     */
    Map<String, Object> secondaryData = new HashMap<>()

//===========================================================================================================//

    String getOriginalName() {
        return originalName != null ? originalName : name
    }

    /**
     * relevant ligand count
     */
    int getLigandCount() {
        ligands.relevantLigandCount
    }

    void calcuateSurfaceAndExposedAtoms() {
        getAccessibleSurface()
        if (exposedAtoms == null) {
            exposedAtoms = getAccessibleSurface().computeExposedAtoms(proteinAtoms)
            log.info "exposed protein atoms: $exposedAtoms.count of $proteinAtoms.count"
        }
    }

    /**
     * solvent exposed protein atoms (i.e. surface atoms)
     */
    Atoms getExposedAtoms() {
        calcuateSurfaceAndExposedAtoms()
        exposedAtoms
    }

    Surface getSurface(boolean train) {
        if (train) {
            return getTrainSurface()
        } else {
            return getAccessibleSurface()
        }
    }

    /**
     * Solvent-accessible surface (SAS) for the given probe radius and tessellation, computed once per
     * distinct (solventRadius, tessellation) and cached. This is the single entry point: train / negatives
     * surfaces and energy-probe features all route through it, so identical-parameter surfaces are shared
     * (e.g. when tessellation == effectiveTrainTessellation, or when an energy feature's xenergy params
     * match the prediction surface) instead of being recomputed.
     */
    Surface getSurface(double solventRadius, int tessellationLevel) {
        String key = solventRadius + ":" + tessellationLevel
        Surface surf = surfaceCache.get(key)
        if (surf == null) {
            surf = Surface.computeAccessibleSurface(proteinAtoms, solventRadius, tessellationLevel)
            surfaceCache.put(key, surf)
            log.info "SAS points (solventRadius=$solventRadius, tessellation=$tessellationLevel): $surf.points.count"
        }
        return surf
    }

    /**
     * solvent accessible surface (SAS) points used for prediction
     */
    Surface getAccessibleSurface() {
        getSurface(params.solvent_radius, params.tessellation)
    }

    /** surface for sampling training points (shares accessibleSurface iff tessellation == effectiveTrainTessellation) */
    Surface getTrainSurface() {
        getSurface(params.solvent_radius, params.effectiveTrainTessellation)
    }

    /** surface for sampling negative training points (shares with accessible/train surface at equal tessellation) */
    Surface getTrainNegativesSurface() {
        getSurface(params.solvent_radius, params.effectiveTrainTessellationNegatives)
    }

    /**
     * clears generated surfaces and secondary data
     */
    void clearSecondaryData() {
        exposedAtoms = null
        surfaceCache.clear()
        secondaryData.clear()
        ligands.allIncludingIgnored.each { it.sasPoints = null; it.predictedPocket = null }
        for (BindingSite site : sites) {
            site.sasPoints = null
            site.predictedPocket = null
            if (site instanceof ResidueSite) {
                ((ResidueSite) site).@cachedAtoms = null
            }
        }
        clearResidues()
    }

//===========================================================================================================//

    ConservationScore loadConservationScores(ProcessedItemContext itemContext) {
        log.info "Loading conservation scores for [{}]", itemContext.item.label

        ConservationScore score = ConservationScore.loadForProtein(this, itemContext)
        secondaryData.put(CONSERV_SCORE_KEY, score)
        secondaryData.put(CONSERV_LOADED_KEY, true)

        return score
    }

    void ensureConservationLoaded(ProcessedItemContext itemContext) {
        if (!secondaryData.getOrDefault(CONSERV_LOADED_KEY, false)) {
            loadConservationScores(itemContext)
        }

        if (getConservationScore() == null) {
            String msg = "Failed to load conservation for protein [$name]"
            if (params.fail_fast) {
                throw new PrankException(msg)
            } else {
                log.warn msg
            }
        }
    }

    @Nullable
    ConservationScore getConservationScore() {
        (ConservationScore) secondaryData.get(CONSERV_SCORE_KEY)
    }

    @Nullable
    ResidueLabeling<Double> getConservationLabeling() {
        ConservationScore score = getConservationScore()
        return (score==null) ? null : score.toDoubleLabeling(this)
    }

    /**
     * Get cofactor extraction result, if cofactors were configured for this protein.
     * Stored during {@code loadStructure()} via secondaryData.
     *
     * @return ExtractionResult or null if no cofactors configured
     */
    @Nullable
    CofactorHandler.ExtractionResult getCofactorExtractionResult() {
        (CofactorHandler.ExtractionResult) secondaryData.get(CofactorHandler.EXTRACTION_RESULT_KEY)
    }

//===========================================================================================================//

    List<Ligand> getRelevantLigands() {
        return ligands.relevantLigands
    }

    /**
     * @return ignoredLigands + smallLigands + distantLigands
     */
    List<Ligand> getAllIgnoredLigands() {
        return ligands.allIgnoredLigands
    }

    /**
     * @return all atoms from relevant ligands
     */
    Atoms getAllRelevantLigandAtoms() {
        return ligands.allRelevantLigandAtoms
    }

    /**
     * @return all atoms from relevant ligands
     */
    Atoms getAllIgnoredLigandAtoms() {
        Atoms.join(allIgnoredLigands*.atoms)
    }

//===========================================================================================================//

    /**
     * Note: problem is that occasionally multiple protein chains may have the same authorID
     * In that case, the longer one is indexed. The shorter one may possibly be a peptide ligand.
     */
    private Map<String, ResidueChain> buildChainIndexByAuthorId(List<ResidueChain> chains) {
        Map<String, ResidueChain> map = new HashMap<>()
        for (ResidueChain ch : chains) {
            if (map.containsKey(ch.authorId)) {
                ResidueChain ch0 = map.get(ch.authorId)

                log.warn("Two protein chains with the same authorId: {} {}", ch.labelWithLength, ch0.labelWithLength)

                if (ch0.length < ch.length) {   // keep the longer one
                    map.put(ch.authorId, ch)
                }
            } else {
                map.put(ch.authorId, ch)
            }
        }
        return map
    }

    private void calculateResidues() {
        List<String> paptideIds = peptides*.authorId  // peptides are kept when clearing secondary caches

        residueChains = residueChainsFromStructure(structure).findAll {!(it.authorId in paptideIds) }.toList()
        residues = new Residues( (List<Residue>) residueChains.collect { it.residues }.asList().flatten() )
        residueChainsByAuthorId = buildChainIndexByAuthorId(residueChains)
    }

    private void ensureResiduesCalculated() {
        if (residueChains == null) {
            calculateResidues()
        }
    }

    void clearResidues() {
        residueChains = null
        residues = null
        exposedResidues = null
        residueChainsByAuthorId = null
        ssAssigned = false
    }

    /**
     * @return list of residues from main protein chains
     */
    Residues getResidues() {
        ensureResiduesCalculated()

        residues
    }

    Residues getExposedResidues() {
        // even lazier initialization, requires calculation of the surface
        if (exposedResidues == null) {
            calculateExposedResidues()
        }

        exposedResidues
    }

    List<ResidueChain> getResidueChains() {
        ensureResiduesCalculated()

        residueChains
    }

    ResidueChain getResidueChain(String authorId) {
        ensureResiduesCalculated()

        residueChainsByAuthorId.get(authorId)
    }

    @Nullable
    Residue getResidueForAtom(Atom a) {
        getResidues().getResidueForAtom(a)
    }

    private void calculateExposedResidues() {
        ensureResiduesCalculated()

        getExposedAtoms().each {
            Residue res = getResidueForAtom(it)
            if (res != null) {
                res.exposed = true
            }
        }
        exposedResidues = new Residues( residues.list.findAll { it.exposed }.asList() )
    }

//===========================================================================================================//

    private boolean ssAssigned = false

    void assignSecondaryStructure() {
        if (ssAssigned) {
            return
        }

        SecondaryStructureUtils.assignSecondaryStructure(structure)
        ensureResiduesCalculated()

        for (ResidueChain chain : residueChains) {
            List<Residue.SsSection> sections = new ArrayList<>()

            for (int pos=0; pos!=chain.length; pos++) {
                Residue res = chain.residues[pos]

                SecStrucType type = res.secStruct
                int pos2 = pos + 1
                while (pos2 < chain.residues.size() && chain.residues[pos2].secStruct == type) {
                    pos2++
                }

                int secLength = pos2 - pos
                Residue.SsSection section = new Residue.SsSection(type, pos, secLength)

                for (int i=0; i!=secLength; i++) {
                    chain.residues[pos+i].ss = new Residue.SsInfo(section, i)
                }

                sections.add(section)
            }

            for (int i=0; i!=sections.size(); i++) {
                sections[i].previous = previousInList(i, sections)
                sections[i].next = nextInList(i, sections)
            }

            chain.secStructSections = sections
        }

        ssAssigned = true
    }

//===========================================================================================================//

    /**
     * @param fileName
     * @param compressed - add ".gz" to filename and compress
     * @return file name used
     */
    String saveToPdbFile(String fileName, boolean compressed = false) {
        if (compressed && !fileName.endsWith(".gz")) {
            fileName += ".gz"
        }

        PdbUtils.saveToFile(structure, "pdb", fileName, compressed)

        return fileName
    }

//===========================================================================================================//

    static Protein load(String structureFile) {
        return load(structureFile, new LoaderParams())
    }

    static Protein load(String structureFile, LoaderParams loaderParams) {
        return load(structureFile, null, loaderParams)
    }

    /**
     *
     * @param structureFile
     * @param onlyChains reduce to chains, if null all chains are loaded
     * @param loaderParams
     * @return
     */
    static Protein load(String structureFile, @Nullable List<String> onlyChains, LoaderParams loaderParams) {
        Protein res = new Protein()
        res.loadFile(structureFile, loaderParams, onlyChains)
        return res
    }


    static Protein fromStructure(Structure structure, String name, String originalName, String pdbFileName, @Nullable List<String> onlyChains, LoaderParams loaderParams) {
        Protein res = new Protein()
        res.name = name
        res.originalName = originalName
        res.fileName = pdbFileName
        res.shortFileName = Futils.shortName(pdbFileName)

        res.loadStructure(structure, name, pdbFileName, onlyChains, loaderParams)

        return res
    }

    static Protein fromStructure(Structure structure, String name, String originalName, String pdbFileName, LoaderParams loaderParams) {
        return fromStructure(structure, name, originalName, pdbFileName, null, loaderParams)
    }

    /**
     * preserves fileName
     *
     * @param newName
     * @param inplaceStructureTransformation
     * @return
     */
    Protein transformedCopy(String newName, GeometricTransformation transformation) {
        Structure newStructure = PdbUtils.deepCopyStructure(structure)

        transformation.applyToStructure(newStructure)

        Structure newFullStructure = newStructure
        if (!(structure === fullStructure)) {
            // structure was reduced, apply transformation to full structure too
            newFullStructure = PdbUtils.deepCopyStructure(fullStructure)
            transformation.applyToStructure(newFullStructure)
        }

        Protein res = fromStructure(newStructure, newName, name, fileName, loaderParams)
        res.fullStructure = newFullStructure
        return res
    }

    Protein transformed(@Nullable GeometricTransformation transformation) {
        if (transformation == null) {
            return this
        }

        return transformedCopy(name + "-" + transformation.name, transformation)
    }

//===========================================================================================================//

    /**
     *
     * @param pdbFileName
     * @param loaderParams
     * @param chainIds if null load all
     */
    private void loadFile(String pdbFileName, LoaderParams loaderParams, @Nullable List<String> onlyChains) {

        log.info "loading protein [${Futils.absPath(pdbFileName)}]"

        fileName = pdbFileName
        shortFileName = Futils.shortName(pdbFileName)
        name = shortFileName
        Structure structure = PdbUtils.loadFromFile(pdbFileName)

        loadStructure(structure, name, pdbFileName, onlyChains, loaderParams)

    }

    private void loadStructure(Structure struct, String name, String pdbFileName, @Nullable List<String> onlyChains, LoaderParams loaderParams) {
        structure = struct
        this.loaderParams = loaderParams

        // NMR structures contain multiple models with same chain ids and atom ids
        // we always work only with first model
        if (struct.nrModels() > 1) {
            log.info "protein [{}] contains multiple models, reducing to model 0", name
            structure = Struct.reduceStructureToModel0(structure)
        }

        fullStructure = structure
        if (onlyChains != null) {
            log.info "reducing protein [{}] to chains [{}]", name, onlyChains.join(",")

            name = name + onlyChains.join("")
            structure = PdbUtils.reduceStructureToChains(structure, onlyChains)
        }

        // Collapse alternate-conformation chains (microheterogeneity deposited as superimposed whole chains,
        // e.g. 6een chains A/B/C/D); keeps only the primary conformation. No-op for ordinary structures.
        if (params.reduce_alternate_conformation_chains) {
            structure = AlternateChainReducer.reduceAlternateConformationChains(structure, name)
        }

        calculateResidues()

        allAtoms = Atoms.allFromStructure(structure).withIndex()
        proteinAtoms = Atoms.allFromGroups(residues*.group).withoutHydrogens()

        // Include cofactor atoms in the protein surface (Issue #79 part 2).
        // residues and proteinAtoms must be set before this - contact_res_ids specifier
        // matching consults both.
        CofactorHandler cofactorHandler = loaderParams?.cofactorHandler
        if (cofactorHandler != null && cofactorHandler.isEnabled()) {
            CofactorHandler.ExtractionResult cfResult = cofactorHandler.extractCofactorAtoms(this)

            if (params.cofactor_max_protein_dist > 0) {
                cofactorHandler.warnDistantCofactors(cfResult, proteinAtoms,
                        params.cofactor_max_protein_dist, name)
            }
            if (onlyChains != null && fullStructure != null) {
                cofactorHandler.warnChainExcludedCofactors(fullStructure, cfResult, name)
            }

            if (!cfResult.atoms.empty) {
                proteinAtoms = Atoms.join([proteinAtoms, cfResult.atoms])
            }
            cofactorHandler.logResult(cfResult, name, structure)

            secondaryData.put(CofactorHandler.EXTRACTION_RESULT_KEY, cfResult)
        }

        log.info "structure atoms: $allAtoms.count"
        log.info "protein   atoms: $proteinAtoms.count"

        //Struct.getGroups(structure).each { ConsoleWriter.write "group: chid:$it.chainId pdbname:$it.PDBName ishet:" + Struct.isHetGroup(it) }

        if (proteinAtoms.empty) {
            String msg = "Structure with no protein chain atoms! [$name]"
            if (params.fail_fast) {
                throw new PrankException(msg)
            } else {
                log.error(msg)
            }
        }

        if (!loaderParams.ignoreLigands) {
            // load ligands
            log.info "loading ligands"

            ligands = new Ligands().loadForProtein(this, loaderParams, pdbFileName)
        } else {
            log.info "ignoring ligands"
        }
    }

}
