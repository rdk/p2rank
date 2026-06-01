package cz.siret.prank.geom.kdtree;

import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.kdtree.v1.AtomKdTreeV1;
import cz.siret.prank.geom.kdtree.v2.AtomKdTreeV2;
import cz.siret.prank.program.params.Params;
import org.biojava.nbio.structure.Atom;

/**
 * Interface for spatial atom queries backed by a KD-tree.
 *
 * Implementations:
 *   AtomKdTreeV1 — Rednaxela (mutable, generic N-dimensional)
 *   AtomKdTreeV2 — KdTree3D wrapper (immutable, SoA, hardcoded 3D)
 *
 * Use the static {@link #build(Atoms)} factory to obtain an instance
 * based on the {@code kdtree_implementation} runtime parameter.
 */
public interface AtomKdTree {

    static AtomKdTree build(Atoms atoms) {
        if ("AtomKdTreeV1".equals(Params.INSTANCE.getKdtree_implementation())) {
            return AtomKdTreeV1.build(atoms);
        }
        return AtomKdTreeV2.build(atoms);
    }

    int size();

    Atom findNearest(Atom a);
    double nearestSqrDist(Atom a);

    Atom findNearestDifferent(Atom a);
    double nearestDifferentSqrDist(Atom a);

    Atoms findNearestNAtoms(Atom a, int count, boolean sorted);
    Atoms findNearestNDifferentAtoms(Atom a, int count, boolean sorted);

    Atoms findAtomsWithinRadius(Atom a, double radius, boolean sorted);

    /**
     * Count atoms within {@code radius} of {@code a}. Default builds the result set and
     * counts it; implementations should override with an allocation-free variant.
     */
    default int countAtomsWithinRadius(Atom a, double radius) {
        return findAtomsWithinRadius(a, radius, false).getCount();
    }

    default double nearestDist(Atom a) {
        return Math.sqrt(nearestSqrDist(a));
    }

    default double nearestDifferentDist(Atom a) {
        return Math.sqrt(nearestDifferentSqrDist(a));
    }
}
