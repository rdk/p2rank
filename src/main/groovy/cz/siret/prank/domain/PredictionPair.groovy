package cz.siret.prank.domain

import cz.siret.prank.score.criteria.IdentificationCriterium

import java.util.function.Function

/**
 * Pair of pocket prediction result and liganated structure (with correct ligand/pocket positions)
 */
class PredictionPair {

    String name
    Prediction prediction
    /**
     * This is either query protein when rescoring (original input protein of the method we are rescoring with 'prank rescore')
     * or liganated 'cntrol' protein when doing evaluation with 'prank eval-*'.
     * Either way it should correspond to 'protein' column in the dataset file.
     */
    Protein queryProtein
    Function<String, File> conservationPathForChain

    /**
     * first is 1
     * @return ... -1 = not identified
     */
    static int rankOfIdentifiedPocket(Ligand ligand, IdentificationCriterium assesor, List<Pocket> inList) {

        int rank = 1
        for (Pocket pocket in inList) {
            if (assesor.isIdentified(ligand, pocket)) {
                return rank
            }
            rank++
        }

        return -1
    }

    /**
     * @return null if pocket has no ligand
     */
    Ligand findLigandForPocket(Pocket pocket, IdentificationCriterium assesor) {
        for (Ligand lig in queryProtein.ligands) {
            if (assesor.isIdentified(lig, pocket)) {
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

    List<Pocket> getFalsePositivePockets(IdentificationCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> !isCorrectlyPredictedPocket(p, assesor) }
    }

    List<Pocket> getCorrectlyPredictedPockets(IdentificationCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> isCorrectlyPredictedPocket(p, assesor) }
    }

    boolean isCorrectlyPredictedPocket(Pocket pocket, IdentificationCriterium assesor) {
        for (Ligand lig : queryProtein.ligands) {
            if (assesor.isIdentified(lig, pocket)) {
                return true
            }
        }
        return false
    }

}
