package cz.siret.prank.geom.transform

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomIterator
import org.biojava.nbio.structure.Structure

/**
 *
 */
@CompileStatic
abstract class GeometricTransformation {

    private String name

    GeometricTransformation(String name) {
        this.name = name
    }

    String getName() {
        return name
    }

    void applyToAtom(Atom atom) {
        transformAtom(atom)
    }

    void applyToAtoms(Iterable<Atom> atoms) {
        for (Atom atom : atoms) {
            transformAtom(atom)
        }
    }

    void applyToStructure(Structure structure) {
        AtomIterator iter = new AtomIterator(structure);

        while(iter.hasNext()) {
            Atom atom = iter.next();
            transformAtom(atom)
        }
    }

    abstract void transformAtom(Atom atom)
    
}
