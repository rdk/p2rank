package cz.siret.prank.features.implementation.energy


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
 */
@Slf4j
@CompileStatic
class MethylEnergyFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "energy-ch3"

    // Immutable calculator instance
    private LJEnergyCalculator calculator

    /**
     * Initialize the energy calculator with current parameters (lazy, called on first use per protein).
     */
    private void ensureCalculatorInitialized() {
        if (calculator != null) return
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

    /**
     * Feature computes per-point energy only; no changes to training, ranking, or clustering.
     */
    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
            ensureCalculatorInitialized()

            // Get neighbor atoms around the SAS point
            //Atoms neighbourAtoms = context.extractor.deepLayer.cutoutSphere(sasPoint, params.energy_rc)
            Atoms neighbourAtoms = context.neighbourhoodAtoms

            if (neighbourAtoms == null || neighbourAtoms.size() == 0) {
                return [0.0] as double[]
            }

            // Calculate energy using the calculator (sasPoint is already an Atom)
            double energy = calculator.computeEnergyForPoint(sasPoint, neighbourAtoms)

            return [energy] as double[]

//        } catch (Exception e) {
//            log.error("Error calculating methyl energy for SAS point: ${e.message}", e)
//            return [0.0] as double[]
//        }
    }

}
