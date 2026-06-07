package cz.siret.prank.features.implementation.energy

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Surface
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Shared scaffolding for the methyl-probe vdW cloud SAS features
 * (energy-cloud-ch3, energy-cloudx-ch3, energy-cloudx2-ch3, energy-cloudx2f-ch3).
 *
 * All variants compute the same per-protein {@link ProbePoints} — methyl-probe
 * vdW energy sampled on a re-tessellated SAS surface — and then differ only in
 * how each SAS point's local cloud is reduced to a feature vector. The shared
 * cache key {@link #SEC_DATA_KEY} is intentional: the variants produce
 * identical ProbePoints by construction (same calculator config, same surface
 * params), so the first variant to {@code preProcessProtein} populates the
 * cache for all of them.
 */
@Slf4j
@CompileStatic
abstract class AbstractMethylEnergyCloudSF extends SasFeatureCalculator implements Parametrized {

    /**
     * Shared across every Methyl-cloud variant — all four currently compute
     * identical ProbePoints from identical inputs. If a variant ever diverges
     * (different probe σ, cutoff, tessellation), it must use its own key.
     */
    protected static final String SEC_DATA_KEY = "PP_CH3"

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        protein.secondaryData.computeIfAbsent(SEC_DATA_KEY, { k -> buildProbePoints(protein) })
    }

    private ProbePoints buildProbePoints(Protein protein) {
        // Build the calculator from current Params per protein. A long-lived
        // singleton or memoized calculator would freeze Params for the lifetime
        // of the JVM, breaking grid sweeps that mutate energy_* mid-run (the
        // previous Suppliers.memoize design hit this regression).
        LJEnergyCalculator calc = newCalculator()
        List<LabeledPoint> points = calcProbePoints(protein)
        for (LabeledPoint p : points) {
            Atoms neighbourAtoms = protein.proteinAtoms.cutoutSphere(p, params.energy_rc)
            p.score = calc.computeEnergyForPoint(p, neighbourAtoms)
        }
        return new ProbePoints(new Atoms(points).withKdTree())
    }

    protected LJEnergyCalculator newCalculator() {
        new LJEnergyCalculator(
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

    protected List<LabeledPoint> calcProbePoints(Protein protein) {
        // routed through the protein's shared surface cache: identical-parameter surfaces (other energy
        // features, or the prediction surface when xenergy params match) are computed once, not per feature
        Surface surf = protein.getSurface(params.xenergy_solvent_radius, params.xenergy_tessellation)
        List<LabeledPoint> res = new ArrayList<>(surf.points.size())
        for (Atom point : surf.points) {
            res.add(new LabeledPoint(point, false))
        }
        return res
    }

    protected ProbePoints getProbePoints(Protein protein) {
        (ProbePoints) protein.secondaryData.get(SEC_DATA_KEY)
    }

    protected static double[] extractScores(Atoms cloudPoints) {
        ProbePoints.extractScores(cloudPoints)
    }
}
