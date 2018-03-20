package cz.siret.prank.features.implementation

import cz.siret.prank.domain.AA
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PdbUtils
import org.biojava.nbio.structure.Atom

/**
 * One hot encoding for residue of the atom
 */
class AtomicResidueFeature extends AtomFeatureCalculator implements Parametrized {

    static List<AA> AATYPES = AA.values().toList()
    static List<String> HEADER = AATYPES.collect{ it.name() }.toList()

    @Override
    String getName() {
        "ares"
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    double[] calculateForAtom(Atom atom, AtomFeatureCalculationContext ctx) {

        AA aa = AA.forCode(PdbUtils.getCorrectedAtomResidueCode(atom))

        double[] vect = new double[AATYPES.size()]
        if (aa != null) {
            vect[aa.ordinal()] = 1d
        }

        return vect
    }

}
