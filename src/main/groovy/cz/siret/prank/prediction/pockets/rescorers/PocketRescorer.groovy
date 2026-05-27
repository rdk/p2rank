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
            prediction.outputPockets = new ArrayList<>(prediction.pockets)
            prediction.outputPockets = prediction.outputPockets.sort {
                Pocket a, Pocket b -> b.newScore <=> a.newScore
            } // descending
            prediction.rescoredPockets = new ArrayList<>(prediction.outputPockets)
        }

        // Assign algorithm rank/name to ALL pockets before filtering so that
        // filtered-out pockets in prediction.pockets carry meaningful metadata
        // for eval and debug output (instead of default rank=0, name="pocket").
        if (params.predictions) {
            int r = 1
            for (Pocket p : prediction.pockets) {
                p.rank = r
                p.name = "pocket" + r
                r++
            }
        }

        prediction.outputPockets = Prediction.filterPockets(
            prediction.outputPockets,
            params.pred_max_pockets,
            params.pred_min_pocket_score,
            params.pred_min_pocket_probability,
            params.pred_min_pockets
        )

        // Finalize output pockets: re-rank survivors 1..N for display, assign
        // lp.pocket values. In predict mode this overwrites rank/name on
        // surviving pockets with their output rank (which may differ from the
        // algorithm rank assigned above if filtering removed pockets).
        if (params.predictions) {
            prediction.finalizePredictedPockets()
        } else {
            prediction.finalizeRescoredPockets()
        }
    }

}
