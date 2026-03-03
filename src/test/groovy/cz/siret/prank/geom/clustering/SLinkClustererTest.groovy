package cz.siret.prank.geom.clustering

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class SLinkClustererTest {

    static final Clusterer.Distance<Integer> INT_DIST = new Clusterer.Distance<Integer>() {
        @Override
        double dist(Integer a, Integer b) {
            return Math.abs(a - b) as double
        }
    }

    private static Set<Set<Integer>> toSetOfSets(List<List<Integer>> clusters) {
        return clusters.collect { it.toSet() }.toSet()
    }

    // ==================== Basic correctness ====================

    @Test
    void emptyInput() {
        def v1 = new SLinkClusterer<Integer>().cluster([], 1.0, INT_DIST)
        def v2 = new SLinkClustererV2<Integer>().cluster([], 1.0, INT_DIST)
        assertTrue(v1.isEmpty())
        assertTrue(v2.isEmpty())
    }

    @Test
    void singleElement() {
        def v1 = new SLinkClusterer<Integer>().cluster([42], 1.0, INT_DIST)
        def v2 = new SLinkClustererV2<Integer>().cluster([42], 1.0, INT_DIST)
        assertEquals(toSetOfSets(v1), toSetOfSets(v2))
        assertEquals(1, v2.size())
        assertEquals([42], v2[0])
    }

    @Test
    void twoClusters() {
        // 1,2,3 should cluster together; 10,11 should cluster together (dist <= 2)
        def elements = [1, 2, 3, 10, 11]
        def v1 = new SLinkClusterer<Integer>().cluster(elements, 2.0, INT_DIST)
        def v2 = new SLinkClustererV2<Integer>().cluster(elements, 2.0, INT_DIST)

        def expected = [[1, 2, 3].toSet(), [10, 11].toSet()].toSet()
        assertEquals(expected, toSetOfSets(v1))
        assertEquals(expected, toSetOfSets(v2))
    }

    @Test
    void allInOneCluster() {
        def elements = [1, 2, 3, 4, 5]
        def v1 = new SLinkClusterer<Integer>().cluster(elements, 5.0, INT_DIST)
        def v2 = new SLinkClustererV2<Integer>().cluster(elements, 5.0, INT_DIST)
        assertEquals(1, v1.size())
        assertEquals(1, v2.size())
        assertEquals(elements.toSet(), v2[0].toSet())
    }

    @Test
    void allSingletons() {
        def elements = [1, 10, 20, 30]
        def v1 = new SLinkClusterer<Integer>().cluster(elements, 0.5, INT_DIST)
        def v2 = new SLinkClustererV2<Integer>().cluster(elements, 0.5, INT_DIST)
        assertEquals(4, v1.size())
        assertEquals(4, v2.size())
        assertEquals(toSetOfSets(v1), toSetOfSets(v2))
    }

    @Test
    void transitiveChaining() {
        // 1-3-5: each adjacent pair within dist 2, so all should merge via single-linkage
        def elements = [1, 3, 5]
        def v1 = new SLinkClusterer<Integer>().cluster(elements, 2.0, INT_DIST)
        def v2 = new SLinkClustererV2<Integer>().cluster(elements, 2.0, INT_DIST)
        assertEquals(1, v1.size())
        assertEquals(1, v2.size())
        assertEquals(toSetOfSets(v1), toSetOfSets(v2))
    }

    @Test
    void boundaryDistance() {
        // Elements exactly at minDist should be merged (dist <= minDist)
        def elements = [0, 5]
        def v1 = new SLinkClusterer<Integer>().cluster(elements, 5.0, INT_DIST)
        def v2 = new SLinkClustererV2<Integer>().cluster(elements, 5.0, INT_DIST)
        assertEquals(1, v1.size())
        assertEquals(1, v2.size())

        // Just below: should not merge
        def v1b = new SLinkClusterer<Integer>().cluster(elements, 4.9, INT_DIST)
        def v2b = new SLinkClustererV2<Integer>().cluster(elements, 4.9, INT_DIST)
        assertEquals(2, v1b.size())
        assertEquals(2, v2b.size())
    }

    // ==================== Random 3D data parity ====================

    static final Clusterer.Distance<double[]> SQR_EUCLID_3D = new Clusterer.Distance<double[]>() {
        @Override
        double dist(double[] a, double[] b) {
            double dx = a[0] - b[0]
            double dy = a[1] - b[1]
            double dz = a[2] - b[2]
            return dx * dx + dy * dy + dz * dz
        }
    }

    private static Set<Set<Integer>> indexSets(List<double[]> elements, List<List<double[]>> clusters) {
        Map<double[], Integer> indexMap = new IdentityHashMap<>()
        for (int i = 0; i < elements.size(); i++) {
            indexMap.put(elements[i], i)
        }
        return clusters.collect { List<double[]> c -> c.collect { indexMap.get(it) }.toSet() }.toSet()
    }

    @Test
    void parity_random3d_sparse() {
        parity_random3d(50, 0.0, 100.0, 5.0, 42L)
    }

    @Test
    void parity_random3d_dense() {
        parity_random3d(80, 0.0, 10.0, 25.0, 123L)
    }

    @Test
    void parity_random3d_medium() {
        parity_random3d(100, 0.0, 50.0, 15.0, 7L)
    }

    @Test
    void parity_random3d_varyingDistances() {
        Random rng = new Random(999L)
        List<double[]> points = generateRandom3dPoints(60, 0.0, 30.0, rng)

        for (double minDist : [1.0, 5.0, 10.0, 20.0, 50.0]) {
            double sqrDist = minDist * minDist
            def v1 = new SLinkClusterer<double[]>().cluster(points, sqrDist, SQR_EUCLID_3D)
            def v2 = new SLinkClustererV2<double[]>().cluster(points, sqrDist, SQR_EUCLID_3D)

            assertEquals(indexSets(points, v1), indexSets(points, v2),
                    "Mismatch for minDist=$minDist")
        }
    }

    @Test
    void parity_random3d_duplicateCoordinates() {
        List<double[]> points = []
        Random rng = new Random(55L)
        for (int i = 0; i < 5; i++) {
            points.add([5.0, 5.0, 5.0] as double[])
            points.add([20.0, 20.0, 20.0] as double[])
        }
        for (int i = 0; i < 5; i++) {
            points.add([rng.nextDouble() * 100, rng.nextDouble() * 100, rng.nextDouble() * 100] as double[])
        }
        Collections.shuffle(points, rng)

        double sqrDist = 1.0
        def v1 = new SLinkClusterer<double[]>().cluster(points, sqrDist, SQR_EUCLID_3D)
        def v2 = new SLinkClustererV2<double[]>().cluster(points, sqrDist, SQR_EUCLID_3D)

        assertEquals(indexSets(points, v1), indexSets(points, v2))
    }

    @Test
    void parity_random3d_largerSet() {
        parity_random3d(300, -50.0, 50.0, 8.0, 2026L)
    }

    private void parity_random3d(int n, double min, double max, double minDist, long seed) {
        Random rng = new Random(seed)
        List<double[]> points = generateRandom3dPoints(n, min, max, rng)

        double sqrDist = minDist * minDist
        def v1 = new SLinkClusterer<double[]>().cluster(points, sqrDist, SQR_EUCLID_3D)
        def v2 = new SLinkClustererV2<double[]>().cluster(points, sqrDist, SQR_EUCLID_3D)

        assertEquals(v1.size(), v2.size(),
                "Cluster count mismatch: n=$n minDist=$minDist seed=$seed")
        assertEquals(indexSets(points, v1), indexSets(points, v2),
                "Cluster memberships differ: n=$n minDist=$minDist seed=$seed")
    }

    private static List<double[]> generateRandom3dPoints(int n, double min, double max, Random rng) {
        double range = max - min
        List<double[]> points = new ArrayList<>(n)
        for (int i = 0; i < n; i++) {
            points.add([min + rng.nextDouble() * range, min + rng.nextDouble() * range, min + rng.nextDouble() * range] as double[])
        }
        return points
    }

    // ==================== Parity on real protein atoms ====================

    @Test
    void parity_proteinAtoms_2W83() {
        parity_proteinAtomClusters('distro/test_data/2W83.pdb', 3.0)
        parity_proteinAtomClusters('distro/test_data/2W83.pdb', 5.0)
    }

    @Test
    void parity_proteinAtoms_1fbl() {
        parity_proteinAtomClusters('distro/test_data/1fbl.pdb.gz', 3.0)
        parity_proteinAtomClusters('distro/test_data/1fbl.pdb.gz', 5.0)
    }

    /**
     * Compare V1 and V2 on a subset of real protein atoms.
     * Uses first N atoms to keep test fast (O(N²) is fine for small N).
     */
    void parity_proteinAtomClusters(String pdbFile, double minDist) {
        Protein p = Protein.load(pdbFile)
        // Take a subset to keep test duration reasonable
        int n = Math.min(200, p.proteinAtoms.count)
        List<Atom> atoms = p.proteinAtoms.list.subList(0, n)

        double sqrDist = minDist * minDist
        Clusterer.Distance<Atom> sqrEuclid = AtomClusterer.SQR_EUCLID

        def v1 = new SLinkClusterer<Atom>().cluster(atoms, sqrDist, sqrEuclid)
        def v2 = new SLinkClustererV2<Atom>().cluster(atoms, sqrDist, sqrEuclid)

        // Same number of clusters
        assertEquals(v1.size(), v2.size(),
                "Cluster count mismatch for $pdbFile minDist=$minDist")

        // Same cluster memberships (as sets of PDB serials, order-independent)
        Set<Set<Integer>> v1sets = v1.collect { List<Atom> c -> c.collect { it.PDBserial }.toSet() }.toSet()
        Set<Set<Integer>> v2sets = v2.collect { List<Atom> c -> c.collect { it.PDBserial }.toSet() }.toSet()

        assertEquals(v1sets, v2sets,
                "Cluster memberships differ for $pdbFile minDist=$minDist")
    }
}
