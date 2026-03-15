package cz.siret.prank.domain;

import cz.siret.prank.geom.Atoms;
import org.biojava.nbio.structure.Atom;

/**
 * Observed binding site defined by ligand atoms or by set of residues in the dataset (explicit sites).
 */
public interface BindingSite {

    Atoms getAtoms();

    Atom getCentroid();

    Atom getCenterForEval();

    /**
     * Calculate center using the specified method.
     * Unlike getCenterForEval(), this does not read from params - safe for parallel use.
     * @return center atom, or null if the method cannot produce a center for this site
     */
    Atom getCenterForMethod(SiteCenterMethod method);

    Atoms getSasPoints();

    void setSasPoints(Atoms sasPoints);

    String getLabel();

    /** Predicted pocket matched to this binding site during evaluation (null if not matched). */
    Pocket getPredictedPocket();

    void setPredictedPocket(Pocket pocket);

}
