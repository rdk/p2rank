package cz.siret.prank.domain

import cz.siret.prank.prediction.pockets.criteria.PocketCriterium
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

/**
 * Pair of pocket prediction result and liganated structure (with correct ligand/pocket positions)
 */
@CompileStatic
@Slf4j
class PredictionPair implements Parametrized {

    String name
    /**
     * This is either query protein when rescoring (original input protein of the method we are rescoring with 'prank rescore')
     * or liganated 'control' protein when doing evaluation with 'prank eval-*'.
     * Either way it should correspond to 'protein' column in the dataset file.
     */
    Protein holoProtein
    @Nullable Protein apoProtein
    @Nullable Prediction prediction

    boolean forTraining = false

    PredictionPair() {
    }

    PredictionPair(String name, Protein protein, Protein apoProtein, Prediction prediction) {
        this.name = name
        this.holoProtein = protein
        this.apoProtein = apoProtein
        this.prediction = prediction
    }

    /**
     * @returnHolo Apo protein (if defined) or Holo protein
     */
    Protein getProtein() {

        if (apoProtein != null) {
            boolean useApo = forTraining ? params.apoholo_use_for_train : params.apoholo_use_for_eval
            if (useApo) {
                return apoProtein
            } else {
                log.debug("Apo protein '$apoProtein.name' is disabled by a parameter for ${forTraining ? 'train' : 'eval'} dataset. Using Holo instead.")
            }
        }

        return holoProtein
    }

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
        for (Ligand lig in ligands.relevantLigands) {
            if (criterium.isIdentified(lig, pocket, context)) {
                return lig
            }
        }
        return null
    }

//===========================================================================================================//

    Ligands getLigands() {
        holoProtein.ligands
    }

    int getLigandCount() {
        return ligands.relevantLigandCount
    }

//===========================================================================================================//

    List<Pocket> getFalsePositivePockets(PocketCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> !isCorrectlyPredictedPocket(p, assesor) }
    }

    List<Pocket> getCorrectlyPredictedPockets(PocketCriterium assesor) {
        prediction.pockets.findAll { Pocket p -> isCorrectlyPredictedPocket(p, assesor) }
    }

    boolean isCorrectlyPredictedPocket(Pocket pocket, PocketCriterium criterium) {
        for (Ligand lig : ligands.relevantLigands) {
            if (criterium.isIdentified(lig, pocket, new EvalContext())) {
                return true
            }
        }
        return false
    }

}
