package cz.siret.prank.features.api.wrappers

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.*
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Calculate value for Atom as it was SAS point.
 *
 * Warning: make sure that wrapped feature doesn't use neighbourhoodAtms 
 */
@CompileStatic
class SasToAtomicFeatWrapper extends AtomFeatureCalculator {

    final SasFeatureCalculator delegate
    final String name

    SasToAtomicFeatWrapper(SasFeatureCalculator delegate) {
        this.delegate = delegate
        this.name = delegate.name + '_atomic'
    }

    @Override
    String getName() {
        return name
    }

    @Override
    List<String> getHeader() {
        return delegate.getHeader()
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        delegate.preProcessProtein(protein, context)
    }

    @Override
    void postProcessProtein(Protein protein) {
        delegate.postProcessProtein(protein)
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        SasFeatureCalculationContext ctx = new SasFeatureCalculationContext(context.protein,
                null, null) // !! make sure wrapped feature doesn't use these

        return delegate.calculateForSasPoint(proteinSurfaceAtom, ctx)
    }

}
