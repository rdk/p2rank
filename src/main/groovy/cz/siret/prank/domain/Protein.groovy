package cz.siret.prank.domain

import com.google.common.collect.Maps
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.SecondaryStructureUtils
import cz.siret.prank.geom.Struct
import cz.siret.prank.geom.Surface
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
import java.util.function.Function

import static cz.siret.prank.features.implementation.conservation.ConservationScore.CONSERV_LOADED_KEY
import static cz.siret.prank.features.implementation.conservation.ConservationScore.CONSERV_PATH_FUNCTION_KEY
import static cz.siret.prank.features.implementation.conservation.ConservationScore.CONSERV_SCORE_KEY
import static cz.siret.prank.geom.Struct.residueChainsFromStructure

/**
 * Encapsulates protein structure with ligands.
 */
@Slf4j
@CompileStatic
class Protein implements Parametrized {

    String name
    Structure structure
    
    /**
     * unreduced structure (when structure was reduced to single chain)
     * in case of multi model structures this refers to structure reduced to model 0
     */
    Structure fullStructure // unreduced struct

    /** all atoms of structure indexed by id */
    Atoms allAtoms

    /* protein heavy atoms from chains */
    Atoms proteinAtoms

    /** solvent exposed atoms */
    Atoms exposedAtoms

    /** solvent accessible surface */
    Surface accessibleSurface

    Surface trainSurface

//===========================================================================================================//

    /* relevant ligands (counted in prediction evaluation) */
    List<Ligand> ligands = new ArrayList<>()

    /* too small ligands */
    List<Ligand> smallLigands = new ArrayList<>()

    /* usually cofactors and biologcally unrelevant ligands */
    List<Ligand> ignoredLigands = new ArrayList<>()

    /* ligands(hetgroups) too distant from protein surface */
    List<Ligand> distantLigands = new ArrayList<>()

    List<ResidueChain> peptides = new ArrayList<>()

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

    /**
     * relevant ligand count
     */
    int getLigandCount() {
        ligands.size()
    }

    void calcuateSurfaceAndExposedAtoms() {
        if (exposedAtoms == null) {
            if (proteinAtoms.empty) {
                throw new PrankException("Protein with no chain atoms [$name]!")
            }

            exposedAtoms = getAccessibleSurface().computeExposedAtoms(proteinAtoms)

            log.info "SAS points: $accessibleSurface.points.count"
            log.info "exposed protein atoms: $exposedAtoms.count of $proteinAtoms.count"
        }
    }

    ConservationScore loadConservationScores(ProcessedItemContext itemContext) {
        log.info "Loading conservation scores for [{}]", itemContext.item.label

        Function<String, File> pathFunction = (Function<String, File>) itemContext.auxData.get(CONSERV_PATH_FUNCTION_KEY)
        ConservationScore score = ConservationScore.fromFiles(structure, pathFunction)
        secondaryData.put(CONSERV_SCORE_KEY, score)
        secondaryData.put(CONSERV_LOADED_KEY, true)

        return score
    }

