package cz.siret.prank.features.implementation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.asa.AsaCalculator
import org.biojava.nbio.structure.asa.GroupAsa

/**
 * Local protein solvent accessible surface area feature
 */
@Slf4j
@CompileStatic
class AsaFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "asa"

    @Override
    String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein) {
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
        Atoms localAtoms = context.protein.exposedAtoms.cutoffAroundAtom(sasPoint, params.feat_asa_neigh_radius)
        ProtAsa protAsa = (ProtAsa) context.protein.secondaryData.get("prot_asa")
        double localAsa = (double) localAtoms.collect { Atom a -> protAsa.atomAsas.get(a.PDBserial) ?: 0 }.sum(0)

        return [localAsa] as double[]
    }


    static class ProtAsa {
        Protein protein
        List<GroupAsa> groupAsas

        Map<Integer, Double> atomAsas = new HashMap<>()

        ProtAsa(Protein protein, List<GroupAsa> groupAsas) {
            this.protein = protein
            this.groupAsas = groupAsas

            for (GroupAsa gasa : groupAsas) {

                int n_asas = gasa.atomAsaUs.size()
                int n_atoms = gasa.group.atoms.size()

                if (n_asas!=n_atoms) {
                    log.warn "Number of atoms ({}) and calculated ASAs ({}) for a group ($gasa.group.PDBName) don't match! ", n_atoms, n_asas
                }

                int n = Math.min(n_asas, n_atoms)

                for (int i=0; i<n; ++i) {
                    Double asa = gasa.atomAsaUs[i] ?: 0d
                    Atom atom = gasa.group.atoms[i]

                    if (atom!=null) {
                        atomAsas.put atom.PDBserial, asa
                    }
                }
            }
        }

    }

}
