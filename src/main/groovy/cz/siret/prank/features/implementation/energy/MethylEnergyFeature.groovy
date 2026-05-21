package cz.siret.prank.features.implementation.energy


import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * SAS point feature: vdW-only methyl probe energy (no hydrogens).
 * Provides a single scalar per SAS point; units: kcal/mol. More negative = more favorable.
 *
 * The feature is registered as a singleton, so the LJEnergyCalculator field is
 * shared across worker threads. Guava's {@code Suppliers.memoize} gives us a
 * thread-safe lazy init that snapshots Params on first call. As with the
 * previous lazy-init, the snapshot wins for the lifetime of the JVM —
 * subsequent Params changes are NOT picked up.
 */
@Slf4j
@CompileStatic
class MethylEnergyFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "energy-ch3"

    private final Supplier<LJEnergyCalculator> calculator = Suppliers.memoize({
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
    } as Supplier<LJEnergyCalculator>)

    @Override
    String getName() {
        return NAME
    }

    /**
     * Feature computes per-point energy only; no changes to training, ranking, or clustering.
     */
    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Atoms neighbourAtoms = context.neighbourhoodAtoms
        if (neighbourAtoms == null || neighbourAtoms.size() == 0) {
            return [0.0] as double[]
        }
        double energy = calculator.get().computeEnergyForPoint(sasPoint, neighbourAtoms)
        return [energy] as double[]
    }

}
