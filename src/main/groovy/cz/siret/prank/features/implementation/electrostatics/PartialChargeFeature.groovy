package cz.siret.prank.features.implementation.electrostatics

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.ProcessedItemContext
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Atom-level feature: AMBER ff14SB partial charge of the atom, in elementary
 * charge units ({@code e}). Falls through to element-bucket defaults for
 * atoms outside the AMBER table.
 *
 * <p>Header: {@code partial_charge}.
 *
 * <p>Register {@link cz.siret.prank.features.api.wrappers.AtomicToSasFeatWrapper}
 * around this calculator to get a SAS-projected version "for free".
 */
@CompileStatic
class PartialChargeFeature extends AtomFeatureCalculator {

    static final String NAME = "partial_charge"
    private static final List<String> HEADER = ["partial_charge"].asImmutable()

    @Override
    String getName() { NAME }

    @Override
    List<String> getHeader() { HEADER }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        PartialChargeTable.forProtein(protein)
    }

    @Override
    double[] calculateForAtom(Atom atom, AtomFeatureCalculationContext context) {
        return [PartialChargeTable.forProtein(context.protein).get(atom)] as double[]
    }

}
