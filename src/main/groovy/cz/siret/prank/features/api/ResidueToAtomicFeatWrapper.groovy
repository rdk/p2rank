package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import org.biojava.nbio.structure.Atom

/**
 * Maps a residue features to atom feature
 */
class ResidueToAtomicFeatWrapper extends AtomFeatureCalculator {

    final ResidueFeatureCalculator delegate
    final String name

    ResidueToAtomicFeatWrapper(ResidueFeatureCalculator delegate) {
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
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        Residue res = context.protein.residues.getResidueForAtom(proteinSurfaceAtom)

        if (res != null) {
            return delegate.calculateForResidue(res, new ResidueFeatureCalculationContext(context.protein))
        } else {
            return new double[header.size()]
        }
    }

}
