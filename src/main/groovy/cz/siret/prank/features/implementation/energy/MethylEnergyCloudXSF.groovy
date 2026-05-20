package cz.siret.prank.features.implementation.energy

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Surface
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.StatSample2
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.utils.MathUtils.nanToZero

/**
 * SAS point feature: vdW-only methyl probe energy (no hydrogens).
 * Provides a single scalar per SAS point; units: kcal/mol. More negative = more favorable.
 */
@Slf4j
@CompileStatic
class MethylEnergyCloudXSF extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "energy-cloudx-ch3"
    static final String SEC_DATA_KEY = "PP_CH3"

    // Lazy-initialized calculator instance (see initializeCalculator)
    private LJEnergyCalculator calculator

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        initializeCalculator()

        if (protein.secondaryData.containsKey(SEC_DATA_KEY)) {
            return  // already computed
        }

        List<LabeledPoint> points = calcProbePoints(protein)
        for (LabeledPoint p : points) {
            Atoms neighbourAtoms = protein.proteinAtoms.cutoutSphere(p, params.energy_rc)
            double energy = calculator.computeEnergyForPoint(p, neighbourAtoms)
            p.score = energy
        }

        ProbePoints probePoints = new ProbePoints(new Atoms(points).withKdTree())

        protein.secondaryData.put(SEC_DATA_KEY, probePoints)
    }

//===========================================================================================================//

    private List<LabeledPoint> calcProbePoints(Protein protein) {
        Surface surf = Surface.computeAccessibleSurface(protein.proteinAtoms, params.xenergy_solvent_radius, params.xenergy_tessellation)

        List<LabeledPoint> res = new ArrayList<>(surf.points.size())
        for (Atom point : surf.points) {
            res.add(new LabeledPoint(point, false)) // initially unlabeled
        }

        return res
    }


    /**
     * Initialize the energy calculator with current parameters
     */
    private void initializeCalculator() {
        calculator = new LJEnergyCalculator(
            params.energy_probe_sigma,
            params.energy_probe_epsilon,
            params.energy_rc,
            params.energy_ron,
            params.energy_min_r,
            params.energy_missing_elem_policy,
            params.energy_fallback_sigma,
            params.energy_fallback_epsilon
        )
    }

    @Override
    String getName() {
        return NAME
    }

    @Override
    List<String> getHeader() {
        return ["nearest", "mean", "min",  "vpa", "relstd"]
    }

    /**
     * Feature computes per-point energy only; no changes to training, ranking, or clustering.
     */
    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        ProbePoints probePoints = (ProbePoints) context.protein.secondaryData.get(SEC_DATA_KEY)

        Atoms cloudPoints = probePoints.points.cutoutSphere(sasPoint, params.energy_cloud_radius)

        if (cloudPoints.size() == 0) {
            log.warn("No probe points found in cloud for SAS point, returning 0.0 energy")
            return [0, 0, 0, 0, 0] as double[]
        }

        Atom nearestPoint = cloudPoints.findNearest(sasPoint)
        double nearestPointEnergy = ((LabeledPoint) nearestPoint).score

        StatSample2 stats = new StatSample2(cloudPoints.collect { ((LabeledPoint) it).score } as List<Double>)

        return [
            nearestPointEnergy,
            stats.mean,
            stats.min,
            stats.vpa,
            nanToZero(stats.relativeStddev),
        ] as double[]

    }

}
