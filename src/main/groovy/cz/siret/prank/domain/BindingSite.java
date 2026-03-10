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

    Atoms getSasPoints();

    String getLabel();

}
