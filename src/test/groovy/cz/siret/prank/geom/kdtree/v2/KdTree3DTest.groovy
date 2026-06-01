package cz.siret.prank.geom.kdtree.v2

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.utils.PerfUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for KdTree3D and v2.AtomKdTreeV2.
 *
 * Parity tests compare v2 results against brute-force serial computation
 * on real protein data to catch any algorithmic bugs.
 */
@CompileStatic
class KdTree3DTest {

    // ==================== Parity tests with real protein data ====================

    @Test
    void findWithinRadius_parity() {
        // Compare KdTree radius results vs brute-force serial scan
        test_findWithinRadius_parity('distro/test_data/2W83.pdb')
        test_findWithinRadius_parity('distro/test_data/1fbl.pdb.gz')
    }

    void test_findWithinRadius_parity(String fname) {
        Protein p = Protein.load(fname)
        double RADIUS = 6d

        Atoms atoms = p.proteinAtoms
        AtomKdTreeV2 kdTree = AtomKdTreeV2.build(atoms)

        for (Atom a : atoms) {
            // Brute-force serial scan
            Atoms serial = atoms.cutoutSphereSerial(a, RADIUS)
            // v2 KdTree
            Atoms kd = kdTree.findAtomsWithinRadius(a, RADIUS, false)

            assertEquals(serial.list.toSet(), kd.list.toSet(),
                    "Radius query mismatch for atom ${a.getPDBserial()} in $fname")
        }
    }

    @Test
    void countWithinRadius_matchesFind() {
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        KdTree3D tree = KdTree3D.build(atoms.list)

        for (double radius : [2.0d, 6.0d, 10.0d]) {
            double sqrR = radius * radius
            for (Atom a : atoms) {
                int found = tree.findWithinRadius(a.getX(), a.getY(), a.getZ(), sqrR).getCount()
                int counted = tree.countWithinRadius(a.getX(), a.getY(), a.getZ(), sqrR)
                assertEquals(found, counted,
                        "count != find-count for atom ${a.getPDBserial()} at radius=$radius")
            }
        }
    }

    @Test
    void findWithinRadius_multipleRadii() {
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        AtomKdTreeV2 kdTree = AtomKdTreeV2.build(atoms)

        // Test with different radii to exercise different pruning paths
        for (double radius : [2d, 6d, 10d, 15d]) {
            Atom testAtom = atoms.list.get((int) (atoms.getCount() / 2))
            Atoms serial = atoms.cutoutSphereSerial(testAtom, radius)
            Atoms kd = kdTree.findAtomsWithinRadius(testAtom, radius, false)

            assertEquals(serial.list.toSet(), kd.list.toSet(),
                    "Radius=$radius mismatch")
        }
    }

    @Test
    void findNearest_parity() {
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        AtomKdTreeV2 kdTree = AtomKdTreeV2.build(atoms)

        for (Atom a : atoms) {
            // Brute-force: find nearest by scanning all
            double bestDist = Double.MAX_VALUE
            Atom bestAtom = null
            for (Atom b : atoms.list) {
                double d = PerfUtils.sqrDist(a, b)
                if (d < bestDist) {
                    bestDist = d
                    bestAtom = b
                }
            }

            Atom kdNearest = kdTree.findNearest(a)
            assertNotNull(kdNearest, "findNearest should not return null for non-empty tree")
            // Both should return the same atom (or at least one at the same distance)
            assertEquals(bestDist, kdTree.nearestSqrDist(a), 1e-10,
                    "nearestSqrDist mismatch for atom ${a.getPDBserial()}")
        }
    }

    @Test
    void findNearestNAtoms_parity() {
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        AtomKdTreeV2 kdTree = AtomKdTreeV2.build(atoms)

        int k = 9 // same as PyramidFeature usage

        // Test on a sample of atoms
        int step = Math.max(1, (int) (atoms.getCount() / 20))
        for (int idx = 0; idx < atoms.getCount(); idx += step) {
            Atom a = atoms.list.get(idx)

            // Brute-force k-NN
            List<double[]> allDists = []
            for (int i = 0; i < atoms.getCount(); i++) {
                double d = PerfUtils.sqrDist(a, atoms.list.get(i))
                allDists.add([d, i] as double[])
            }
            allDists.sort { double[] x -> x[0] }
            Set<Atom> serialSet = new HashSet<>()
            for (int i = 0; i < Math.min(k, allDists.size()); i++) {
                serialSet.add(atoms.list.get((int) allDists.get(i)[1]))
            }

            Atoms kdResult = kdTree.findNearestNAtoms(a, k, true)
            assertEquals(serialSet, kdResult.list.toSet(),
                    "k-NN mismatch for atom ${a.getPDBserial()}, k=$k")
        }
    }

    // ==================== Functional tests ====================

