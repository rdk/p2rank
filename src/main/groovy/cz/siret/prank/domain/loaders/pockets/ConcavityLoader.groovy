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
    static int POCKET_GRID_TO_SURFACE_DIST = 4

    /**
     * @param ppOutputFile concavity grid points output file, something like a.001.001.001_1s69a_xxxxx_pocket.pdb
     * @return
     */
    @Override
    Prediction loadPrediction(String ppOutputFile, Protein queryProtein) {

        // a.001.001.001_1s69a_xxxxx_residue.pdb in the same dir.
        // ConCavity's *_residue.pdb is a subset of the protein consisting of pocket-touching
        // residues; we use it only to define the per-pocket surface-atom shell. The Prediction
        // itself must be tied to the original queryProtein so that downstream lookups (notably
        // conservation, which keys on protein.fileName) resolve against the actual protein,
        // not the residue subset PDB.
        String proteinFile = ppOutputFile.replaceFirst("_pocket.pdb\$", "_residue.pdb")

        Protein residueSubset = Protein.load(proteinFile, new LoaderParams())
        residueSubset.calcuateSurfaceAndExposedAtoms()
        Structure pocketStruct = PdbUtils.loadFromFile(ppOutputFile)
        List<ConcavityPocket> pockets = loadConcavityPockets(residueSubset, pocketStruct)

        return new Prediction(queryProtein, pockets)
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
            // Expand the surface-atom shell until non-empty (capped at 10 Å). Same
            // idiom as SwinSiteLoader; candidate for extraction into a shared helper.
            while (poc.surfaceAtoms.empty && distToSurface<10) {
                poc.surfaceAtoms = protein.exposedAtoms.cutoutShell(poc.gridPoints, distToSurface)
                if (poc.surfaceAtoms.empty) {
                    log.warn "no surface atoms in dist=$distToSurface from gridpoints"
                }
                distToSurface++
            }

            poc.centroid = poc.gridPoints.centroid
            poc.stats.realVolumeApprox = poc.gridPoints.count * 8 // grid points are spaced by ~2A
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
