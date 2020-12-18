package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.LigandBasedResidueLabeler
import cz.siret.prank.domain.labeling.ResidueLabelings
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.prediction.pockets.rescorers.PocketRescorer
import cz.siret.prank.prediction.pockets.results.PredictionSummary
import cz.siret.prank.prediction.transformation.ScoreTransformer
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.rendering.OldPymolRenderer
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.results.PredictResults
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Routine for making (and evaluating) predictions in RESIDUE PREDICTION MODE
 *
 * Backs prank commands 'predict' and 'eval-predict' when param -predict_residues = true
 *
 * TODO work in progress - not used yet
 */
@Slf4j
@CompileStatic
class PredictResiduesRoutine extends Routine {

    Dataset dataset
    String modelf

    boolean collectStats = false
    boolean produceVisualizations = params.visualizations
    boolean produceFilesystemOutput = true

    PredictResiduesRoutine(Dataset dataset, String modelf, String outdir) {
        super(outdir)
        this.dataset = dataset
        this.modelf = modelf
    }

    static PredictResiduesRoutine createForInternalUse(Dataset dataset, String modelf) {
        PredictResiduesRoutine routine = new PredictResiduesRoutine(dataset, modelf, null)
        routine.produceFilesystemOutput = false
        routine.produceVisualizations = false
        return routine
    }

    Dataset.Result execute() {
 
    }

}
