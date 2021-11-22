package cz.siret.prank.features.implementation.contactres

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import cz.siret.prank.domain.AA
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.AminoAcid
import org.biojava.nbio.structure.Atom

/**
 *
 */
@Slf4j
@CompileStatic
class ContactResiduesPositionFeature extends SasFeatureCalculator implements Parametrized {

    static String NAME = 'crpos'

    static List<AA> AATYPES = AA.values().sort { it.name() }.toList()

    final List<String> HEADER = new ArrayList<>()

    double MAX_DIST = 20;

//===========================================================================================================//

    double contactDist

    ContactResiduesPositionFeature() {
        contactDist = params.feat_crang_contact_dist

        for (AA aa : AATYPES) {
            String prefix = NAME + '.' + aa.name().toLowerCase() + '.'
            HEADER.add prefix + 'count'
            HEADER.add prefix + 'distca'
            HEADER.add prefix + 'distclosest'
            HEADER.add prefix + 'distcenter'
        }
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

        Atoms contactAtoms = context.neighbourhoodAtoms.cutoutSphere(sasPoint, contactDist)
        List<AminoAcid> contactResidues = (List<AminoAcid>)(List)contactAtoms.getDistinctGroupsSorted().findAll{ it instanceof AminoAcid }.toList()

        log.debug 'contact residues: ' + contactResidues.size()

        // TODO: this can be optimized

        Multimap<AA, AminoAcid> contactResIndex = ArrayListMultimap.create(20, 3);
        for (AminoAcid res : contactResidues) {
            AA aa = AA.forName(PdbUtils.getCorrectedResidueCode(res))
            if (aa!=null) {
                contactResIndex.put(aa, res)
            }
        }
        Map<AA, Collection<AminoAcid>> cresmap = (Map<AA, Collection<AminoAcid>>) contactResIndex.asMap()

        double[] vect = new double[HEADER.size()]

        int i = 0
        for (AA aa : AATYPES) {
            double count = 0
            double distclosest = MAX_DIST
            double distca = MAX_DIST
            double distcenter = MAX_DIST

            Collection<AminoAcid> residues = (Collection<AminoAcid>) cresmap.get(aa)
            if (residues!=null && !residues.empty) {

                AminoAcid closestResOfType = residues.min { Atoms.allFromGroup(it).dist(sasPoint)  }
                Atoms ratoms = Atoms.allFromGroup(closestResOfType)

                count = residues.size()
                distclosest = ratoms.dist(sasPoint)
                distcenter = Struct.dist ratoms.centroid, sasPoint
                distca = (closestResOfType.CA==null) ? distcenter : Struct.dist(closestResOfType.CA, sasPoint)
            }

            vect[i] = count
            vect[i+1] = distca
            vect[i+2] = distclosest
            vect[i+3] = distcenter

            i += 4
        }

        return vect
    }
    
}
