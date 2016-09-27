package cz.siret.prank.score.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket

/**
 * Successful pocket identification criterium
 */
public interface IdentificationCriterium {

    boolean isIdentified(Ligand ligand, Pocket pocket);

    /**
     * higher score = better identified (eg. closer to ilgand/ better overlap etc.)
     */
    double score(Ligand ligand, Pocket pocket)

}