    @Test
    void emptyTree() {
        Atoms empty = new Atoms(0)
        AtomKdTreeV2 tree = AtomKdTreeV2.build(empty)

        assertEquals(0, tree.size())
        assertNull(tree.findNearest(new Point(0, 0, 0)))
        assertEquals(0, tree.findAtomsWithinRadius(new Point(0, 0, 0), 10d, false).getCount())
    }

    @Test
    void singlePoint() {
        Point p = new Point(1, 2, 3)
        AtomKdTreeV2 tree = AtomKdTreeV2.build(new Atoms(p))

        assertEquals(1, tree.size())
        assertSame(p, tree.findNearest(new Point(0, 0, 0)))
        assertEquals(14d, tree.nearestSqrDist(new Point(0, 0, 0)), 1e-10) // 1+4+9=14

        // Radius that includes the point
        assertEquals(1, tree.findAtomsWithinRadius(new Point(0, 0, 0), 4d, false).getCount())
        // Radius that excludes the point
        assertEquals(0, tree.findAtomsWithinRadius(new Point(0, 0, 0), 3d, false).getCount())
    }

    @Test
    void twoPoints() {
        Point p1 = new Point(0, 0, 0)
        Point p2 = new Point(10, 0, 0)
        AtomKdTreeV2 tree = AtomKdTreeV2.build(new Atoms([p1, p2] as List<Atom>))

        assertEquals(2, tree.size())
        assertSame(p1, tree.findNearest(new Point(1, 0, 0)))
        assertSame(p2, tree.findNearest(new Point(9, 0, 0)))
    }

    @Test
    void collinearPoints() {
        // All points on the x-axis — tests 1D degenerate case
        List<Atom> points = []
        for (int i = 0; i < 100; i++) {
            points.add(new Point(i as double, 0, 0))
        }
        AtomKdTreeV2 tree = AtomKdTreeV2.build(new Atoms(points))
        assertEquals(100, tree.size())

        // Nearest to (50.4, 0, 0) should be the point at x=50
        Atom nearest = tree.findNearest(new Point(50.4, 0, 0))
        assertEquals(50d, nearest.getX(), 1e-10)
    }

    @Test
    void identicalPoints() {
        // All points at the same location — edge case for splitting
        List<Atom> points = []
        for (int i = 0; i < 50; i++) {
            points.add(new Point(5, 5, 5))
        }
        AtomKdTreeV2 tree = AtomKdTreeV2.build(new Atoms(points))
        assertEquals(50, tree.size())

        // All should be within any radius
        assertEquals(50, tree.findAtomsWithinRadius(new Point(5, 5, 5), 0.1d, false).getCount())
        assertEquals(0d, tree.nearestSqrDist(new Point(5, 5, 5)), 1e-10)
    }

    @Test
    void findNearestDifferent_skipsSelf() {
        Point p1 = new Point(0, 0, 0)
        Point p2 = new Point(1, 0, 0)
        Point p3 = new Point(10, 0, 0)
        AtomKdTreeV2 tree = AtomKdTreeV2.build(new Atoms([p1, p2, p3] as List<Atom>))

        // findNearestDifferent(p1) should return p2, not p1 itself
        Atom different = tree.findNearestDifferent(p1)
        assertSame(p2, different)
    }

    // ==================== Thread-safety test ====================

    @Test
    void concurrentQueries_threadSafe() {
        // Build tree, then query from multiple threads simultaneously.
        // v1 KdTree would fail here due to mutable node.status field.
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        AtomKdTreeV2 kdTree = AtomKdTreeV2.build(atoms)

        double RADIUS = 6d
        int THREADS = 8

        // Pre-compute expected results serially
        Map<Integer, Set<Atom>> expected = new HashMap<>()
        for (Atom a : atoms) {
            expected.put(a.getPDBserial(), atoms.cutoutSphereSerial(a, RADIUS).list.toSet())
        }

        // Run concurrent queries
        AtomicInteger errors = new AtomicInteger(0)
        CyclicBarrier barrier = new CyclicBarrier(THREADS)
        List<Thread> threads = []

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t
            threads.add(new Thread({
                barrier.await() // synchronize start for maximum contention
                for (int i = threadId; i < atoms.getCount(); i += THREADS) {
                    Atom a = atoms.list.get(i)
                    Atoms result = kdTree.findAtomsWithinRadius(a, RADIUS, false)
                    Set<Atom> resultSet = result.list.toSet()
                    if (resultSet != expected.get(a.getPDBserial())) {
                        errors.incrementAndGet()
                    }
                }
            }))
        }

        threads.each { it.start() }
        threads.each { it.join() }

        assertEquals(0, errors.get(),
                "Concurrent radius queries produced incorrect results (thread-safety bug)")
    }
}
