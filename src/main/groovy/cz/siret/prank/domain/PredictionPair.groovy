package cz.siret.prank.domain

import cz.siret.prank.prediction.pockets.criteria.PocketCriterium
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

import javax.annotation.Nullable

/**
 * Pair of pocket prediction result and liganated structure (with correct ligand/pocket positions)
 */
@CompileStatic
class PredictionPair {

    String name
    /**
     * This is either query protein when rescoring (original input protein of the method we are rescoring with 'prank rescore')
     * or liganated 'control' protein when doing evaluation with 'prank eval-*'.
     * Either way it should correspond to 'protein' column in the dataset file.
     */
    Protein protein
    @Nullable Prediction prediction
    
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
        for (Ligand lig in protein.ligands) {
            if (criterium.isIdentified(lig, pocket, context)) {
                return lig
            }
        }
        return null
    }

    int getLigandCount() {
        protein.ligands.size()
    }

    int getIgnoredLigandCount() {
        protein.ignoredLigands.size()
    }

    int getSmallLigandCount() {
        protein.smallLigands.size()
    }

    int getDistantLigandCount() {
        protein.distantLigands.size()
    }

    List<Pocket> getFalsePositivePockets(PocketCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> !isCorrectlyPredictedPocket(p, assesor) }
    }

    List<Pocket> getCorrectlyPredictedPockets(PocketCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> isCorrectlyPredictedPocket(p, assesor) }
    }

    boolean isCorrectlyPredictedPocket(Pocket pocket, PocketCriterium criterium) {
        for (Ligand lig : protein.ligands) {
            if (criterium.isIdentified(lig, pocket, new EvalContext())) {
                return true
            }
        }
        return false
    }

}
