package cz.siret.prank.features.api.wrappers

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.*
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Mapping Closest Atom to SAS point
 */
@CompileStatic
class AtomicToSasFeatWrapper extends SasFeatureCalculator {

    final AtomFeatureCalculator delegate
    final String name

    AtomicToSasFeatWrapper(AtomFeatureCalculator delegate) {
        this.delegate = delegate
        this.name = delegate.name + '_sas'
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
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Atom closestAtom = context.neighbourhoodAtoms.findNearest(sasPoint)

        if (closestAtom != null) {
            return delegate.calculateForAtom(closestAtom, new AtomFeatureCalculationContext(context.protein, closestAtom))
        } else {
            return new double[header.size()]
        }
    }

}
