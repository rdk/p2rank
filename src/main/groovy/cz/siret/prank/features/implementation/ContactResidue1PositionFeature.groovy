package cz.siret.prank.features.implementation

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.AminoAcid
import org.biojava.nbio.structure.Atom

/**
 * Contact residue position feature (for single nearest residue)
 */
@Slf4j
@CompileStatic
class ContactResidue1PositionFeature extends SasFeatureCalculator implements Parametrized {

    static String NAME = 'cr1pos'

    final List<String> HEADER = ['CA', 'CB', 'Closest', 'Center', 'CAmCB', 'CAdClosest', 'CAdCenter']

//===========================================================================================================//

    ContactResidue1PositionFeature() {
    }

    @Override
    String getName() {
        NAME
    }

    @Override
    List<String> getHeader() {
        HEADER
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        Residue res = context.protein.residues.findNearest(sasPoint)
        AminoAcid aa = res.aminoAcid

        Atom centerPoint = res.atoms.centerOfMass

        double ca = Struct.dist(sasPoint, aa.getCA())
        double cb = Struct.dist(sasPoint, aa.getCB())
        double center = Struct.dist(sasPoint, centerPoint)
        double closest = res.atoms.dist(sasPoint)

        double CAmCB = ca - cb
        double CAdClosest = ca / closest
        double CAdCenter = ca / center

        return [ca, cb, center, closest, CAmCB, CAdClosest, CAdCenter] as double[]
    }
}
