package cz.siret.prank.domain

import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Surface
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PDBUtils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Structure

import java.util.function.Function

/**
 * encapsulates protein structure with ligands
 */
@Slf4j
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
    Surface connollySurface

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

    /**
     * secondary data calculated by feature calculators (see FeatureCalculator)
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

            exposedAtoms = getConnollySurface().computeExposedAtoms(proteinAtoms)

            log.info "SAS points: $connollySurface.points.count"
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
            return getConnollySurface()
        }
    }

    Surface getConnollySurface() {
        if (connollySurface == null) {
            connollySurface = Surface.computeConnollySurface(proteinAtoms, params.solvent_radius, params.tessellation)
        }
        return connollySurface
    }

    Surface getTrainSurface() {
        if (trainSurface == null) {
            boolean TRAIN_SURFACE_DIFFERENT = params.tessellation != params.train_tessellation
            if (TRAIN_SURFACE_DIFFERENT) {
                trainSurface = Surface.computeConnollySurface(proteinAtoms, params.solvent_radius, params.train_tessellation)
                log.info "train SAS points: $trainSurface.points.count"
            } else {
                trainSurface = connollySurface
            }

        }
        return trainSurface
    }

    /**
     * clears generated surfaces and secondary data
     */
    void clearSecondaryData() {
        connollySurface = null
        trainSurface = null
        exposedAtoms = null
        secondaryData.clear()
        ligands.each { it.sasPoints = null; it.predictedPocket = null }
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

    public static Protein load(String pdbFileName, LoaderParams loaderParams = new LoaderParams()) {
        Protein res = new Protein()
        res.loadFile(pdbFileName, loaderParams)
        return res
    }

    /**
     */
    private void loadFile(String pdbFileName, LoaderParams loaderParams) {

        log.info "loading protein [${Futils.absPath(pdbFileName)}]"

        name = Futils.shortName(pdbFileName)
        structure = PDBUtils.loadFromFile(pdbFileName)
        allAtoms = Atoms.allFromStructure(structure).withIndex()
        proteinAtoms = Atoms.onlyProteinAtoms(structure).withoutHydrogens()

        log.info "structure atoms: $allAtoms.count"
        log.info "protein   atoms: $proteinAtoms.count"

        //Struct.getGroups(structure).each { ConsoleWriter.write "group: chid:$it.chainId pdbname:$it.PDBName ishet:" + Struct.isHetGroup(it) }

        if (proteinAtoms.empty) {
            log.error "protein with no chain atoms! [$name]"
            throw new PrankException("Protein with no chain atoms [$name]!")
        }

        if (loaderParams.ignoreLigands) return

        // load ligands

        Ligands categorizedLigands = new Ligands().loadForProtein(this, loaderParams, pdbFileName)

        ligands = categorizedLigands.relevantLigands
        smallLigands = categorizedLigands.smallLigands
        distantLigands = categorizedLigands.distantLigands
        ignoredLigands = categorizedLigands.ignoredLigands

    }


}
