package cz.siret.prank.features.implementation.energy

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.StatSample
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * SAS point feature: vdW-only methyl probe energy (no hydrogens).
 * Per-SAS-point feature vector: [avg, min, max, stdev] over the local probe-point cloud.
 * Units: kcal/mol. More negative = more favorable.
 *
 * Uses {@link StatSample} (percentage-scale relative stddev), unlike the X/X2/X2Full
 * variants which use {@link cz.siret.prank.utils.StatSample2} (ratio-scale).
 * The two are kept distinct to preserve feature values across model versions.
 */
@Slf4j
@CompileStatic
class MethylEnergyCloudSF extends AbstractMethylEnergyCloudSF {

    static final String NAME = "energy-cloud-ch3"

    @Override String getName() { NAME }

    @Override
    List<String> getHeader() {
        return ["avg", "min", "max", "std"]
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        ProbePoints probePoints = getProbePoints(context.protein)
        Atoms cloudPoints = probePoints.points.cutoutSphere(sasPoint, params.energy_cloud_radius)

        if (cloudPoints.size() == 0) {
            log.warn("No probe points found in cloud for SAS point, returning 0.0 energy")
            return new double[getHeader().size()]
        }

        // Build the score list without a Groovy closure (audit follow-up); StatSample's
        // internal API still takes Collection<Double>, so unboxing happens there.
        double[] scores = extractScores(cloudPoints)
        List<Double> scoreList = new ArrayList<>(scores.length)
        for (double s : scores) scoreList.add(s)
        StatSample stats = new StatSample(scoreList)

        double stdev = 0.0
        if (params.xenergy_cloud_stdev_type == 1) {
            stdev = stats.stddev
        } else if (params.xenergy_cloud_stdev_type == 2) {
            stdev = stats.relativeStdev
        }
        if (Double.isNaN(stdev)) {
            stdev = 0.0
        }

        return [stats.mean, stats.min, stats.max, stdev] as double[]
    }
}
