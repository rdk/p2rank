package cz.siret.prank.domain;

import cz.siret.prank.geom.Atoms;
import org.biojava.nbio.structure.Atom;

public interface BindingSite {
    Atoms getAtoms();
    Atom getCentroid();
    Atoms getSasPoints();
    String getLabel();
}
