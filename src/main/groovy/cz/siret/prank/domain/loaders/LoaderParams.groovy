package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor

/**
 * Protein file loader parameters
 */
@CompileStatic
@TupleConstructor
class LoaderParams {

    public static ignoreLigandsSwitch = false

    boolean ignoreLigands = ignoreLigandsSwitch
    boolean ligandsSeparatedByTER = false

    boolean relevantLigandsDefined
    List<Dataset.LigandDefinition> relevantLigandDefinitions = new ArrayList<>()

    boolean load_conservation_paths
    boolean load_conservation
    String conservation_origin

    int minLigandAtoms = Params.inst.min_ligand_atoms

    Set<String> getIgnoredHetGroups() {
        return Params.inst.ignore_het_groups
    }
    
}