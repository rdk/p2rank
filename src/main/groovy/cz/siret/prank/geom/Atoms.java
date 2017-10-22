package cz.siret.prank.geom;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import cz.siret.prank.geom.kdtree.AtomKdTree;
import cz.siret.prank.utils.ATimer;
import cz.siret.prank.utils.CutoffAtomsCallLog;
import cz.siret.prank.utils.PerfUtils;
import org.biojava.nbio.structure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static cz.siret.prank.utils.ATimer.startTimer;

/**
 * list of atoms with additional properties
 */
public final class Atoms implements Iterable<Atom> {

    private static final Logger log = LoggerFactory.getLogger(Atoms.class);

    private static final int KD_TREE_THRESHOLD = 15;

    public final List<Atom> list;

    private Map<Integer, Atom> index;
    private AtomKdTree kdTree;
    private Atom centerOfMass;

    public Atoms() {
        list = new ArrayList<>();
    }

    public Atoms(int initialCapacity) {
        list = new ArrayList<>(initialCapacity);
    }

    public Atoms(List<? extends Atom> list) {
        if (list == null) throw new AssertionError();
        this.list = (List<Atom>) list;
    }

    public Atoms(Collection<? extends Atom> collection) {
        if (collection == null) throw new AssertionError();
        this.list = new ArrayList<>(collection.size());
        this.list.addAll(collection);
    }

    /**
     * copy atoms and fill with points
     */
    public static Atoms copyPoints(Atom... atoms) {
        Atoms res = new Atoms(atoms.length);
        for (Atom a: atoms) {
            res.add(new Point(a.getCoords()));
        }
        return res;
    }

    /**
     * @return Atom objects with Points (all have unit C mass)
     */
    public Atoms toPoints() {
        return copyPoints(this.list.toArray(new Atom[0]));
    }

    public Atoms(Atom atom) {
        this(Lists.newArrayList(atom));
    }

    public Atoms(Atoms atoms) {
        this(atoms.list);
    }

    public Atoms withIndex() {
        index = new HashMap<>(list.size());
        for (Atom a : list) {
            index.put(a.getPDBserial(), a);
        }
        return this;
    }

    /**
     * @return conditionally builds KDTree
     */
    public Atoms withKdTreeConditional() {
        if (getCount() > KD_TREE_THRESHOLD) {
            if (kdTree==null || kdTree.size()!=getCount()) {
                buildKdTree();
            }
        }
        return this;
    }

    public Atoms withKdTree() {
        if (kdTree==null) {
            buildKdTree();
        }
        return this;
    }

    /**
     * @return builds KDTree
     */
    public Atoms buildKdTree() {
        kdTree = AtomKdTree.build(this);
        return this;
    }

    public AtomKdTree getKdTree() {
        return kdTree;
    }

    @Override
    public Iterator<Atom> iterator() {
        return list.iterator();
    }

    /**
     * @param id pdb serial (PDBserial) of the atom
     */
    public Atom getByID(int id) {
        return this.index.get(id);
    }

    public int getCount() {
        return list.size();
    }

    public boolean isEmpty() {
        return getCount()==0;
    }

    public double dist(Atom a) {
        if (kdTree!=null && getCount() > KD_TREE_THRESHOLD) {
            return kdTree.nearestDist(a);
        } else {
            return Struct.dist(a, list);
        }
    }

    public double sqrDist(Atom a) {
        if (kdTree!=null && getCount() > KD_TREE_THRESHOLD) {
            return kdTree.nearestSqrDist(a);
        } else {
            return Struct.sqrDist(a, list);
        }
    }

    public double dist(Atoms toAtoms) {
        double sqrMin = Double.POSITIVE_INFINITY;
        for (Atom a : toAtoms) {
            double next = sqrDist(a);
            if (next < sqrMin) {
                sqrMin = next;
            }
        }
        return Math.sqrt(sqrMin);
    }

    public boolean areWithinDistance(Atom a, double dist) {
        if (kdTree!=null && getCount() > KD_TREE_THRESHOLD) {
            return kdTree.nearestDist(a) <= dist;
        } else {
            return Struct.areWithinDistance(a, this.list, dist);
        }
    }

    public boolean areWithinDistance(Atoms toAtoms, double dist) {

        return areWithinDistance(this, toAtoms, dist);
    }

    private static boolean areWithinDistance(Atoms aa, Atoms ab, double dist) {

        Atoms bigger;
        Atoms smaller;

        if (ab.getCount() > aa.getCount()) {
            bigger = ab;
            smaller = aa;
        } else {
            bigger = aa;
            smaller = ab;
        }

        bigger.withKdTreeConditional();
        for (Atom a : smaller) {
            if (bigger.areWithinDistance(a, dist)) {   // give a chance to kdtree
                return true;
            }
        }

        return false;
    }

    public boolean areDistantFromAtomAtLeast(Atom a, double dist) {
        return Struct.areDistantAtLeast(a, this.list, dist);
    }

    public Atom getCenterOfMass() {
        if (centerOfMass==null) {
            Atom[] aa = new Atom[list.size()];
            aa = list.toArray(aa);
            centerOfMass = Calc.centerOfMass(aa);
        }
        return centerOfMass;
    }

    public List<Group> getDistinctGroups() {
        Set<Group> res = new HashSet<>();
        for (Atom a : list) {
            if (a.getGroup()!=null) {
                res.add(a.getGroup());
            }
        }

        List<Group> sres = Struct.sortGroups(res);

        return sres;
    }

