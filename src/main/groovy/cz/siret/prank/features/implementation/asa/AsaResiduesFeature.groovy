package cz.siret.prank.features.implementation.asa

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.ResidueNumber
import org.biojava.nbio.structure.asa.AsaCalculator
import org.biojava.nbio.structure.asa.GroupAsa

/**
 * Local protein solvent accessible surface area feature
 */
@Slf4j
@CompileStatic
class AsaResiduesFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "asares"

    @Override
    String getName() { NAME }

    
    ProtGroupAsa calculateProtGroupAsa(Protein protein) {
        double probeRadius = params.feat_asa_probe_radius
        int nSpherePoints = AsaCalculator.DEFAULT_N_SPHERE_POINTS
        int threads = 1
        boolean hetAtoms = false

        AsaCalculator asaCalculator = new AsaCalculator(protein.structure, probeRadius, nSpherePoints, threads, hetAtoms)

        List<GroupAsa> asas = asaCalculator.groupAsas.toList()

        return new ProtGroupAsa(protein, asas)
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.secondaryData.computeIfAbsent "prot_group_asa", { k -> calculateProtGroupAsa(protein) }
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        List<Group> groups = context.protein.exposedAtoms.cutoutSphere(sasPoint, params.feat_asa_neigh_radius).distinctGroupsSorted
        ProtGroupAsa protAsa = (ProtGroupAsa) context.protein.secondaryData.get("prot_group_asa")
        double localAsa = (double) groups.collect { Group g -> protAsa.groupAsaMap.get(g.residueNumber) ?: 0 }.sum(0)

        return [localAsa] as double[]
    }


    static class ProtGroupAsa {
        
        Protein protein
        List<GroupAsa> groupAsas

        Map<ResidueNumber, Double> groupAsaMap = new HashMap<>()

        ProtGroupAsa(Protein protein, List<GroupAsa> groupAsas) {
            this.protein = protein
            this.groupAsas = groupAsas

            for (GroupAsa gasa : groupAsas) {

                double asa = gasa.asaC
                ResidueNumber resNum = gasa?.group?.residueNumber

                if (resNum!=null) {
                    groupAsaMap.put resNum, asa
                }
            }
        }

    }

}
