package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.predict.output.PointExportData
import cz.siret.prank.program.routines.predict.output.PointsExporter
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs

/**
 * Routine for exporting SAS points with feature vectors — no model, no prediction.
 *
 * Generates SAS surface for each protein, calculates configured features
 * (including extra_features), and exports point coordinates + feature values.
 * No model is loaded, no predictions are made, no score column in output.
 *
 * Backs prank command 'export-points'.
 */
@Slf4j
@CompileStatic
class ExportPointsRoutine extends Routine {

    Dataset dataset

    ExportPointsRoutine(Dataset dataset, String outdir) {
        super(outdir)
        this.dataset = dataset
    }

    Dataset.Result execute() {
        def timer = startTimer()

        write "exporting SAS points with features for proteins from dataset [$dataset.name]"

        mkdirs(outdir)
        writeParams(outdir)
        log.info "outdir: $outdir"

        FeatureExtractor extractorFactory = FeatureExtractor.createFactory()

        LoaderParams.ignoreLigandsSwitch = true

        String format = params.export_points_format

        Dataset.Result result = dataset.processItems { Dataset.Item item ->

            PredictionPair pair = item.predictionPair

            PointExportData exportData = calculateExportData(extractorFactory, pair, item)

            PointsExporter.exportPoints(exportData, outdir, item.label, format)

            if (!dataset.cached) {
                item.cachedPair = null
            }
        }

        write "exporting points finished in $timer.formatted"
        write "results saved to directory [$outdir]"

        return result
    }

    /**
     * Calculate feature vectors for all SAS points on the protein surface.
     */
    private static PointExportData calculateExportData(FeatureExtractor extractorFactory,
                                                       PredictionPair pair,
                                                       Dataset.Item item) {
        FeatureExtractor proteinExtractor = extractorFactory.createPrototypeForProtein(pair.protein, item.context)

        try {
            PrankFeatureExtractor extractor = (PrankFeatureExtractor) proteinExtractor
            extractor = (PrankFeatureExtractor) extractor.createInstanceForWholeProtein()

            int nPoints = extractor.sampledPoints.points.count
            List<LabeledPoint> labeledPoints = new ArrayList<>(nPoints)
            List<FeatureVector> vectors = new ArrayList<>(nPoints)

            for (Atom point : extractor.sampledPoints.points) {
                labeledPoints.add(new LabeledPoint(point))
                vectors.add(extractor.calcFeatureVector(point))
            }

            return PointExportData.createWithoutScores(labeledPoints, vectors, extractor.vectorHeader)
        } finally {
            proteinExtractor.finalizeProteinPrototype()
        }
    }

}
