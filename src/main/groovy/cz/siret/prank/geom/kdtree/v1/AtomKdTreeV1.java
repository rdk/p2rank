package cz.siret.prank.geom.kdtree.v1;

import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.kdtree.AtomKdTree;
import org.biojava.nbio.structure.Atom;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AtomKdTreeV1 extends KdTree.SqrEuclid3D<Atom> implements AtomKdTree {

    public AtomKdTreeV1(int sizeLimit) {
        super(sizeLimit);
    }

    public static AtomKdTreeV1 build(Atoms atoms) {
        AtomKdTreeV1 res = new AtomKdTreeV1(Integer.MAX_VALUE);
        for (Atom a : atoms.list) {
            res.addPoint(a.getCoords(), a);
        }
        return res;
    }

    @Override
    public Atom findNearest(Atom a) {
        Entry<Atom> entry = singleNearestNeighbor(a.getCoords());
        return entry != null ? entry.value : null;
    }

    @Override
    public double nearestSqrDist(Atom a) {
        return singleNearestNeighbor(a.getCoords()).distance;
    }

    @Override
    public Atom findNearestDifferent(Atom a) {
        Entry<Atom> entry = singleNearestDifferent(a);
        return entry != null ? entry.value : null;
    }

    @Override
    public double nearestDifferentSqrDist(Atom a) {
        Entry<Atom> entry = singleNearestDifferent(a);
        return entry != null ? entry.distance : Double.NaN;
    }

    private Entry<Atom> singleNearestDifferent(Atom a) {
        List<Entry<Atom>> resList = nearestNeighbor(a.getCoords(), 2, false);
        for (Entry<Atom> ent : resList) {
            if (ent.value != a) {
                return ent;
            }
        }
        return null;
    }

    @Override
    public Atoms findNearestNAtoms(Atom a, int count, boolean sorted) {
        return toAtoms(nearestNeighbor(a.getCoords(), count, sorted));
    }

    @Override
    public Atoms findNearestNDifferentAtoms(Atom a, int count, boolean sorted) {
        List<Entry<Atom>> entries = nearestNeighbor(a.getCoords(), count, sorted);
        Iterator<Entry<Atom>> it = entries.iterator();
        while (it.hasNext()) {
            if (it.next().value == a) {
                it.remove();
            }
        }
        return toAtoms(entries);
    }

    @Override
    public Atoms findAtomsWithinRadius(Atom a, double radius, boolean sorted) {
        double sqrRadius = radius * radius; // SqrEuclid uses squared distances
        return toAtoms(neighboursWithinRadius(a.getCoords(), sqrRadius, sorted));
    }

    private static Atoms toAtoms(List<Entry<Atom>> entries) {
        List<Atom> list = new ArrayList<>(entries.size());
        for (Entry<Atom> e : entries) {
            list.add(e.value);
        }
        return new Atoms(list);
    }
}
