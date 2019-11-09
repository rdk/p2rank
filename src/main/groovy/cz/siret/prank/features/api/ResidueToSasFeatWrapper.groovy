package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Mapping Closest Residue to SAS point
 */
@CompileStatic
class ResidueToSasFeatWrapper extends SasFeatureCalculator {

    final ResidueFeatureCalculator delegate
    final String name

    ResidueToSasFeatWrapper(ResidueFeatureCalculator delegate) {
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
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Residue res = context.protein.residues.findNearest(sasPoint)

        if (res != null) {
            return delegate.calculateForResidue(res, new ResidueFeatureCalculationContext(context.protein))
        } else {
            return new double[header.size()]
        }
    }

}
