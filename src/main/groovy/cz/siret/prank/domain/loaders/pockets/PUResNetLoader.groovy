package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Structure

import javax.annotation.Nullable

/**
 * Loader for predictions produced by PUResNet server
 */
@Slf4j
@CompileStatic
class PUResNetLoader extends PredictionLoader implements Parametrized {

    /**
     * @param predictionOutputFile path to the PUResNet pocket output directory
     *                             (a directory of {@code *pkt.pdb} files, one per pocket).
     * @param queryProtein         protein the prediction is associated with (used for
     *                             the {@code Prediction.queryProtein} binding only —
     *                             pocket atoms are loaded from the sub-PDB files).
     */
    @Override
    Prediction loadPrediction(String predictionOutputFile, @Nullable Protein queryProtein) {
        return new Prediction(queryProtein, loadPockets(predictionOutputFile, queryProtein))
    }


    List<PUResNetPocket> loadPockets(String pocketDir, Protein queryProtein) {

        List<File> pocketFiles = Futils.listFiles(pocketDir, {
            it.name.endsWith('pkt.pdb') && it.name != 'without_clus_pkt.pdb'
        })

        log.info('Found {} pocket files: {}', pocketFiles.size(), pocketFiles*.name)

        // SAS points come from queryProtein.accessibleSurface; ensure it's computed
        // before the per-pocket loop. Build the PDB-serial index on proteinAtoms so
        // the per-atom re-link below uses O(1) lookups. Both calls are idempotent.
        queryProtein.calcuateSurfaceAndExposedAtoms()
        queryProtein.proteinAtoms.withIndex()
        double sasCutoff = params.getSasCutoffDist()

        List<PUResNetPocket> res = new ArrayList<>()
        int i = 1
        for (File pocketFile : pocketFiles) {
            String absName = pocketFile.absolutePath
            log.info('Loading pocket from file {}', absName)

            Structure pocketStructure = PUResNetPdbRepair.loadPocketStructure(absName)
            Atoms pocketAtoms = Atoms.allFromStructure(pocketStructure).withIndex()

            PUResNetPocket pocket = new PUResNetPocket(pocketAtoms)
            pocket.rank = i++
            pocket.name = Sutils.removeSuffix(Futils.baseName(pocketFile.name), '.pdb')

            // Re-link to queryProtein atoms by PDB serial. PUResNet's sub-PDB preserves
            // source serials, so identity-based downstream ops (DSO/DSWO overlap,
            // BindingSite intersection) work the same way they do for FPocketLoader.
            Atoms surfaceAtoms = new Atoms()
            int dropped = 0
            for (Atom a : pocketAtoms) {
                Atom linked = queryProtein.proteinAtoms.getByID(a.PDBserial)
                if (linked != null) surfaceAtoms.add(linked)
                else dropped++
            }
            if (dropped > 0) {
                log.warn('Pocket {}: {} atom(s) not re-linked against queryProtein (serial mismatch); using {} re-linked atom(s) only',
                        pocket.name, dropped, surfaceAtoms.count)
            }
            // All atoms failed to re-link → wrong queryProtein passed in. Falling
            // back to the foreign-Structure pocketAtoms would silently re-introduce
            // the identity-mismatch bug this loader was rewritten to fix.
            if (surfaceAtoms.empty) {
                throw new PrankException(
                        "PUResNet pocket '${pocket.name}': all ${pocketAtoms.count} atom(s) failed " +
                        "to re-link against queryProtein by PDB serial — check that the queryProtein " +
                        "matches the structure PUResNet was run on.")
            }

            pocket.surfaceAtoms = surfaceAtoms
            pocket.centroid = surfaceAtoms.getCentroid()
            pocket.sasPoints = queryProtein.accessibleSurface.points.cutoutShell(surfaceAtoms, sasCutoff)

            res.add(pocket)
        }

        return res
    }

    static class PUResNetPocket extends Pocket {
        Atoms pocketAtoms  // as defined by PUResNet
        PUResNetPocket(Atoms pocketAtoms) {
            this.pocketAtoms = pocketAtoms
        }
    }

}
