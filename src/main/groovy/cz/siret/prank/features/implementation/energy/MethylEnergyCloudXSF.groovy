package cz.siret.prank.features.implementation.energy

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.StatSample2
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.utils.MathUtils.nanToZero

/**
 * SAS point feature: vdW-only methyl probe energy (no hydrogens).
 * Per-SAS-point feature vector: [nearest, mean, min, vpa, relstd] over the local probe-point cloud.
 * Units: kcal/mol. More negative = more favorable.
 */
@Slf4j
@CompileStatic
class MethylEnergyCloudXSF extends AbstractMethylEnergyCloudSF {

    static final String NAME = "energy-cloudx-ch3"

    @Override String getName() { NAME }

    @Override
    List<String> getHeader() {
        return ["nearest", "mean", "min",  "vpa", "relstd"]
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        ProbePoints probePoints = getProbePoints(context.protein)
        Atoms cloudPoints = probePoints.points.cutoutSphere(sasPoint, params.energy_cloud_radius)

        if (cloudPoints.size() == 0) {
            log.warn("No probe points found in cloud for SAS point, returning 0.0 energy")
            return [0, 0, 0, 0, 0] as double[]
        }

        double nearestPointEnergy = ((LabeledPoint) cloudPoints.findNearest(sasPoint)).score
        StatSample2 stats = new StatSample2(extractScores(cloudPoints))

        return [
            nearestPointEnergy,
            stats.mean,
            stats.min,
            stats.vpa,
            nanToZero(stats.relativeStddev),
        ] as double[]
    }
}
