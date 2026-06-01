package cz.siret.prank.geom;

import com.google.common.collect.Lists;
import cz.siret.prank.geom.kdtree.AtomKdTree;
import cz.siret.prank.program.params.Params;
import cz.siret.prank.utils.ATimer;
import cz.siret.prank.utils.CutoffAtomsCallLog;
import cz.siret.prank.utils.PerfUtils;
import org.biojava.nbio.structure.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static cz.siret.prank.utils.ATimer.startTimer;
import static cz.siret.prank.utils.Cutils.nonNullValues;

/**
 * list of atoms with additional properties
 */
public final class Atoms implements Iterable<Atom> {

    private static final Logger log = LoggerFactory.getLogger(Atoms.class);

    private static final int KD_TREE_THRESHOLD = 15;

    public final List<Atom> list;

    // lazy fields
    private Map<Integer, Atom> index;
    private AtomKdTree kdTree;
    private Atom centroid;
    private Atom centerOfMass;

    public Atoms() {
        list = new ArrayList<>();
    }

    public Atoms(int initialCapacity) {
        list = new ArrayList<>(initialCapacity);
    }

    @SuppressWarnings("unchecked")
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
            res.add(new Point(a.getX(), a.getY(), a.getZ()));
        }
        return res;
    }

    /**
     * Allows to cast to list of subtypes, e.g. LabeledPoint:
     * .<LabeledPoint>asList()
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> asList() {
        return (List<T>)list;
    }

    public Atom[] asArray() {
        return list.toArray(new Atom[0]);
    }

    /**
     * @return Atom objects with Points (all have unit C mass)
     */
    public Atoms toPoints() {
        return copyPoints(this.list.toArray(new Atom[0]));
    }

    public List<Integer> getIndexes() {
        return list.stream().map(Atom::getPDBserial).collect(Collectors.toList());
    }

    public Atoms(Atom atom) {
        this(Lists.newArrayList(atom));
    }

    public Atoms(Atoms atoms) {
        this(atoms.list);
    }

    public Atoms withIndex() {
        if (index == null) {
            index = new HashMap<>(list.size());
            for (Atom a : list) {
                index.put(a.getPDBserial(), a);
            }
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

    @NonNull
    @Override
    public Iterator<Atom> iterator() {
        return list.iterator();
    }

    /**
     * based on index and PDBSerial
     */
    public boolean contains(Atom a) {
        withIndex();
        return index.containsKey(a.getPDBserial());
    }

    /**
     * @param id pdb serial (PDBserial) of the atom
     */
    public Atom getByID(int id) {
        return index.get(id);
    }

    public int getCount() {
        return list.size();
    }

    public boolean isEmpty() {
        return getCount()==0;
    }

    public double dist(Atom a) {
        if (kdTree != null) {
            return kdTree.nearestDist(a);
        } else {
            return Struct.dist(a, list);
        }
    }

    public double sqrDist(Atom a) {
        if (kdTree != null) {
            return kdTree.nearestSqrDist(a);
        } else {
            return Struct.sqrDist(a, list);
        }
    }

    public double dist(Atoms toAtoms) {
        return Math.sqrt(sqrDist(toAtoms));
    }

    public double sqrDist(Atoms toAtoms) {
        double sqrMin = Double.POSITIVE_INFINITY;
        for (Atom a : toAtoms) {
            double next = sqrDist(a);
            if (next < sqrMin) {
                sqrMin = next;
            }
        }
        return sqrMin;
    }

    public boolean areWithinDistance(Atom a, double dist) {
        if (kdTree != null && getCount() > KD_TREE_THRESHOLD) {
            return kdTree.nearestDist(a) <= dist;
        } else {
            return PerfUtils.areWithinDistance(a, this.list, dist);
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
        return PerfUtils.areDistantAtLeast(a, this.list, dist);
    }

    public Atom findNearest(Atom point) {
        return withKdTree().kdTree.findNearest(point);
    }

    public Atom getCenterOfMass() {
        if (list.isEmpty()) {
            return null;
        }
        if (centerOfMass == null) {
            centerOfMass = PerfUtils.centerOfMass(list);
        }
        return centerOfMass;
    }

    public Atom getCentroid() {
        if (centroid == null) {
            centroid = PerfUtils.calculateCentroid(list);
        }
        return centroid;
    }

    public List<Group> getDistinctGroupsSorted() {

        return Struct.sortedGroups(getDistinctGroups());
    }

    /**
     * based on object identity
     */
    public List<Group> getDistinctGroups() {
        Set<Group> res = new HashSet<>();
        for (Atom a : list) {
            if (a.getGroup()!=null) {
                res.add(a.getGroup());
            }
        }

        return new ArrayList<>(res);
    }

    public void add(Atom a) {
        list.add(a);
        // invalidate, rebuilt lazily via withKdTree()/buildKdTree()
        kdTree = null;
    }

    public Atoms addAll(Atoms atoms) {
        list.addAll(atoms.list);
        kdTree = null; // invalidate
        return this;
    }

    public Atoms addAll(Collection<Atoms> col) {
        for (Atoms a : col) {
            addAll(a);
        }
        return this;
    }

    public static Atoms join(Collection<Atoms> col) {
        return new Atoms().addAll(col);
    }

    /**
     * @return new instance
     */
    public Atoms joinWith(Atoms atoms) {
        List<Atom> newlist = new ArrayList<>(list.size() + atoms.getCount());
        newlist.addAll(list);
        newlist.addAll(atoms.list);
        return new Atoms(newlist);
    }

    /**
     * @return new instance
     */
    public Atoms plus(Atoms atoms) {
        return joinWith(atoms);
    }

    public static Atoms union(Atoms... aa) {
        return union(Arrays.asList(aa));
    }

    public static Atoms union(Collection<Atoms> aa) {
        List<Atoms> aaa = nonNullValues(aa);
        int total = aaa.stream().mapToInt(Atoms::getCount).sum();

        Set<Atom> res = new HashSet<>(total);
        for (Atoms a : aaa) {
            res.addAll(a.list);
        }

        return new Atoms(res);
    }

    public static Atoms intersection(Atoms aa, Atoms bb) {
        Set<Atom> bset = new HashSet<>(bb.list);
        List<Atom> res = new ArrayList<>(Math.min(aa.getCount(), bb.getCount()));
        for (Atom a : aa) {
            if (bset.contains(a)) {
                res.add(a);
            }
        }
        return new Atoms(res);
    }

//===========================================================================================================//

    public Atoms cutoutShell(Atoms aroundAtoms, double dist) {
        if (aroundAtoms==null || aroundAtoms.isEmpty()) {
            return new Atoms(0);
        }

        Atom center;
        double additionalDist;
        if (aroundAtoms.getCount()==1) {
            center = aroundAtoms.list.get(0);
            return cutoutSphere(center, dist);

        } else {
            Box box = Box.aroundAtoms(aroundAtoms);
            center = box.getCenter();
            additionalDist = Struct.dist(center, box.getMax());

            Atoms ofAtoms = this.cutoutSphere(center, dist + additionalDist);
            return cutoutShell(ofAtoms, aroundAtoms, dist);
        }
    }

    public static Atoms cutoutShell(Atoms ofAtoms, Atoms aroundAtoms, double dist) {
        if (aroundAtoms==null || aroundAtoms.isEmpty()) {
            return new Atoms(0);
        }

        aroundAtoms.withKdTreeConditional();
        Atoms res = new Atoms(128);

        double sqrDist = dist*dist;
        for (Atom a : ofAtoms) {
            if (aroundAtoms.sqrDist(a) <= sqrDist) {
                res.add(a);
            }
        }

        return res;
    }

    /**
     * intercepting calls for further analysis
     */
    public Atoms cutSphere_(Atom distanceTo, double dist) {
        ATimer timer = startTimer();

        Atoms res = cutoutSphere(distanceTo, dist);

        CutoffAtomsCallLog.INST.addCall(getCount(), res.getCount(), timer.getTime());

        return res;
    }

    public Atoms cutoutSphereSerial(final Atom center, double radius) {
        List<Atom> res = new ArrayList<>();
        double sqrDist = radius*radius;

        for (Atom a : list) {
            if (PerfUtils.sqrDist(a, center) <= sqrDist) {
                res.add(a);
            }
        }
        return new Atoms(res);
    }

    public Atoms cutoutSphereKD(final Atom center, double radius) {
        withKdTree();
        return kdTree.findAtomsWithinRadius(center, radius, false);
    }

    /** Whether sphere queries on this set should use the KD-tree rather than a serial scan. */
    private boolean useKdTreeForSphereQuery() {
        return getCount() >= Params.INSTANCE.getUse_kdtree_cutout_sphere_thrashold();
    }

    public Atoms cutoutSphere(Atom center, double radius) {
        if (useKdTreeForSphereQuery()) {
            return cutoutSphereKD(center, radius);
        } else {
            return cutoutSphereSerial(center, radius);
        }
    }

    public int countWithinSphereSerial(final Atom center, double radius) {
        double sqrDist = radius*radius;
        int count = 0;
        for (Atom a : list) {
            if (PerfUtils.sqrDist(a, center) <= sqrDist) {
                count++;
            }
        }
        return count;
    }

    public int countWithinSphereKD(final Atom center, double radius) {
        withKdTree();
        return kdTree.countAtomsWithinRadius(center, radius);
    }

    /**
     * Count atoms within a sphere, without materializing an {@link Atoms} list.
     * Equivalent to {@code cutoutSphere(center, radius).getCount()} but allocation-free;
     * uses the same KD-tree-vs-serial threshold as {@link #cutoutSphere}.
     */
    public int countWithinSphere(Atom center, double radius) {
        if (useKdTreeForSphereQuery()) {
            return countWithinSphereKD(center, radius);
        } else {
            return countWithinSphereSerial(center, radius);
        }
    }


    public SphereLayers cutoutLayers(Atom center, double radiusInner, double radiusOuter) {
        Atoms outerSphere = cutoutSphere(center, radiusOuter);
        List<Atom> outerLayer = new ArrayList<>(outerSphere.getCount());
        List<Atom> innerSphere = new ArrayList<>(outerSphere.getCount());

        double sqrBorder = radiusInner*radiusInner;
        for (Atom a : outerSphere) {
            if (Struct.sqrDist(a, center) <= sqrBorder) {
                innerSphere.add(a);
            } else {
                outerLayer.add(a);
            }
        }

        return new SphereLayers(new Atoms(innerSphere), outerSphere, new Atoms(outerLayer));
    }

    public static class SphereLayers {
        public final Atoms outerSphere;
        public final Atoms innerSphere;
        public final Atoms outerLayer;

        public SphereLayers(Atoms innerSphere, Atoms outerSphere, Atoms outerLayer) {
            this.outerSphere = outerSphere;
            this.innerSphere = innerSphere;
            this.outerLayer = outerLayer;
        }

        public Atoms getOuterSphere() {
            return outerSphere;
        }

        public Atoms getInnerSphere() {
            return innerSphere;
        }

        public Atoms getOuterLater() {
            return outerLayer;
        }
    }


    public Atoms cutoutBox(Box box) {
        return new Atoms(Struct.cutoffAtomsInBox(this.list, box));
    }

    public Atoms withoutHydrogens() {
        return withoutHydrogenAtoms(this);
    }

    public Atoms without(Atoms remove) {
        List<Atom> res = new ArrayList<>(list.size());

        for (Atom a : list) {
            if (!remove.contains(a)) {
                res.add(a);
            }
        }

        return new Atoms(res);
    }

//===========================================================================================================//

    public static Atoms allFromStructure(Structure struc) {
        List<Atom> list = new ArrayList<>(4096);
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

    public static Atoms allFromGroup(Group group) {
        return new Atoms(group.getAtoms());
    }

    public static Atoms allFromGroups(List<? extends Group> groups) {
        Atoms res = new Atoms(groups.size() * 10);
        for (Group g : groups) {
            res.list.addAll(g.getAtoms());
        }
        return res;
    }

    public static Atoms allFromChain(Chain chain) {
        return allFromGroups(chain.getAtomGroups());
    }

}
