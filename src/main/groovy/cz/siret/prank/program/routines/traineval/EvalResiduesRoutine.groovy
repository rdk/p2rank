package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.*
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.samplers.SampledPoints
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.rendering.PymolRenderer
import cz.siret.prank.program.rendering.RenderingModel
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Formatter.bton
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Evaluate a model on a dataset.
 * Residue centric (for labeled residues).
 */
@CompileStatic
class EvalResiduesRoutine extends EvalRoutine {

    private Dataset dataset
    private Model model
    private final EvalResults results = new EvalResults(1)

    EvalResiduesRoutine(Dataset dataSet, Model model, String outdir) {
        super(outdir)
        this.dataset = dataSet
        this.model = model
    }

    EvalResults getResults() {
        return results
    }

    @Override
    EvalResults execute() {
        def timer = startTimer()

        write "evaluating residue prediction on dataset [$dataset.name]"
        mkdirs(outdir)
        writeParams(outdir)
        String visDir = "$outdir/visualizations"
        if (params.visualizations) mkdirs(visDir)


        results.datasetResult = dataset.processItems { Dataset.Item item ->
            Protein protein = item.protein
            Atoms sampledPoints = SampledPoints.fromProtein(protein, false, params).points // grid or sas points

            BinaryLabeling observed = item.binaryLabeling

            List<LabeledPoint> observedPoints
            if (params.derivePointLabelingFromLigands()) {
                observedPoints = new LigandBasedPointLabeler().labelPoints(sampledPoints, protein)
            } else {
                observedPoints = new ResidueBasedPointLabeler(observed).labelPoints(sampledPoints, protein)
            }

            ResidueLabeler predictor
            List<LabeledPoint> predictedPoints = null

            if (dataset.hasPredictedResidueLabeling()) {   // load static labeling
                predictor = StaticResidueLabeler.createForDatasetItem(item)
            } else { // predict with model
                predictor = new ModelBasedResidueLabeler(model, sampledPoints, item.context).withObserved(observedPoints)
            }

            BinaryLabeling predicted = predictor.getBinaryLabeling(protein.residues, protein)
            ClassifierStats predictionStats = BinaryLabelings.eval(observed, predicted, predictor.doubleLabeling)

            if (predictor instanceof ModelBasedResidueLabeler) {
                predictedPoints = predictor.labeledPoints
            }

            synchronized (results) {
                results.residuePredictionStats.addAll(predictionStats)
                if (predictor instanceof ModelBasedResidueLabeler)
                    results.classifierStats.addAll(predictor.classifierStats) // SAS points related stats
            }

            if (params.log_cases) {
                logCases(observed, predicted, protein)
            }

            if (params.visualizations) {
                new PymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: item.protein,
                        observedLabeling: observed,
                        predictedLabeling: predicted,
                        labeledPoints: predictedPoints
                )).render()
            }

            if (!dataset.cached) {
                item.cachedPair = null
            }
        }

        results.logAndStore(outdir, model?.label)
        //logSummaryResults(dataset.label, model.label, results)

        write "evaluated residue labelng prediction in $dataset.size files"
        logTime "model evaluation finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        results.firstEvalTime = timer.time

        return results
    }

    private logCases(BinaryLabeling observed, BinaryLabeling predicted, Protein protein) {
        String cdir = mkdirs("$outdir/cases")
        StringBuilder csv = new StringBuilder("obs_id, pred_id, observed, predicted\n")
        for (int i = 0; i != observed.labeledResidues.size(); i++) {
            LabeledResidue<Boolean> obs = observed.labeledResidues[i]
            LabeledResidue<Boolean> pred = predicted.labeledResidues[i]
            csv << "$obs.residue, $pred.residue, ${bton(obs.label)}, ${bton(pred.label)}\n"
        }
        writeFile "$cdir/${protein.fileName}_residues.csv", csv
    }

}
