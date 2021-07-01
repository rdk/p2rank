package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.utils.PdbUtils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.Structure

/**
 * Loader for predictions produced by ConCavity
 */
@Slf4j
class ConcavityLoader extends PredictionLoader {

    /**
     * distance from pocket grid points to protein surface atoms
     */
    static double POCKET_GRID_TO_SURFACE_DIST = 4

    /**
     * @param ppOutputFile concavity grid points output file, something like a.001.001.001_1s69a_xxxxx_pocket.pdb
     * @return
     */
    @Override
    Prediction loadPrediction(String ppOutputFile, Protein liganatedProtein) {

        // a.001.001.001_1s69a_xxxxx_residue.pdb in the same dir
        String proteinFile = ppOutputFile.replaceFirst("_pocket.pdb\$", "_residue.pdb")

        Protein protein = Protein.load(proteinFile, new LoaderParams())
        protein.calcuateSurfaceAndExposedAtoms()
        Structure pocketStruct = PdbUtils.loadFromFile(ppOutputFile)
        List<ConcavityPocket> pockets = loadConcavityPockets(protein, pocketStruct)

        return new Prediction(protein, pockets)
    }

    List<ConcavityPocket> loadConcavityPockets(Protein protein, Structure pocketStruct) {

        List<ConcavityPocket> res = new ArrayList<>()

        int rank = 1
        Struct.getHetGroups(pocketStruct).each { Group g ->

            ConcavityPocket poc = new ConcavityPocket()
            poc.name = "pocket.$rank"
            poc.rank = rank
            poc.gridPoints = Atoms.allFromGroup(g)

            double concavityGridValue = poc.gridPoints.list.first().getTempFactor()
            poc.newScore = concavityGridValue

            log.info "POCKET_SCORE: $poc.newScore"

            if (poc.gridPoints.empty) {
                log.error "trying to load pocket with no gridpoints [$poc.name in $protein.name]"
            }

            poc.gridPoints.each { Atom a -> a.setElement(Element.C)} // for center of mass calculation

            int distToSurface = POCKET_GRID_TO_SURFACE_DIST
            while (poc.surfaceAtoms.empty && distToSurface<10) {    // TODO XXX
                poc.surfaceAtoms = protein.exposedAtoms.cutoutShell(poc.gridPoints, distToSurface)
                if (poc.surfaceAtoms.empty) {
                    log.warn "no surface atoms in dist=$distToSurface from gridpoints"
                }
                distToSurface++
            }

            poc.centroid = poc.gridPoints.centerOfMass
            poc.stats.realVolumeApprox = poc.gridPoints.count * 8 // grid points are spaced by ~2A
            res.add(poc)

            log.info("$poc.name gridPoints:$poc.gridPoints.count")

            rank++
        }

        ///X correct sorting by cocnavity score encoded in temp. value od atoms in pdb file
        res = res.sort { Pocket a, Pocket b -> b.newScore <=> a.newScore } //descending
        int i = 1
        res.each {
            it.rank = i
            it.name = "pocket.$i"
            log.info(" > $it.name gridPoints:$it.gridPoints.count score:$it.newScore")
            i++
        }

        return res
    }

    public static class ConcavityPocket extends Pocket {

        Atoms gridPoints
    }

}
