package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
abstract class PocketRescorer implements Parametrized {

    /** optional - for evaluation statistics */
    Protein ligandedProtein
    Atoms ligandAtoms = null

    boolean collectStats = false

    void collectStatsForProtein(Protein liganatedProtein) {
        collectStats = true
        this.ligandedProtein = liganatedProtein
        if (liganatedProtein != null) {
            ligandAtoms = liganatedProtein.allRelevantLigandAtoms
            // Fallback: use explicit site residue atoms for point labeling.
            // Note: this uses residue atoms, not SAS points, even when site_eval_sas_pts_as_atoms is enabled.
            // Point labeling region may therefore differ from the DCA evaluation region.
            if ((ligandAtoms == null || ligandAtoms.empty) && !liganatedProtein.sites.isEmpty()) {
                List<Atoms> siteAtomsList = liganatedProtein.sites.collect { it.atoms }
                ligandAtoms = Atoms.union(siteAtomsList)
            }
        }
    }

    /**
     * should set pocket.newScore on all pockets
     * and optionally store information to pocket.auxInfo
     */
    abstract void rescorePockets(Prediction prediction, ProcessedItemContext context);

    /**
     * Reorder pockets or make new pocket predictions, then finalize
     * rank/newRank/name/LabeledPoint.pocket assignments.
     */
    void reorderPockets(Prediction prediction, ProcessedItemContext context) {

        rescorePockets(prediction, context)

        if (!params.predictions) {
            prediction.reorderedPockets = new ArrayList<>(prediction.pockets)
            prediction.reorderedPockets = prediction.reorderedPockets.sort {
                Pocket a, Pocket b -> b.newScore <=> a.newScore
            } // descending
        }

        if (params.predictions) {
            prediction.finalizePredictedPockets()
        } else {
            prediction.finalizeRescoredPockets()
        }
    }

}
