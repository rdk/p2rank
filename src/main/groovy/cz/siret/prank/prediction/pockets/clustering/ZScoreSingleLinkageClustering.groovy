package cz.siret.prank.prediction.pockets.clustering

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.StatSample
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Clustering strategy that clusters points based on z-score threshold instead of raw score.
 * Calculates z-scores from point scores and applies xpoint_zscore_threshold for filtering.
 */
@Slf4j
@CompileStatic
class ZScoreSingleLinkageClustering extends ClusteringStrategy implements Parametrized {

    final double clusteringDist = params.pred_clustering_dist
    final int minClusterSize = params.pred_min_cluster_size
    final double zscoreThreshold = params.xpoint_zscore_threshold

    // Store z-score statistics for the current clustering operation
    private double mean = 0.0
    private double stdev = 0.0

    @Override
    List<Atoms> clusterPointsIntoPockets(List<LabeledPoint> points) {

        calculateZScoreStatistics(points)

        List<LabeledPoint> ligandablePoints = points.findAll { admitPoint(it) }.toList()
        List<Atoms> clusters = Struct.clusterAtoms(new Atoms(ligandablePoints), clusteringDist)
        List<Atoms> filteredClusters = clusters.findAll { it.count >= minClusterSize }.toList()

        log.info "Z-SCORE THRESHOLD: {}", zscoreThreshold
        log.info "LIGANDABLE POINTS (z-score >= threshold): {}", ligandablePoints.size()
        log.info "CLUSTERS: {}", clusters.size()
        log.info "FILTERED CLUSTERS: {}", filteredClusters.size()

        return filteredClusters
    }

    /**
     * Calculate z-score statistics (mean and standard deviation) for all valid points
     */
    private void calculateZScoreStatistics(List<LabeledPoint> points) {
        // Collect all scores for statistical calculation
        List<Double> scores = points.collect { it.score }.findAll { !Double.isNaN(it) }

        if (scores.isEmpty()) {
            log.warn "No valid scores found for z-score calculation"
            mean = 0.0
            stdev = 1.0  // Set to 1 to avoid division by zero
            return
        }

        // Calculate mean and standard deviation
        StatSample sample = new StatSample(scores)
        mean = sample.mean
        stdev = sample.stddev

        log.debug "Score statistics: mean={}, stdev={}, n={}", mean, stdev, scores.size()

        // Avoid division by zero or undefined stdev (when n=1)
        if (stdev == 0.0 || Double.isNaN(stdev) || scores.size() == 1) {
            log.warn "Standard deviation is zero/undefined (n={}), treating all points as having z-score 0", scores.size()
            stdev = 1.0  // Set to 1 so z-score calculation gives (score - mean) / 1 = (score - mean)
        }
    }

    /**
     * Calculate z-score for a specific point
     */
    private double calculateZScore(LabeledPoint point) {
        if (Double.isNaN(point.score)) {
            return Double.NaN
        }
        return (point.score - mean) / stdev
    }

    private boolean admitPoint(LabeledPoint point) {
        double zScore = calculateZScore(point)
        return !Double.isNaN(zScore) && zScore >= zscoreThreshold
    }

}
