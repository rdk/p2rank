package cz.siret.prank.utils

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.openscience.cdk.silent.AtomContainer
import org.openscience.cdk.config.Elements
import org.openscience.cdk.interfaces.IAtom
import org.openscience.cdk.interfaces.IAtomContainer

import javax.vecmath.Point3d

/**
 * Interface to CDK geometric functions.
 */
@Slf4j
@CompileStatic
class CdkUtils {

    static IAtom bioJavaToCDKAtom(Atom atom) {
        Point3d point = atom.getCoordsAsPoint3d()

        String elementSymbol = "C"
        if (atom.element!=null) {
            elementSymbol = atom.element.name()
        }

        if (Elements.ofString(elementSymbol).equals(Elements.Unknown)) {
            //String bad = elementSymbol
            elementSymbol = atom.name.substring(0,1)
            if (Elements.ofString(elementSymbol).equals(Elements.Unknown)) {
                elementSymbol = "C"
            }
            //log.warn "Element unknown to CDK: $bad (from {}) - using {}", atom.name, elementSymbol
        }

        IAtom res = new org.openscience.cdk.silent.Atom(elementSymbol, point)

        return res
    }

    static IAtomContainer toAtomContainer(Atoms atoms) {

        AtomContainer container = new AtomContainer(atoms.count, 0, 0, 0)

        IAtom[] cdkAtoms = new IAtom[atoms.count]

        int i = 0
        for (Atom a in atoms) {
            cdkAtoms[i] = bioJavaToCDKAtom(a)
            i++
        }

        container.setAtoms(cdkAtoms)

        return container
    }

    static Point toAtomPoint(Point3d pt) {
        double[] coords = new double[3]
        pt.get(coords)
        Point res = new Point(coords)
        return res
    }

    static Atoms toAtomPoints(Point3d[] pts) {
        List<Atom> res = new ArrayList<Atom>(pts.length)
        for (Point3d pt in pts) {
            res.add(toAtomPoint(pt))
        }
        return new Atoms(res)
    }

}
