package cz.siret.prank.features.implementation.energy3

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.features.implementation.electrostatics.PartialChargeTable
import cz.siret.prank.features.implementation.energy2.calc.EnergyCalculator
import cz.siret.prank.features.implementation.energy2.calc.EnergyCalculatorConfig
import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Direct-at-point probe energy feature. Computes probe interaction energy
 * directly at each query SAS point — no separate probe surface, no KD-tree,
 * no cloud statistics. Returns a single scalar per probe.
 *
 * <p>All 5 probe energies are computed in a single
 * {@code computeEnergyForPoint} call and cached per SAS point so that
 * the 5 concrete features share the neighbour-data precomputation.
 */
@Slf4j
@CompileStatic
abstract class AbstractDirectProbeEnergyFeature extends SasFeatureCalculator implements Parametrized {

    private static final String CALC_CACHE_KEY = "energy3_calculator"
    private static final String POINT_CACHE_KEY = "energy3_point_cache"

    abstract ProbeType getProbeType()

    @Override
    List<String> getHeader() {
        return [getName()]
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        if (!protein.secondaryData.containsKey(CALC_CACHE_KEY)) {
            EnergyCalculatorConfig cfg = new EnergyCalculatorConfig.Builder()
                .rCutoff(params.energy_rc)
                .rOn(params.energy_ron)
                .rMin(params.energy_min_r)
                .dielectricConstant(params.energy2_dielectric)
                .enableCoulomb(params.energy2_enable_coulomb)
                .aromaticOnly(params.energy2_aromatic_only)
                .selectedProbes(EnumSet.allOf(ProbeType))
                .build()

            EnergyCalculator calc
            if (cfg.enableCoulomb) {
                PartialChargeTable charges = PartialChargeTable.forProtein(protein)
                calc = new EnergyCalculator(cfg, charges.&get)
            } else {
                calc = new EnergyCalculator(cfg)
            }
            protein.secondaryData.put(CALC_CACHE_KEY, calc)
            protein.secondaryData.put(POINT_CACHE_KEY, new PointEnergyCache())
        }
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        EnergyCalculator calc = (EnergyCalculator) context.protein.secondaryData.get(CALC_CACHE_KEY)
        if (calc == null) {
            return [0d] as double[]
        }

        PointEnergyCache cache = (PointEnergyCache) context.protein.secondaryData.get(POINT_CACHE_KEY)
        List<Double> energies = cache.getOrCompute(sasPoint, calc, context.neighbourhoodAtoms)

        return [energies.get(getProbeType().ordinal())] as double[]
    }

    /**
     * Single-slot cache: stores the last SAS point's energies so the 5
     * concrete features sharing this cache avoid redundant computation.
     * Not thread-safe — callers process one point at a time per protein.
     */
    @CompileStatic
    static class PointEnergyCache {
        private Atom lastPoint
        private List<Double> lastEnergies

        List<Double> getOrCompute(Atom point, EnergyCalculator calc, Atoms neighbours) {
            if (lastPoint != null && lastPoint.is(point)) {
                return lastEnergies
            }
            lastPoint = point
            lastEnergies = calc.computeEnergyForPoint(point, neighbours)
            return lastEnergies
        }
    }
}
