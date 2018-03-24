package cz.siret.prank.features.implementation

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

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        double probeRadius = params.feat_asa_probe_radius
        int nSpherePoints = AsaCalculator.DEFAULT_N_SPHERE_POINTS
        int threads = 1
        boolean hetAtoms = false

        AsaCalculator asaCalculator = new AsaCalculator(protein.structure, probeRadius, nSpherePoints, threads, hetAtoms)

        List<GroupAsa> asas = asaCalculator.groupAsas.toList()

        protein.secondaryData.put "prot_asa", new ProtAsa(protein, asas)
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        List<Group> groups = context.protein.exposedAtoms.cutoutSphere(sasPoint, params.feat_asa_neigh_radius).distinctGroupsSorted
        ProtAsa protAsa = (ProtAsa) context.protein.secondaryData.get("prot_asa")
        double localAsa = (double) groups.collect { Group g -> protAsa.groupAsaMap.get(g.residueNumber) ?: 0 }.sum(0)

        return [localAsa] as double[]
    }


    static class ProtAsa {
        Protein protein
        List<GroupAsa> groupAsas

        Map<ResidueNumber, Double> groupAsaMap = new HashMap<>()

        ProtAsa(Protein protein, List<GroupAsa> groupAsas) {
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
