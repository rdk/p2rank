package cz.siret.prank.features.implementation

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import cz.siret.prank.domain.AA
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PDBUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AminoAcid
import org.biojava.nbio.structure.Atom

/**
 *
 */
@CompileStatic
class ContactResiduesPositionFeature extends SasFeatureCalculator implements Parametrized {

    static String NAME = 'crpos'

    static List<AA> AATYPES = AA.values().sort { it.name() }.toList()

    final List<String> HEADER = new ArrayList<>()

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

        Atoms contactAtoms = context.neighbourhoodAtoms.cutoffAroundAtom(sasPoint, contactDist)
        List<AminoAcid> contactResidues = (List<AminoAcid>)(List)contactAtoms.getDistinctGroups().findAll{ it instanceof AminoAcid }.toList()

        // TODO: this can be optmized

        Multimap<AA, AminoAcid> contactResIndex = ArrayListMultimap.create(20, 3);
        for (AminoAcid res : contactResidues) {
            AA aa = AA.forName(PDBUtils.getResidueCode(res))
            if (aa!=null) {
                contactResIndex.put(aa, res)
            }
        }
        Map<AA, Collection<AminoAcid>> cresmap = contactResIndex.asMap()

        double[] vect = new double[HEADER.size()]

        int i = 0
        for (AA aa : AATYPES) {
            Collection<AminoAcid> residues = cresmap.get(aa)
            if (residues!=null && !residues.isEmpty()) {

                AminoAcid closestResOfType = residues.min { Atoms.allFromGroup(it).dist(sasPoint)  }
                Atoms ratoms = Atoms.allFromGroup(closestResOfType)

                double count = residues.size()
                double distclosest = ratoms.dist(sasPoint)
                double distca = Struct.dist closestResOfType.CA, sasPoint
                double distcenter = Struct.dist ratoms.centerOfMass, sasPoint

                vect[i] = count
                vect[i+1] = distca
                vect[i+2] = distclosest
                vect[i+3] = distcenter
            }

            i += 4
        }

        return vect
    }
}
