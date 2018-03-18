package cz.siret.prank.domain

import com.google.common.collect.Maps
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Surface
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Structure
import org.biojava.nbio.structure.StructureTools

import javax.annotation.Nullable
import javax.print.PrintException
import java.util.function.Function

import static cz.siret.prank.geom.Struct.residueChainsFromStructure

/**
 * Encapsulates protein structure with ligands.
 */
@Slf4j
@CompileStatic
class Protein implements Parametrized {

    String name
    Structure structure

    /** atoms indexed by id */
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

//===========================================================================================================//

    private List<Residue> proteinResidues
    private List<Residue> exposedResidues
    private Atoms residueAtoms
    private Map<Residue.Key, Residue> proteinResidueMap
    private Map<String, ResidueChain> residueChainsMap

//===========================================================================================================//

    /**
     * secondary data calculated by feature calculators (see FeatureCalculator)
     * serves a s a temporary cache, may be cleared between experiment runs
     */
    Map<String, Object> secondaryData = new HashMap<>()

//===========================================================================================================//

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

    void loadConservationScores(ProcessedItemContext itemContext) {
        log.info "loading conservation scores"
        ConservationScore score = ConservationScore.fromFiles(structure, (Function<String, File>)
                itemContext.auxData.get(ConservationScore.conservationScoreKey))
        secondaryData.put(ConservationScore.conservationScoreKey, score)
        secondaryData.put(ConservationScore.conservationLoadedKey, true)
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

    void calculateResidues() {
        residueChainsMap = Maps.uniqueIndex(residueChainsFromStructure(structure), { it.id })

        //Struct.getProteinChainGroups(structure).collect { Residue.fromGroup(it) }.asList()
        proteinResidues = (List<Residue>) residueChains.collect { it.residues }.asList().flatten()
        proteinResidueMap = Maps.uniqueIndex(proteinResidues, { it.key })
    }

    void checkResiduesCalculated() {
        if (residueChainsMap == null) {
            calculateResidues()
        }
    }

    void clearResidues() {
        proteinResidues   = null
        exposedResidues   = null
        proteinResidueMap = null
        residueChainsMap  = null
        residueAtoms = null
    }

    /**
     * @return list of residues from main protein chanis
     */
    List<Residue> getProteinResidues() {
        checkResiduesCalculated()
        
        proteinResidues
    }

    /**
     * All atoms from proteinResidues.
     * Ideally, it should be the same as proteinAtoms,
     * however there can be minor differences due to imperfect structure model in biojava.
     */
    Atoms getResidueAtoms() {
        if (residueAtoms == null) {
            residueAtoms = Atoms.join(getProteinResidues().collect { it.atoms })
        }
        residueAtoms
    }

    List<Residue> getExposedResidues() {
        checkResiduesCalculated()

        // even lazier initialization, requires calculation of the surface
        if (exposedResidues == null) {
            calculateExposedResidues()
        }

        exposedResidues
    }

    List<ResidueChain> getResidueChains() {
        checkResiduesCalculated()

        residueChainsMap.values().asList()
    }

    ResidueChain getResidueChain(String id) {
        checkResiduesCalculated()

        residueChainsMap.get(id)
    }

    @Nullable
    Residue getResidueForAtom(Atom a) {
        checkResiduesCalculated()

        proteinResidueMap.get(Residue.Key.forAtom(a))
    }

    private void calculateExposedResidues() {
        checkResiduesCalculated()

        getExposedAtoms().each {
            Residue res = getResidueForAtom(it)
            if (res != null) {
                res.exposed = true
            }
        }
        exposedResidues = proteinResidues.findAll { it.exposed }.asList()
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

        if (onlyChains != null) {
            log.info "reducing protein [{}] to chains [{}]", name, onlyChains.join(",")

            if (onlyChains.size() > 1) {
                throw new PrintException("Reducing structure to multiple chains is not supported yet!")
            }
            String chainId = onlyChains.first()
            name = name + chainId
            // TODO replace with own method that can reduce to multiple chains
            structure = StructureTools.getReducedStructure(structure, onlyChains.first())
        }

        allAtoms = Atoms.allFromStructure(structure).withIndex()
        proteinAtoms = Atoms.onlyProteinAtoms(structure).withoutHydrogens()

        log.info "structure atoms: $allAtoms.count"
        log.info "protein   atoms: $proteinAtoms.count"

        //Struct.getGroups(structure).each { ConsoleWriter.write "group: chid:$it.chainId pdbname:$it.PDBName ishet:" + Struct.isHetGroup(it) }

        if (proteinAtoms.empty) {
            log.error "protein with no chain atoms! [$name]"
            throw new PrankException("Protein with no chain atoms [$name]!")
        }

        if (!loaderParams.ignoreLigands) {
            // load ligands

            Ligands categorizedLigands = new Ligands().loadForProtein(this, loaderParams, pdbFileName)

            ligands = categorizedLigands.relevantLigands
            smallLigands = categorizedLigands.smallLigands
            distantLigands = categorizedLigands.distantLigands
            ignoredLigands = categorizedLigands.ignoredLigands
        }

    }


}
