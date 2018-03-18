package cz.siret.prank.domain

import cz.siret.prank.program.routines.results.EvalContext
import cz.siret.prank.score.pocketcriteria.PocketCriterium
import groovy.transform.CompileStatic

/**
 * Pair of pocket prediction result and liganated structure (with correct ligand/pocket positions)
 */
@CompileStatic
class PredictionPair {

    String name
    Prediction prediction
    /**
     * This is either query protein when rescoring (original input protein of the method we are rescoring with 'prank rescore')
     * or liganated 'cntrol' protein when doing evaluation with 'prank eval-*'.
     * Either way it should correspond to 'protein' column in the dataset file.
     */
    Protein queryProtein
    // Function<String, File> conservationPathForChain  // unused or used by webapp?

    /**
     * first is 1
     * @return ... -1 = not identified
     */
    static int rankOfIdentifiedPocket(Ligand ligand, List<Pocket> pockets, PocketCriterium criterium, EvalContext context) {

        int rank = 1
        for (Pocket pocket in pockets) {
            if (criterium.isIdentified(ligand, pocket, context)) {
                return rank
            }
            rank++
        }

        return -1
    }

    /**
     * @return null if pocket has no ligand
     */
    Ligand findLigandForPocket(Pocket pocket, PocketCriterium criterium, EvalContext context) {
        for (Ligand lig in queryProtein.ligands) {
            if (criterium.isIdentified(lig, pocket, context)) {
                return lig
            }
        }
        return null
    }

    int getLigandCount() {
        queryProtein.ligands.size()
    }

    int getIgnoredLigandCount() {
        queryProtein.ignoredLigands.size()
    }

    int getSmallLigandCount() {
        queryProtein.smallLigands.size()
    }

    int getDistantLigandCount() {
        queryProtein.distantLigands.size()
    }

    List<Pocket> getFalsePositivePockets(PocketCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> !isCorrectlyPredictedPocket(p, assesor) }
    }

    List<Pocket> getCorrectlyPredictedPockets(PocketCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> isCorrectlyPredictedPocket(p, assesor) }
    }

    boolean isCorrectlyPredictedPocket(Pocket pocket, PocketCriterium criterium) {
        for (Ligand lig : queryProtein.ligands) {
            if (criterium.isIdentified(lig, pocket, new EvalContext())) {
                return true
            }
        }
        return false
    }

}
