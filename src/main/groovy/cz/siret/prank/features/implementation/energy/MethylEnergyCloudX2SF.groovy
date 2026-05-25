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
 * Per-SAS-point feature vector: 9 sub-features = [nearest] + [mean,min,vpa,relstd] × 2
 * for inner (energy_cloud_radius) and outer (energy_cloud_radius2) shell.
 * Units: kcal/mol. More negative = more favorable.
 */
@Slf4j
@CompileStatic
class MethylEnergyCloudX2SF extends AbstractMethylEnergyCloudSF {

    static final String NAME = "energy-cloudx2-ch3"

    @Override String getName() { NAME }

    @Override
    List<String> getHeader() {
        return [
            "nearest",
            "mean1", "min1", "vpa1", "relstd1",
            "mean2", "min2", "vpa2", "relstd2"
        ]
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        ProbePoints probePoints = getProbePoints(context.protein)

        Atoms.SphereLayers layers = probePoints.points.cutoutLayers(
            sasPoint, params.energy_cloud_radius, params.energy_cloud_radius2)
        Atoms cloudPoints = layers.innerSphere
        Atoms cloudPoints2 = params.xenergy_cloud2_layered ? layers.outerLayer : layers.outerSphere

        if (cloudPoints.size() == 0) {
            log.warn("No probe points found in cloud for SAS point, returning 0.0 energy")
            return [0, 0, 0, 0, 0, 0, 0, 0, 0] as double[]
        }

        double nearestPointEnergy = ((LabeledPoint) cloudPoints.findNearest(sasPoint)).score
        StatSample2 stats = new StatSample2(extractScores(cloudPoints))
        StatSample2 stats2 = new StatSample2(extractScores(cloudPoints2))

        return [
            nearestPointEnergy,
            stats.mean,
            stats.min,
            stats.vpa,
            nanToZero(stats.relativeStddev),
            nanToZero(stats2.mean),
            nanToZero(stats2.min),
            nanToZero(stats2.vpa),
            nanToZero(stats2.relativeStddev),
        ] as double[]
    }
}
