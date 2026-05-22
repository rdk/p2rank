package cz.siret.prank.features.implementation.electrostatics

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * SAS-point feature: three Coulomb-flavour scalars summarising the
 * electrostatic environment at the point, within
 * {@code electrostatics_radius}:
 *
 * <ul>
 *   <li>{@code potential} = Σ qᵢ / rᵢ  (signed, units e/Å) — net potential</li>
 *   <li>{@code abs_potential} = Σ |qᵢ| / rᵢ (units e/Å) — magnitude regardless of sign</li>
 *   <li>{@code field_magnitude} = ‖Σ qᵢ · r⃗ᵢ / rᵢ³‖ (units e/Å²) — electric field vector magnitude</li>
 * </ul>
 *
 * <p>All three are computed in one neighbor walk via {@link CoulombKernel}. The
 * 1/r is clamped to {@code electrostatics_min_r} to avoid singularities when
 * the SAS point sits inside an atom's vdW radius.
 */
@CompileStatic
class ElectrostaticsSasFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "electrostatics"
    private static final List<String> HEADER = ["potential", "abs_potential", "field_magnitude"].asImmutable()

    @Override
    String getName() { NAME }

    @Override
    List<String> getHeader() { HEADER }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        PartialChargeTable.forProtein(protein)
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        PartialChargeTable table = PartialChargeTable.forProtein(context.protein)
        Atoms nearby = context.protein.proteinAtoms.cutoutSphere(sasPoint, params.electrostatics_radius)
        CoulombKernel.Result r = CoulombKernel.accumulate(sasPoint, nearby, table, params.electrostatics_min_r)
        return [r.potential(), r.absPotential(), r.fieldMagnitude()] as double[]
    }

}
