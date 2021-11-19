package cz.siret.prank.features.implementation.asa

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.StructureTools
import org.biojava.nbio.structure.asa.AsaCalculator

/**
 * Local protein solvent accessible surface area feature
 */
@Slf4j
@CompileStatic
class AsaFeature extends SasFeatureCalculator implements Parametrized, Writable {

    static final String NAME = "asa"

    @Override
    String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.secondaryData.computeIfAbsent("prot_atom_asa", { k -> calcProtAsa(protein, params.feat_asa_probe_radius) })
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Atoms localAtoms = context.protein.exposedAtoms.cutoutSphere(sasPoint, params.feat_asa_neigh_radius)
        ProtAsa protAsa = (ProtAsa) context.protein.secondaryData.get("prot_atom_asa")
        double localAsa = (double) localAtoms.collect { Atom a -> protAsa.asaByAtom.get(a.PDBserial) ?: 0 }.sum(0)

        return [localAsa] as double[]
    }

//===========================================================================================================//

    static ProtAsa calcProtAsa(Protein protein, double probeRadius) {
        int nSpherePoints = AsaCalculator.DEFAULT_N_SPHERE_POINTS
        int threads = 1
        boolean hetAtoms = false

        Atom[] protAtoms = StructureTools.getAllNonHAtomArray(protein.structure, hetAtoms)
        AsaCalculator asaCalculator = new AsaCalculator(protein.structure, probeRadius, nSpherePoints, threads, hetAtoms)
        double[] atomAsas = asaCalculator.calculateAsas()
        protAtoms[0].getPDBserial()

        Map<Integer, Double> asaByAtom = new HashMap<>()
        for (int i=0; i!= protAtoms.length; ++i) {
            asaByAtom.put protAtoms[i].PDBserial, atomAsas[i]
        }

        return new ProtAsa(protein, asaByAtom)
    }

    static class ProtAsa {
        Protein protein
        Map<Integer, Double> asaByAtom

        ProtAsa(Protein protein, Map<Integer, Double> asaByAtom) {
            this.protein = protein
            this.asaByAtom = asaByAtom
        }
    }

}
