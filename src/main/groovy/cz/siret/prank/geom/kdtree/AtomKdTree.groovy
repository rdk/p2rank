package cz.siret.prank.geom.kdtree

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.kdtree.KdTree.Entry

@CompileStatic
public class AtomKdTree extends KdTree.SqrEuclid<Atom> {

    private static final int DIMENSIONS = 3

    AtomKdTree(int dimensions, Integer sizeLimit) {
        super(dimensions, sizeLimit)
    }

    public static AtomKdTree build(Atoms atoms) {
        AtomKdTree res = new AtomKdTree(DIMENSIONS, Integer.MAX_VALUE)
        res.addAll(atoms)
        return res
    }

    public add(Atom a) {
        addPoint(a.coords, a)
    }

    public addAll(Atoms atoms) {
        for (Atom a in atoms) {
            add(a)
        }
    }

    public Atom findNearest(Atom a) {
        def entry = singleNearestNeighbor(a.coords)

        return entry ? entry.value : null
    }

    public double nearestDist(Atom a) {
        double dist = nearestSqrDist(a)
        return Math.sqrt(dist)
    }

    public double nearestSqrDist(Atom a) {
        return singleNearestNeighbor(a.coords).distance
    }

    public Atom findNearestDifferent(Atom a) {
        return singleNearestDifferent(a)?.value
    }

    public double nearestDifferentDist(Atom a) {
        return Math.sqrt(nearestDifferentSqrDist(a))
    }

    public double nearestDifferentSqrDist(Atom a) {
        Entry<Atom> ent = singleNearestDifferent(a)
        return ent!=null ? ent.distance : Double.NaN
    }

    public Entry<Atom> singleNearestDifferent(Atom a) {
        List<Entry<Atom>> resList = nearestNeighbor(a.coords, 2, false)

        for (Entry<Atom> ent in resList) {
            if (!(ent.value == a)) {
                return ent
            }
        }

        return null
    }

    public List<Entry<Atom>> findNearestN(Atom a, int count, boolean sorted) {
        return nearestNeighbor(a.coords, count, sorted)
    }

    public List<Entry<Atom>> findNearestNDifferent(Atom a, int count, boolean sorted) {
        List<Entry<Atom>> aaa = nearestNeighbor(a.coords, count, sorted)

        // only java 1.8
//        aaa.removeIf(new Predicate<Entry<Atom>>() {
//            @Override
//            boolean test(Entry<Atom> atomEntry) {
//                atomEntry == a
//            }
//        })

        Iterator<Entry<Atom>> it = aaa.iterator();
        while (it.hasNext()) {
            Atom ai = it.next().value
            if (a.equals(ai)) {
                it.remove();
            }
        }

        return aaa
    }

    public Atoms findNearestNAtoms(Atom a, int count, boolean sorted) {
        return new Atoms((List<Atom>) findNearestN(a, count, sorted)*.value )
    }

    public Atoms findNearestNDifferentAtoms(Atom a, int count, boolean sorted) {
        return new Atoms((List<Atom>) findNearestNDifferent(a, count, sorted)*.value )
    }

    public final Atoms findAtomsWithinRadius(Atom a, double radius, boolean sorted) {
        radius = radius*radius // since we inherit from SqrEuclid

        return new Atoms((List<Atom>) neighboursWithinRadius(a.coords, radius, sorted)*.value )
    }

}
