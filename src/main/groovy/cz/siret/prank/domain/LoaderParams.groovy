package cz.siret.prank.domain

import cz.siret.prank.program.params.Params
import groovy.transform.TupleConstructor

/**
 *
 */
@TupleConstructor
class LoaderParams {

    public static ignoreLigandsSwitch = false

    boolean ignoreLigands = ignoreLigandsSwitch
    boolean ligandsSeparatedByTER = false

    boolean relevantLigandsDefined
    Set<String> relevantLigandNames = new HashSet<>()

    int minLigandAtoms = Params.inst.min_ligand_atoms

    Set<String> getIgnoredHetGroups() {
        return Params.inst.ignore_het_groups
    }
}