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
 * <p>Eliminates the main bottleneck of energy2 features: building 5 separate
 * SAS surfaces with full energy evaluation at every surface point.
 * Instead, one EnergyCalculator is cached per protein and called once
 * per query SAS point with the protein's neighbour atoms.
 */
@Slf4j
@CompileStatic
abstract class AbstractDirectProbeEnergyFeature extends SasFeatureCalculator implements Parametrized {

    private static final String CALC_CACHE_KEY = "energy3_calculator"

    abstract ProbeType getProbeType()

    @Override
    List<String> getHeader() {
        return [getName()]
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        protein.secondaryData.computeIfAbsent(CALC_CACHE_KEY, { k ->
            EnergyCalculatorConfig cfg = new EnergyCalculatorConfig.Builder()
                .rCutoff(params.energy_rc)
                .rOn(params.energy_ron)
                .rMin(params.energy_min_r)
                .dielectricConstant(params.energy2_dielectric)
                .enableCoulomb(params.energy2_enable_coulomb)
                .aromaticOnly(params.energy2_aromatic_only)
                .selectedProbes(EnumSet.allOf(ProbeType))
                .build()

            if (cfg.enableCoulomb) {
                PartialChargeTable charges = PartialChargeTable.forProtein(protein)
                return new EnergyCalculator(cfg, charges.&get)
            } else {
                return new EnergyCalculator(cfg)
            }
        })
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        EnergyCalculator calc = (EnergyCalculator) context.protein.secondaryData.get(CALC_CACHE_KEY)
        if (calc == null) {
            return [0d] as double[]
        }

        Atoms neighbours = context.neighbourhoodAtoms
        if (neighbours == null || neighbours.isEmpty()) {
            return [0d] as double[]
        }

        List<Double> energies = calc.computeEnergyForPoint(sasPoint, neighbours)

        int idx = probeIndex()
        return [energies.get(idx)] as double[]
    }

    private int probeIndex() {
        ProbeType target = getProbeType()
        int i = 0
        for (ProbeType pt : ProbeType.values()) {
            if (pt == target) return i
            i++
        }
        return 0
    }
}
