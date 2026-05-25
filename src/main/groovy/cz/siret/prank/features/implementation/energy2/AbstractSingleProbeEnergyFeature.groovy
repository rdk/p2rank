package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.implementation.energy.ProbePoints
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Single-scalar variant of the energy2 probe features. Returns just the
 * energy at the nearest precomputed probe point (1 dimension instead of 9).
 * Shares the same precomputed ProbePoints cache as the full 9-dim variant.
 */
@Slf4j
@CompileStatic
abstract class AbstractSingleProbeEnergyFeature extends AbstractProbeEnergyFeature {

    @Override
    List<String> getHeader() {
        return [getName()]
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        ProbePoints probePoints = (ProbePoints) context.protein.secondaryData.get(getSecondaryDataKey())

        if (!probePoints || probePoints.points.size() == 0) {
            return [0d] as double[]
        }

        Atoms cloud = probePoints.points.cutoutSphere(sasPoint, params.energy_cloud_radius)
        if (cloud.size() == 0) {
            return [0d] as double[]
        }

        double energy = ((LabeledPoint) cloud.findNearest(sasPoint)).score
        return [energy] as double[]
    }
}
