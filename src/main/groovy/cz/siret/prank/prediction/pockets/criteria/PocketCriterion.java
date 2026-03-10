package cz.siret.prank.prediction.pockets.criteria;

import cz.siret.prank.domain.BindingSite;
import cz.siret.prank.domain.Pocket;
import cz.siret.prank.program.routines.results.EvalContext;

/**
 * Successful pocket identification criterion.
 * Defines whether a predicted pocket is considered to be successfully identified or not,
 * and also defines a score for the pocket prediction (eg. distance to ligand, overlap etc.).
 */
public abstract class PocketCriterion {

    private final String name;

    public PocketCriterion(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     *
     * @param site ground truth binding site defined by ligand or by set of residues in the dataset (explicit sites)
     * @param pocket predicted pocket
     * @param context currently used for passing cached data
     * @return true if the pocket is considered to be successfully identified, false otherwise
     */
    public abstract boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context);

    /**
     * higher score = better identified (eg. closer to ligand/ better overlap etc.)
     */
    public abstract double score(BindingSite site, Pocket pocket);

}
