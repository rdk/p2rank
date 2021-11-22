package cz.siret.prank.features.implementation.contactres

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

        Atom center = res.atoms.centroid
        Atom ca = center
        Atom cb = center
        if (aa!=null) {
            ca = aa.getCA()
            cb = aa.getCB()    
        }

        double dcenter = Struct.dist(sasPoint, center)
        double dclosest = res.atoms.dist(sasPoint)

        double dca = 0
        double dcb = 0
        double CAmCB = 0
        double CAdClosest = 1
        double CAdCenter = 1

        if (ca != null) {
            dca = Struct.dist(sasPoint, ca)
            CAdClosest = dca / dclosest
            CAdCenter = dca / dcenter
            if (cb != null) {
                CAmCB = dca - dcb
            }
        } else {
            log.debug "WARN: CA atom not found in residue [{}]!", res
        }
        if (cb != null) {
            dcb = Struct.dist(sasPoint, cb)
        } else {
            log.debug "WARN: CB atom not found in residue [{}]!", res
        }

        return [dca, dcb, dcenter, dclosest, CAmCB, CAdClosest, CAdCenter] as double[]
    }
    
}
