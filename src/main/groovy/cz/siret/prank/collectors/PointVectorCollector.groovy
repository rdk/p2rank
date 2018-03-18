package cz.siret.prank.collectors

import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
@CompileStatic
abstract class PointVectorCollector extends VectorCollector implements Parametrized {

    FeatureExtractor extractorFactory

    PointVectorCollector(FeatureExtractor extractorFactory) {
        this.extractorFactory = extractorFactory
    }

    /**
     * Must return subset of points with observed value set
     */
    abstract List<LabeledPoint> labelPoints(Atoms points, PredictionPair pair, ProcessedItemContext context)

    @Override
    Result collectVectors(PredictionPair pair, ProcessedItemContext context) {

        FeatureExtractor proteinExtractorPrototype = extractorFactory.createPrototypeForProtein(pair.prediction.protein, context)
        FeatureExtractor proteinExtractor = (proteinExtractorPrototype as PrankFeatureExtractor).createInstanceForWholeProtein()

        Atoms points = proteinExtractor.sampledPoints
        List<LabeledPoint> labeledPoints = labelPoints(points, pair, context)
        Result res = new Result(labeledPoints.size())

        for (LabeledPoint point : labeledPoints) {
            try {
                FeatureVector vect = proteinExtractor.calcFeatureVector(point)

                boolean positive = point.observed
                res.addBinary(vect.array, positive)

            } catch (Exception e) {
                if (params.fail_fast) {
                    throw new PrankException("failed extraction for point", e)
                } else {
                    log.error("skipping extraction for point", e)
                }
            }
        }

        proteinExtractorPrototype.finalizeProteinPrototype()
        return res
    }

    @Override
    List<String> getHeader() {
        return extractorFactory.vectorHeader + "class"
    }

}
