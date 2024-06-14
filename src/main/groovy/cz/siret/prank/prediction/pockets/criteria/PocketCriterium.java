package cz.siret.prank.prediction.pockets.criteria;

import cz.siret.prank.domain.Ligand;
import cz.siret.prank.domain.Pocket;
import cz.siret.prank.program.routines.results.EvalContext;

/**
 * Successful pocket identification criterium
 */
public abstract class PocketCriterium {

    private final String name;

    public PocketCriterium(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract boolean isIdentified(Ligand ligand, Pocket pocket, EvalContext context);

    /**
     * higher score = better identified (eg. closer to ligand/ better overlap etc.)
     */
    public abstract double score(Ligand ligand, Pocket pocket);

}
