package cz.siret.prank.domain

import cz.siret.prank.prediction.pockets.criteria.PocketCriterion
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

    PredictionPair(String name, Protein holoProtein, Protein apoProtein, Prediction prediction) {
        this.name = name
        this.holoProtein = holoProtein
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
    static int rankOfIdentifiedPocket(BindingSite site, List<Pocket> pockets, PocketCriterion criterion, EvalContext context) {

        int rank = 1
        for (Pocket pocket in pockets) {
            if (criterion.isIdentified(site, pocket, context)) {
                return rank
            }
            rank++
        }

        return -1
    }

    /**
     * @return null if pocket has no ligand
     * Note: only searches ligands, not ResidueSites. For site-based evaluation see Evaluation.findSiteForPocket().
     */
    Ligand findLigandForPocket(Pocket pocket, PocketCriterion criterion, EvalContext context) {
        for (Ligand lig in ligands.relevantLigands) {
            if (criterion.isIdentified(lig, pocket, context)) {
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

    List<Pocket> getFalsePositivePockets(PocketCriterion assesor) {
        prediction.pockets.findAll { Pocket p -> !isCorrectlyPredictedPocket(p, assesor) }
    }

    List<Pocket> getCorrectlyPredictedPockets(PocketCriterion assesor) {
        prediction.pockets.findAll { Pocket p -> isCorrectlyPredictedPocket(p, assesor) }
    }

    boolean isCorrectlyPredictedPocket(Pocket pocket, PocketCriterion criterion) {
        for (Ligand lig : ligands.relevantLigands) {
            if (criterion.isIdentified(lig, pocket, new EvalContext())) {
                return true
            }
        }
        return false
    }

}
