package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.CofactorHandler
import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.biojava.nbio.structure.Group

import javax.annotation.Nullable

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
    boolean loadLigandsFromSeparateFiles = Params.inst.load_ligands_from_separate_files

    boolean relevantLigandsDefined
    List<Dataset.LigandDefinition> relevantLigandDefinitions = new ArrayList<>()

    private Set<String> ignoredHetGroups = Params.inst.ignore_het_groups as Set

    Set<String> getIgnoredHetGroups() {
        return ignoredHetGroups
    }

    /**
     * Handler for cofactor processing and lookup. Populated from {@code Params.cofactors} or
     * per-structure dataset column. Null when no cofactors are configured (default behaviour).
     *
     * Single source of truth for cofactor state - eliminates state duplication between
     * LoaderParams and CofactorHandler.
     */
    @Nullable
    CofactorHandler cofactorHandler = null

    /**
     * Check if a group is a configured cofactor. Delegates to {@link CofactorHandler#isCofactor},
     * which is an O(1) identity lookup against the set of groups matched during
     * {@code CofactorHandler.extractCofactorAtoms}.
     *
     * Thread-safe: each LoaderParams instance has its own handler.
     */
    boolean isCofactor(Group group) {
        return cofactorHandler != null && cofactorHandler.isCofactor(group)
    }

}