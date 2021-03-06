package cz.siret.prank.features.implementation

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

import static cz.siret.prank.features.implementation.AsaFeature.ProtAsa
import static cz.siret.prank.features.implementation.AsaFeature.calcProtAsa

/**
 * Local protein solvent accessible surface area feature
 */
@Slf4j
@CompileStatic
class Asa2Feature extends SasFeatureCalculator implements Parametrized, Writable {

    static final String NAME = "asa2"

    @Override
    String getName() { NAME }

    @Override
    List<String> getHeader() {
        return ["asa2.1", "asa2.2"]
    }


    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        if (!protein.secondaryData.containsKey("prot_atom_asa")) {
            protein.secondaryData.put "prot_atom_asa", calcProtAsa(protein, params.feat_asa_probe_radius)
        }
        if (!protein.secondaryData.containsKey("prot_atom_asa2")) {
            protein.secondaryData.put "prot_atom_asa2", calcProtAsa(protein, params.feat_asa_probe_radius2)
        }
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Atoms localAtoms = context.protein.exposedAtoms.cutoutSphere(sasPoint, params.feat_asa_neigh_radius)

        ProtAsa protAsa = (ProtAsa) context.protein.secondaryData.get("prot_atom_asa")
        double localAsa = (double) localAtoms.collect { Atom a -> protAsa.asaByAtom.get(a.PDBserial) ?: 0 }.sum(0)

        ProtAsa protAsa2 = (ProtAsa) context.protein.secondaryData.get("prot_atom_asa2")
        double localAsa2 = (double) localAtoms.collect { Atom a -> protAsa2.asaByAtom.get(a.PDBserial) ?: 0 }.sum(0)

        return [localAsa, localAsa2] as double[]
    }

}
