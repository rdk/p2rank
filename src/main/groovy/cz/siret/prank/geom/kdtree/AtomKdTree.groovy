package cz.siret.prank.geom.kdtree

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
class AtomKdTree extends KdTree.SqrEuclid3D<Atom> {

    AtomKdTree(Integer sizeLimit) {
        super(sizeLimit)
    }

    public static AtomKdTree build(Atoms atoms) {
        AtomKdTree res = new AtomKdTree(Integer.MAX_VALUE)
        res.addAll(atoms)
        return res
    }

//===========================================================================================================//

    public add(Atom a) {
        addPoint(a.coords, a)
    }

    public addAll(Atoms atoms) {
        for (Atom a : atoms) {
            add(a)
        }
    }

    public Atom findNearest(Atom a) {
        return singleNearestNeighbor(a.coords)?.value
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
        return atoms(findNearestN(a, count, sorted))
    }

    public Atoms findNearestNDifferentAtoms(Atom a, int count, boolean sorted) {
        return atoms( findNearestNDifferent(a, count, sorted))
    }

    public final Atoms findAtomsWithinRadius(Atom a, double radius, boolean sorted) {
        radius = radius*radius // since we inherit from SqrEuclid

        return atoms(neighboursWithinRadius(a.coords, radius, sorted))
    }

    private Atoms atoms(List<Entry<Atom>> entries) {
        List<Atom> list = new ArrayList<>(entries.size());
        for (Entry<Atom> e : entries) {
            list.add(e.value)
        }
        return new Atoms(list)
    }

}
