package cz.siret.prank.domain

import cz.siret.prank.score.criteria.IdentificationCriterium

/**
 * Pair of pocket prediction result and liganated structure (with correct ligand/pocket positions)
 */
class PredictionPair {

    String name
    Prediction prediction
    Protein liganatedProtein

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
        for (Ligand lig in liganatedProtein.ligands) {
            if (assesor.isIdentified(lig, pocket)) {
                return lig
            }
        }
        return null
    }

    int getLigandCount() {
        liganatedProtein.ligands.size()
    }

    int getIgnoredLigandCount() {
        liganatedProtein.ignoredLigands.size()
    }

    int getSmallLigandCount() {
        liganatedProtein.smallLigands.size()
    }

    int getDistantLigandCount() {
        liganatedProtein.distantLigands.size()
    }

    List<Pocket> getFalsePositivePockets(IdentificationCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> !isCorrectlyPredictedPocket(p, assesor) }
    }

    List<Pocket> getCorrectlyPredictedPockets(IdentificationCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> isCorrectlyPredictedPocket(p, assesor) }
    }

    boolean isCorrectlyPredictedPocket(Pocket pocket, IdentificationCriterium assesor) {
        for (Ligand lig : liganatedProtein.ligands) {
            if (assesor.isIdentified(lig, pocket)) {
                return true
            }
        }
        return false
    }

}
