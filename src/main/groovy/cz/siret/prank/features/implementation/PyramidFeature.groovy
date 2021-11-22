package cz.siret.prank.features.implementation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.geom.Struct.dist

/**
 * Simple geometric feature
 */
@CompileStatic
class PyramidFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "pyramid"

    final List<String> HEADER = new ArrayList<>()

    PyramidFeature() {
        for (int pi : [1, 2, 3, 4]) {
            for (String ft : ["dc", "surf"]) {
                HEADER.add "${name}_${pi}_${ft}".toString()
            }
        }
    }

    @Override
    String getName() { NAME }

    @Override
    List<String> getHeader() {
        HEADER
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.exposedAtoms.buildKdTree()
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        Atoms nearest = context.protein.exposedAtoms.kdTree.findNearestNAtoms(sasPoint, 9, true)

        Pyramid p1 = new Pyramid(sasPoint, nearest[0], nearest[1], nearest[2])
        Pyramid p2 = new Pyramid(sasPoint, nearest[3], nearest[4], nearest[5])
        Pyramid p3 = new Pyramid(sasPoint, nearest[0], nearest[2], nearest[4])
        Pyramid p4 = new Pyramid(sasPoint, nearest[6], nearest[7], nearest[8])

        double p1_dc = dist(sasPoint, p1.centroid)
        double p2_dc = dist(sasPoint, p2.centroid)
        double p3_dc = dist(sasPoint, p3.centroid)
        double p4_dc = dist(sasPoint, p4.centroid)

        return [p1_dc, p1.surface, p2_dc, p2.surface, p3_dc, p3.surface, p4_dc, p4.surface] as double[]
    }

    Point point(Atom a) {
        new Point(a.coords)
    }

    static class Pyramid {
        Atom a
        Atom b
        Atom c
        Atom d

        Pyramid(Atom a, Atom b, Atom c, Atom d) {
            this.a = a
            this.b = b
            this.c = c
            this.d = d
        }

        Atom getCentroid() {
            Atoms.calculateCentroid([a, b, c, d])
        }

        double getSurface() {
            pyramidSurface(a, b, c, d)
        }

        /**
         * @return triangle surface from side lengths
         */
        static double tsurf(double sa, double sb, double sc) {
            double s = (sa + sb + sc) / 2
            return Math.sqrt(s * (s - sa) * (s - sb) * (s-sc)) * 0.5
        }

        static double pyramidSurface(Atom a, Atom b, Atom c, Atom d) {
            double ab = dist(a, b)
            double ac = dist(a, c)
            double ad = dist(a, d)
            double bc = dist(b, c)
            double bd = dist(b, d)
            double cd = dist(c, d)

            tsurf(ab, ac, bc) + tsurf(ab, ad, bd) + tsurf(bc, bd, cd) + tsurf(ac, ad, cd)
        }

    }

}