    void ensureConservationLoaded(ProcessedItemContext itemContext) {
        if (!secondaryData.getOrDefault(CONSERV_LOADED_KEY, false)
                && itemContext.auxData.getOrDefault(CONSERV_SCORE_KEY,
                null) != null) {
            loadConservationScores(itemContext)
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

    Surface getAccessibleSurface() {
        if (accessibleSurface == null) {
            accessibleSurface = Surface.computeAccessibleSurface(proteinAtoms, params.solvent_radius, params.tessellation)
        }
        return accessibleSurface
    }

    Surface getTrainSurface() {
        if (trainSurface == null) {
            boolean shouldBeDistinct = params.tessellation != params.train_tessellation
            if (shouldBeDistinct) {
                trainSurface = Surface.computeAccessibleSurface(proteinAtoms, params.solvent_radius, params.train_tessellation)
                log.info "train SAS points: $trainSurface.points.count"
            } else {
                trainSurface = accessibleSurface
            }

        }
        return trainSurface
    }

    /**
     * clears generated surfaces and secondary data
     */
    void clearSecondaryData() {
        accessibleSurface = null
        trainSurface = null
        exposedAtoms = null
        secondaryData.clear()
        ligands.each { it.sasPoints = null; it.predictedPocket = null }
        clearResidues()
    }

    /**
     * @return all atoms from relevant ligands
     */
    Atoms getAllLigandAtoms() {
        Atoms res = new Atoms()
        for (lig in ligands) {
            res.addAll(lig.atoms)
        }
        return res
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
        residueChains = residueChainsFromStructure(structure)
        residues = new Residues( (List<Residue>) residueChains.collect { it.residues }.asList().flatten() )
        residueChainsByAuthorId = buildChainIndexByAuthorId(residueChains)
    }

    private void ensureResiduesCalculated() {
        if (residueChains == null) {
            calculateResidues()
        }
    }

    void clearResidues() {
        residues   = null
        exposedResidues   = null
        residueChainsByAuthorId  = null
    }

    /**
     * @return list of residues from main protein chanis
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

    void assignSecondaryStructure() {
        SecondaryStructureUtils.assignSecondaryStructure(structure)

        ensureResiduesCalculated()

        for (ResidueChain chain : residueChains) {
            for (int pos=0; pos!=chain.length; pos++) {
                Residue res = chain.residues[pos]
                if (res.ss != null) continue

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
            }
        }
    }


//===========================================================================================================//

    /**
     * @param fileName
     * @param compressed - add ".gz" to filename and compress
     * @return file name used
     */
    String saveToPdbFile(String fileName, boolean compressed = false) {
        if (compressed) {
            fileName += ".gz"
            Futils.writeGzip fileName, structure.toPDB()
        } else {
            Futils.writeFile fileName, structure.toPDB()
        }
        return fileName
    }

    public static Protein load(String pdbFileName, LoaderParams loaderParams = new LoaderParams()) {
        Protein res = new Protein()
        res.loadFile(pdbFileName, loaderParams, null)
        return res
    }

    public static Protein loadReduced(String pdbFileName, LoaderParams loaderParams, List<String> onlyChains) {
        Protein res = new Protein()
        res.loadFile(pdbFileName, loaderParams, onlyChains)
        return res
    }

    /**
     *
     * @param pdbFileName
     * @param loaderParams
     * @param chainIds if null load all
     */
    private void loadFile(String pdbFileName, LoaderParams loaderParams, @Nullable List<String> onlyChains) {

        log.info "loading protein [${Futils.absPath(pdbFileName)}]"

        name = Futils.shortName(pdbFileName)
        structure = PdbUtils.loadFromFile(pdbFileName)

        // NMR structures contain multiple models with same chain ids and atom ids
        // we always work only with first model
        if (structure.nrModels() > 1) {
            log.info "protein [{}] contains multiple models, reducing to model 0", name
            structure = Struct.reduceStructureToModel0(structure)
        }

        fullStructure = structure
        if (onlyChains != null) {
            log.info "reducing protein [{}] to chains [{}]", name, onlyChains.join(",")

            name = name + onlyChains.join("")
            structure = PdbUtils.getReducedStructure(structure, onlyChains)
        }

        calculateResidues()

        allAtoms = Atoms.allFromStructure(structure).withIndex()
        proteinAtoms = Atoms.allFromGroups(residues*.group).withoutHydrogens()

        log.info "structure atoms: $allAtoms.count"
        log.info "protein   atoms: $proteinAtoms.count"

        //Struct.getGroups(structure).each { ConsoleWriter.write "group: chid:$it.chainId pdbname:$it.PDBName ishet:" + Struct.isHetGroup(it) }

        if (proteinAtoms.empty) {
            log.error "protein with no chain atoms! [$name]"
            throw new PrankException("Protein with no chain atoms [$name]!")
        }

        if (!loaderParams.ignoreLigands) {
            // load ligands
            log.info "loading ligands"

            Ligands categorizedLigands = new Ligands().loadForProtein(this, loaderParams, pdbFileName)

            ligands = categorizedLigands.relevantLigands
            smallLigands = categorizedLigands.smallLigands
            distantLigands = categorizedLigands.distantLigands
            ignoredLigands = categorizedLigands.ignoredLigands
        } else {
            log.info "ignoring ligands"
        }

    }


}
