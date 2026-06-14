package cz.siret.prank.features.implementation.contactres

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import cz.siret.prank.domain.AA
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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

    ContactResiduesPositionFeature() {
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
        double contactDist = params.feat_crang_contact_dist

        Atoms contactAtoms = context.protein.exposedAtoms.cutoutSphere(sasPoint, contactDist)
        List<Residue> contactResidues = context.protein.residues.getDistinctForAtoms(contactAtoms)

        int n = contactResidues.size()
        if (n == 0) {
            log.debug "no contact residues found for SAS point using contact dist {}!", contactDist
        } else {
            log.trace 'contact residues: {}', contactResidues.size()
        }

        // TODO: this can be optimized

        Multimap<AA, Residue> contactResIndex = ArrayListMultimap.create(20, 3);
        for (Residue res : contactResidues) {
            AA aa = res.getAa()
            if (aa!=null) {
                contactResIndex.put(aa, res)
            }
        }
        Map<AA, Collection<Residue>> cresmap = (Map<AA, Collection<Residue>>) contactResIndex.asMap()

        double[] vect = new double[HEADER.size()]

        int i = 0
        for (AA aa : AATYPES) {
            double count = 0
            double distclosest = MAX_DIST
            double distca = MAX_DIST
            double distcenter = MAX_DIST

            Collection<Residue> residues = (Collection<Residue>) cresmap.get(aa)
            if (residues!=null && !residues.empty) {

                Residue closestResOfType = residues.min { it.atoms.dist(sasPoint)  }
                Atoms ratoms = closestResOfType.atoms
                // residues are admitted on res.getAa() (corrected 3-letter code), which can resolve for
                // modified/HETATM groups that are not BioJava AminoAcids; guard the CA lookup (distca falls back to distcenter below)
                Atom Ca = closestResOfType.aminoAcid?.getCA()

                count = residues.size()
                distclosest = ratoms.dist(sasPoint)
                distcenter = Struct.dist ratoms.centroid, sasPoint
                distca = (Ca==null) ? distcenter : Struct.dist(Ca, sasPoint)
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
