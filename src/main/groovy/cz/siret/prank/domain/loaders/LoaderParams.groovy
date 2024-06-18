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

    /**
     * TODO get rid of this global variable
     */
    public static ignoreLigandsSwitch = false



    boolean ignoreLigands = ignoreLigandsSwitch
    boolean ligandsSeparatedByTER = false

    boolean relevantLigandsDefined
    List<Dataset.LigandDefinition> relevantLigandDefinitions = new ArrayList<>()

    private Set<String> ignoredHetGroups = Params.inst.ignore_het_groups as Set

    Set<String> getIgnoredHetGroups() {
        return ignoredHetGroups
    }

//    LoaderParams(LoaderParams lp) {
//        this.ignoreLigands = lp.ignoreLigands
//        this.ligandsSeparatedByTER = lp.ligandsSeparatedByTER
//        this.relevantLigandsDefined = lp.relevantLigandsDefined
//        this.relevantLigandDefinitions = lp.relevantLigandDefinitions
//        this.ignoredHetGroups = lp.ignoredHetGroups
//    }
    
}