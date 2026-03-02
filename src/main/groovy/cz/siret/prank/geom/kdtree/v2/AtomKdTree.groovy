package cz.siret.prank.geom.kdtree.v2

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Drop-in replacement for kdtree.AtomKdTree, delegating to immutable KdTree3D.
 *
 * Same class name and method signatures as the v1 AtomKdTree — only the import
 * in Atoms.java needs to change. Groovy callers accessing via Atoms.getKdTree()
 * are transparent (duck typing).
 *
 * Key difference from v1: no add()/addAll() — tree is immutable.
 * Atoms.add() invalidates the tree; it gets rebuilt lazily via withKdTree().
 *
 * Uses getX()/getY()/getZ() instead of .coords — eliminates new double[3] allocation per call.
 */
@CompileStatic
class AtomKdTree {

    private final KdTree3D tree

    private AtomKdTree(KdTree3D tree) {
        this.tree = tree
    }

    static AtomKdTree build(Atoms atoms) {
        return new AtomKdTree(KdTree3D.build(atoms))
    }

    int size() {
        return tree.size()
    }

    // --- Single nearest neighbor ---

    Atom findNearest(Atom a) {
        return tree.findNearest(a.getX(), a.getY(), a.getZ())
    }

    double nearestDist(Atom a) {
        return Math.sqrt(nearestSqrDist(a))
    }

    double nearestSqrDist(Atom a) {
        return tree.nearestSqrDist(a.getX(), a.getY(), a.getZ())
    }

    // --- Nearest different (excluding identity-equal atom) ---

    /**
     * Find nearest atom that is not identity-equal to a.
     * Uses k-NN with k=2 and filters. Returns null if no different atom exists.
     */
    Atom findNearestDifferent(Atom a) {
        KdTree3D.NNEntry entry = singleNearestDifferent(a)
        return entry?.atom()
    }

    double nearestDifferentDist(Atom a) {
        return Math.sqrt(nearestDifferentSqrDist(a))
    }

    double nearestDifferentSqrDist(Atom a) {
        KdTree3D.NNEntry entry = singleNearestDifferent(a)
        return entry != null ? entry.sqrDist() : Double.NaN
    }

    /**
     * Internal: find nearest non-self entry.
     * Requests k=2 neighbors and picks the one that isn't identity-equal to a.
     * If both are identity-equal (duplicate points), returns null.
     */
    private KdTree3D.NNEntry singleNearestDifferent(Atom a) {
        List<KdTree3D.NNEntry> entries = tree.findNearestN(a.getX(), a.getY(), a.getZ(), 2, false)
        for (KdTree3D.NNEntry entry : entries) {
            if (!(entry.atom().is(a))) {  // identity check, not equals()
                return entry
            }
        }
        return null
    }

    // --- k-NN ---

    List<KdTree3D.NNEntry> findNearestN(Atom a, int count, boolean sorted) {
        return tree.findNearestN(a.getX(), a.getY(), a.getZ(), count, sorted)
    }

    Atoms findNearestNAtoms(Atom a, int count, boolean sorted) {
        return toAtoms(findNearestN(a, count, sorted))
    }

    List<KdTree3D.NNEntry> findNearestNDifferent(Atom a, int count, boolean sorted) {
        // Request count+1 to account for the self-match, then filter
        List<KdTree3D.NNEntry> entries = tree.findNearestN(a.getX(), a.getY(), a.getZ(), count + 1, sorted)
        entries.removeIf { KdTree3D.NNEntry entry -> entry.atom().is(a) }
        // Trim to requested count (in case self wasn't in results)
        if (entries.size() > count) {
            entries = entries.subList(0, count)
        }
        return entries
    }

    Atoms findNearestNDifferentAtoms(Atom a, int count, boolean sorted) {
        return toAtoms(findNearestNDifferent(a, count, sorted))
    }

    // --- Radius search ---

    /**
     * Find all atoms within radius of a.
     * Squares the radius (KdTree3D works in squared distances throughout).
     * sorted param kept for API compat but ignored — always false in production
     * (only caller is Atoms.cutoutSphereKD which passes false).
     */
    Atoms findAtomsWithinRadius(Atom a, double radius, boolean sorted) {
        double sqrRadius = radius * radius
        return tree.findWithinRadius(a.getX(), a.getY(), a.getZ(), sqrRadius)
    }

    // --- Helpers ---

    private static Atoms toAtoms(List<KdTree3D.NNEntry> entries) {
        List<Atom> list = new ArrayList<>(entries.size())
        for (KdTree3D.NNEntry e : entries) {
            list.add(e.atom())
        }
        return new Atoms(list)
    }
}
