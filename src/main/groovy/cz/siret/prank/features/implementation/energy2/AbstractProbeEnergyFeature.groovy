package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.features.implementation.energy.ProbePoints
import cz.siret.prank.features.implementation.energy2.calc.EnergyCalculator
import cz.siret.prank.features.implementation.energy2.calc.EnergyCalculatorConfig
import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Surface
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.StatSample2
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.utils.MathUtils.nanToZero

/**
 * Abstract base class for Tier-1 multi-probe energy features.
 * Provides common logic for computing probe energies on SAS points and extracting cloud statistics.
 */
@Slf4j
@CompileStatic
abstract class AbstractProbeEnergyFeature extends SasFeatureCalculator implements Parametrized {

    // Immutable calculator instance
    protected EnergyCalculator calculator
    protected EnergyCalculatorConfig config

    AbstractProbeEnergyFeature() {
        initializeCalculator()
    }

    /**
     * Get the probe type that this feature calculates
     */
    abstract ProbeType getProbeType()

    /**
     * Get the secondary data key for caching probe points
     */
    abstract String getSecondaryDataKey()

    /**
     * Initialize the energy calculator with current parameters and specific probe selection
     */
    protected void initializeCalculator() {
        // Create config with only the specific probe type selected
        config = new EnergyCalculatorConfig.Builder()
            .rCutoff(params.energy_rc)
            .rOn(params.energy_ron)
            .rMin(params.energy_min_r)
            .dielectricConstant(12.0)  // Use default dielectric constant
            .enableCoulomb(true)       // Enable Coulomb by default
            .selectedProbes(EnumSet.of(getProbeType()))
            .build()

        calculator = new EnergyCalculator(config)
    }

    /**
     * Pre-process protein to compute probe energies at all SAS points
     */
    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        initializeCalculator()

        String dataKey = getSecondaryDataKey()
        if (protein.secondaryData.containsKey(dataKey)) {
            return  // already computed
        }

        List<LabeledPoint> points = calcProbePoints(protein)
        for (LabeledPoint p : points) {
            Atoms neighbourAtoms = protein.proteinAtoms.cutoutSphere(p, config.rCutoff)
            List<Double> energies = calculator.computeEnergyForPoint(p, neighbourAtoms)
            // Since we selected only one probe type, take the first (and only) energy
            p.score = energies[0]
        }

        ProbePoints probePoints = new ProbePoints(new Atoms(points).withKdTree())
        protein.secondaryData.put(dataKey, probePoints)
    }

    /**
     * Calculate probe points on the protein surface
     */
    protected List<LabeledPoint> calcProbePoints(Protein protein) {
        Surface surf = Surface.computeAccessibleSurface(
            protein.proteinAtoms,
            params.xenergy_solvent_radius,
            params.xenergy_tessellation
        )

        List<LabeledPoint> res = new ArrayList<>(surf.points.size())
        for (Atom point : surf.points) {
            res.add(new LabeledPoint(point, false)) // initially unlabeled
        }

        return res
    }

    @Override
    List<String> getHeader() {
        return [
            "nearest",
            "mean1", "min1", "vpa1", "relstd1",
            "mean2", "min2", "vpa2", "relstd2"
        ]
    }

    /**
     * Calculate feature values for a specific SAS point using probe energy cloud statistics
     */
    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        ProbePoints probePoints = (ProbePoints) context.protein.secondaryData.get(getSecondaryDataKey())

        if (!probePoints || probePoints.points.size() == 0) {
            log.warn("No probe points found for ${getProbeType()}, returning zero features")
            return [0, 0, 0, 0, 0, 0, 0, 0, 0] as double[]
        }

        // Get probe points in two-layer cloud around the SAS point
        Atoms.SphereLayers layers = probePoints.points.cutoutLayers(
            sasPoint,
            params.energy_cloud_radius,
            params.energy_cloud_radius2
        )

        Atoms cloudPoints = layers.innerSphere
        Atoms cloudPoints2 = params.xenergy_cloud2_layered ? layers.outerLayer : layers.outerSphere

        if (cloudPoints.size() == 0) {
            log.warn("No ${getProbeType()} probe points found in cloud for SAS point, returning zero features")
            return [0, 0, 0, 0, 0, 0, 0, 0, 0] as double[]
        }

        // Extract energy statistics from the probe point clouds
        double nearestPointEnergy = ((LabeledPoint) cloudPoints.findNearest(sasPoint)).score

        StatSample2 stats = new StatSample2(cloudPoints.collect { ((LabeledPoint) it).score } as List<Double>)
        StatSample2 stats2 = new StatSample2(cloudPoints2.collect { ((LabeledPoint) it).score } as List<Double>)

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