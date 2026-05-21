package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
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
class ConcavityLoader extends PredictionLoader implements Parametrized {

    /**
     * distance from pocket grid points to protein surface atoms
     */
    static int POCKET_GRID_TO_SURFACE_DIST = 4

    /**
     * @param ppOutputFile concavity grid points output file, something like a.001.001.001_1s69a_xxxxx_pocket.pdb
     * @return
     */
    @Override
    Prediction loadPrediction(String ppOutputFile, Protein queryProtein) {

        // The per-pocket surface-atom shell is now cut against queryProtein directly,
        // so the historical companion file *_residue.pdb (a residue subset of the
        // source protein) is no longer needed. surfaceAtoms therefore reference real
        // queryProtein atoms (identity), and DSO/DSWO/BindingSite intersection logic
        // works the same way it does for the other loaders.
        queryProtein.calcuateSurfaceAndExposedAtoms()

        Structure pocketStruct = PdbUtils.loadFromFile(ppOutputFile)
        List<ConcavityPocket> pockets = loadConcavityPockets(queryProtein, pocketStruct)

        return new Prediction(queryProtein, pockets)
    }

    List<ConcavityPocket> loadConcavityPockets(Protein queryProtein, Structure pocketStruct) {

        List<ConcavityPocket> res = new ArrayList<>()
        double sasCutoff = params.getSasCutoffDist()

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
                log.error "trying to load pocket with no gridpoints [$poc.name in $queryProtein.name]"
            }

            poc.gridPoints.each { Atom a -> a.setElement(Element.C)} // for center of mass calculation

            int distToSurface = POCKET_GRID_TO_SURFACE_DIST
            // Expand the surface-atom shell until non-empty (capped at 10 Å). Same
            // idiom as SwinSiteLoader; candidate for extraction into a shared helper.
            // Cut against queryProtein.exposedAtoms so surfaceAtoms reference real
            // queryProtein atoms (identity-correct for downstream set operations).
            while (poc.surfaceAtoms.empty && distToSurface<10) {
                poc.surfaceAtoms = queryProtein.exposedAtoms.cutoutShell(poc.gridPoints, distToSurface)
                if (poc.surfaceAtoms.empty) {
                    log.warn "no surface atoms in dist=$distToSurface from gridpoints"
                }
                distToSurface++
            }

            poc.centroid = poc.gridPoints.centroid
            poc.stats.realVolumeApprox = poc.gridPoints.count * 8 // grid points are spaced by ~2A
            poc.sasPoints = queryProtein.accessibleSurface.points.cutoutShell(poc.surfaceAtoms, sasCutoff)
            res.add(poc)

            log.info("$poc.name gridPoints:$poc.gridPoints.count")

            rank++
        }

        // Re-sort by concavity score (the score is encoded in the tempFactor of grid atoms in the input PDB).
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
