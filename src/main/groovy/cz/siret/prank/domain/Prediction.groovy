package cz.siret.prank.domain

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.labeling.ResidueLabelings
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

/**
 * Pocket prediction result for single protein.
 */
@Slf4j
@CompileStatic
class Prediction {

    Protein protein

    /**
     * All pockets from the prediction method or clustering step (unfiltered).
     */
    List<Pocket> pockets

    /**
     * Output pocket list: may be reordered (rescore) or filtered (predict).
     * Always a separate copy from {@link #pockets}.
     */
    List<Pocket> reorderedPockets

    /**
     *  SAS points with ligandability score for prediction and visualization.
     */
    List<LabeledPoint> labeledPoints = null

    @Nullable
    ResidueLabelings residueLabelings


    Prediction(Protein protein, List<? extends Pocket> pockets) {
        this.protein = protein
        this.pockets = (List<Pocket>) pockets
    }

    int getPocketCount() {
        return pockets.size()
    }

    /**
     * Finalize predicted pockets: assign rank, newRank, name, and
     * LabeledPoint.pocket fields. Use after P2Rank prediction where
     * rank and name are generated (not loaded from an external method).
     *
     * <p>May be called more than once (e.g. early for residue labeling, then
     * again after filtering). Idempotent when pocket lists haven't changed.
     */
    void finalizePredictedPockets() {
        doFinalizePockets(true)
    }

    /**
     * Finalize rescored pockets: assign newRank and LabeledPoint.pocket
     * fields, but preserve pocket.rank and pocket.name from the external
     * prediction method (fpocket, ConCavity, etc.).
     */
    void finalizeRescoredPockets() {
        doFinalizePockets(false)
    }

    private void doFinalizePockets(boolean assignRankAndName) {
        if (reorderedPockets == null) {
            throw new IllegalStateException("reorderedPockets must be set before finalizePockets()")
        }

        int i = 1
        for (Pocket pocket : reorderedPockets) {
            pocket.newRank = i
            if (assignRankAndName) {
                pocket.rank = i
                pocket.name = "pocket" + i
            }
            i++
        }

        // Reset pass: iterate ALL pockets (including filtered-out ones) so that
        // points belonging to removed pockets don't retain stale lp.pocket values.
        for (Pocket pocket : pockets) {
            if (pocket.labeledPoints == null) continue
            for (LabeledPoint lp : pocket.labeledPoints) {
                lp.pocket = 0
            }
        }
        // Assignment pass: only surviving (reordered) pockets get numbered.
        // Extended pocket shells can overlap (extended_pocket_cutoff > 0), so a
        // single LabeledPoint can appear in multiple pocket.labeledPoints lists.
        // Iterating best-first (reorderedPockets is sorted by newScore descending)
        // and only writing when lp.pocket is still 0 ensures the best (lowest)
        // newRank wins for shared points.
        for (Pocket pocket : reorderedPockets) {
            if (pocket.labeledPoints == null) continue
            for (LabeledPoint lp : pocket.labeledPoints) {
                if (lp.pocket == 0) {
                    lp.pocket = pocket.newRank
                }
            }
        }
    }

}
