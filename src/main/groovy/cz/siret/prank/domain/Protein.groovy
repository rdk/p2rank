package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Surface
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PDBUtils
import cz.siret.prank.utils.futils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Structure

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

    /* ligands counted in predictions */
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

    void calcuateSurfaceAndExposedAtoms() {
        if (exposedAtoms==null) {
            if (proteinAtoms.empty) {
                throw new PrankException("Protein with no chain atoms [$name]!")
            }

            exposedAtoms = getConnollySurface().computeExposedAtoms(proteinAtoms)

            log.info "connolly surface points: $connollySurface.points.count"
            log.info "exposed protein atoms: $exposedAtoms.count of $proteinAtoms.count"
        }
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
        if (connollySurface==null) {
            connollySurface = Surface.computeConnollySurface(proteinAtoms, params.solvent_radius, params.tessellation)
        }
        return connollySurface
    }

    Surface getTrainSurface() {
        if (trainSurface==null) {
            boolean TRAIN_SURFACE_DIFFERENT = params.tessellation != params.train_tessellation
            if (TRAIN_SURFACE_DIFFERENT) {
                trainSurface = Surface.computeConnollySurface(proteinAtoms, params.solvent_radius, params.train_tessellation)
                log.info "train connolly surface points: $trainSurface.points.count"
            } else {
                trainSurface = connollySurface
            }

        }
        return  trainSurface
    }

    void clearCachedSurfaces() {
        connollySurface = null
        trainSurface = null
        exposedAtoms = null
    }

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

        log.info "loading protein [${futils.absPath(pdbFileName)}]"

        name = futils.shortName(pdbFileName)
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
