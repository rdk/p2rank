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
    List<Pocket> outputPockets

    /**
     * Rescored pocket list before output filtering. Used by eval in rescore mode
     * so that success rates reflect the full rescored ranking, not the filtered output.
     * Null in predict mode (eval uses {@link #pockets} directly).
     */
    @Nullable
    List<Pocket> rescoredPockets

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
     * LabeledPoint.pocket fields on {@link #outputPockets}. Use after
     * P2Rank prediction where rank and name are generated (not loaded
     * from an external method).
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

    /**
     * Filter pockets by score, probability, and count limits.
     * Input list must be sorted by descending newScore.
     *
     * @return new filtered list (never mutates the input; returns the same
     *         reference when all filter params are at their disabled defaults)
     */
    static List<Pocket> filterPockets(List<Pocket> pockets,
                                      int maxPockets,
                                      double minScore,
                                      double minProbability,
                                      int minPockets) {
        boolean hasScoreFilter = !Double.isNaN(minScore)
        boolean hasProbFilter  = !Double.isNaN(minProbability)

        if (maxPockets == 0 && !hasScoreFilter && !hasProbFilter && minPockets == 0) {
            return pockets
        }

        if (minPockets > 0 && maxPockets > 0 && minPockets > maxPockets) {
            log.warn "pred_min_pockets ({}) > pred_max_pockets ({}); max takes precedence",
                    minPockets, maxPockets
        }

        List<Pocket> filtered
        if (hasScoreFilter || hasProbFilter) {
            filtered = pockets.findAll { Pocket p ->
                (!hasScoreFilter || p.newScore >= minScore) &&
                (!hasProbFilter  || p.auxInfo.probaTP >= minProbability)
            }
        } else {
            filtered = new ArrayList<>(pockets)
        }

        if (minPockets > 0 && filtered.size() < minPockets) {
            filtered = new ArrayList<>(pockets.take(Math.min(minPockets, pockets.size())))
        }

        if (maxPockets > 0 && filtered.size() > maxPockets) {
            filtered = new ArrayList<>(filtered.take(maxPockets))
        }

        return filtered
    }

    private void doFinalizePockets(boolean assignRankAndName) {
        if (outputPockets == null) {
            throw new IllegalStateException("outputPockets must be set before finalizePredictedPockets/finalizeRescoredPockets")
        }

        int i = 1
        for (Pocket pocket : outputPockets) {
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
        // Iterating best-first (outputPockets is sorted by newScore descending)
        // and only writing when lp.pocket is still 0 ensures the best (lowest)
        // newRank wins for shared points.
        for (Pocket pocket : outputPockets) {
            if (pocket.labeledPoints == null) continue
            for (LabeledPoint lp : pocket.labeledPoints) {
                if (lp.pocket == 0) {
                    lp.pocket = pocket.newRank
                }
            }
        }
    }

}