    public void add(Atom a) {
        list.add(a);
        if (kdTree!=null) {
            kdTree.add(a);
        }
    }

    public Atoms addAll(Atoms atoms) {
        list.addAll(atoms.list);
        if (kdTree!=null) {
            kdTree.addAll(atoms);
        }
        return this;
    }

    public Atoms addAll(Collection<Atoms> col) {
        for (Atoms a : col) {
            addAll(a);
        }
        return this;
    }

    public static Atoms joinAll(Collection<Atoms> col) {
        return new Atoms().addAll(col);
    }

    /**
     * @return new instance
     */
    public Atoms join(Atoms atoms) {
        List<Atom> newlist = new ArrayList<>(list.size() + atoms.getCount());
        newlist.addAll(list);
        newlist.addAll(atoms.list);
        return new Atoms(newlist);
    }

    /**
     * @return new instance
     */
    public Atoms plus(Atoms atoms) {
        return join(atoms);
    }

    public static Atoms union(Atoms... aa) {
        return union(Arrays.asList(aa));
    }

    public static Atoms union(Collection<Atoms> aa) {
        Set<Atom> res = new HashSet<>();
        for (Atoms a : aa) {
            res.addAll(a.list);
        }

        return new Atoms(res);
    }

    public static Atoms intersection(Atoms aa, Atoms bb) {
        Set<Atom> bset = new HashSet<>(bb.list);
        List<Atom> res = new ArrayList<>(aa.getCount());
        for (Atom a : aa) {
            if (bset.contains(a)) {
                res.add(a);
            }
        }
        return new Atoms(res);
    }

//===========================================================================================================//

    public Atoms cutoffAtoms(Atoms aroundAtoms, double dist) {
        aroundAtoms.withKdTreeConditional();
        Atoms res = new Atoms(100);

        double sqrDist = dist*dist;
        for (Atom a : list) {
            if (aroundAtoms.sqrDist(a) <= sqrDist) {
                res.add(a);
            }
        }

        return res;
    }


    /**
     * intercepting calls for further alalysis
     */
    public Atoms cutoffAroundAtom_(Atom distanceTo, double dist) {
        ATimer timer = startTimer();

        Atoms res = doCutoffAroundAtom(distanceTo, dist);

        CutoffAtomsCallLog.INST.addCall(getCount(), res.getCount(), timer.getTime());

        return res;
    }

    private Atoms doCutoffAroundAtom(Atom distanceTo, double dist) {
        List<Atom> res = new ArrayList<>();
        double sqrDist = dist*dist;

        double[] bcoords = distanceTo.getCoords();

        for (Object o : list.toArray()) {

            Atom a = (Atom)o;
            double[] acoords = a.getCoords();

            double x = acoords[0] - bcoords[0];
            double y = acoords[1] - bcoords[1];
            double z = acoords[2] - bcoords[2];

            double d = x*x + y*y + z*z;

            if (d <= sqrDist) {
                res.add(a);
            }
        }
        return new Atoms(res);
    }

    public Atoms cutoffAroundAtom(Atom distanceTo, double dist) {
        List<Atom> res = new ArrayList<>();
        double sqrDist = dist*dist;

        double[] toCoords = distanceTo.getCoords();

        for (Atom a : list) {
            if (PerfUtils.sqrDist(a.getCoords(), toCoords) <= sqrDist) {
                res.add(a);
            }
        }
        return new Atoms(res);
    }

    public Atoms cutoffAtomsInBox(Box box) {
        return new Atoms(Struct.cutoffAtomsInBox(this.list, box));
    }

    public Atoms withoutHydrogens() {
        return withoutHydrogenAtoms(this);
    }

//===========================================================================================================//

    /**
     * delete atoms that are too close together
     */
    public static Atoms consolidate(Atoms atoms, double dist) {
        Atoms res = new Atoms().buildKdTree();

        dist = dist*dist;

        for (Atom a : atoms) {
            Atom nearest = res.kdTree.findNearest(a);

            if (nearest==null || Struct.sqrDist(a, nearest) > dist) {
                res.add(a);
            }
        }

        return res;
    }

    public static Atoms allFromStructure(Structure struc) {
        List<Atom> list = new ArrayList<>(3000);
        AtomIterator atomIterator = new AtomIterator(struc);

        while (atomIterator.hasNext()) {
            list.add(atomIterator.next());
        }
        return new Atoms(list);
    }

    public static Atoms withoutHydrogenAtoms(Atoms atoms) {
        Atoms res = new Atoms(atoms.getCount());

        for (Atom a : atoms) {
            if (!Struct.isHydrogenAtom(a)) {
                res.add(a);
            }
        }

        return res;
    }

    public static Atoms onlyProteinAtoms(Atoms structAtoms) {
        List<Atom> res = new ArrayList<>(structAtoms.getCount());
        for (Atom a : structAtoms) {
            if (Struct.isProteinChainGroup(a.getGroup())) {
                res.add(a);
            }
        }

        return new Atoms(res);
    }

    public static Atoms onlyProteinAtoms(Structure struc) {
        // TODO UNK residues, double models
        return onlyProteinAtoms(allFromStructure(struc));
    }

    public static Atoms allFromGroup(Group group) {
        return new Atoms(group.getAtoms());
    }

    public static Atoms allFromGroups(List<? extends Group> groups) {
        Atoms res = new Atoms();
        for (Group g : groups) {
            res.list.addAll(g.getAtoms());
        }
        return res;
    }

}
