package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import cz.siret.prank.utils.PerfUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for AtomDeduplicator.sparsify (uniform-grid spatial de-duplication).
 *
 * The grid implementation is asserted to be EXACTLY equivalent (same kept atoms,
 * same order) to a brute-force greedy reference, on both random point clouds and
 * real protein atoms, plus the dedup invariant and edge cases.
 */
@CompileStatic
class AtomDeduplicatorTest {

    /** Brute-force O(N^2) greedy reference: keep a point iff every already-kept point is strictly farther than dist. */
    private static List<Atom> bruteForceSparsify(List<Atom> atoms, double dist) {
        double sqr = dist * dist
        List<Atom> kept = new ArrayList<>()
        for (Atom a : atoms) {
            boolean drop = false
            for (Atom b : kept) {
                double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ()
                if (dx * dx + dy * dy + dz * dz <= sqr) { drop = true; break }
            }
            if (!drop) kept.add(a)
        }
        return kept
    }

    private static Atoms randomAtoms(long seed, int n, double span) {
        Random rnd = new Random(seed)
        List<Atom> list = new ArrayList<>(n)
        for (int i = 0; i < n; i++) {
            list.add(new Point(
                    (rnd.nextDouble() - 0.5d) * span,
                    (rnd.nextDouble() - 0.5d) * span,
                    (rnd.nextDouble() - 0.5d) * span))
        }
        return new Atoms(list)
    }

    private static void assertSameKept(List<Atom> ref, List<Atom> got, String ctx) {
        assertEquals(ref.size(), got.size(), "kept count ($ctx)")
        for (int i = 0; i < ref.size(); i++) {
            assertSame(ref.get(i), got.get(i), "kept identity/order at index $i ($ctx)")
        }
    }

    @Test
    void exactGreedyEquivalence_random() {
        for (long seed : [1L, 42L, 12345L]) {
            for (double span : [5.0d, 20.0d, 80.0d]) {
                Atoms atoms = randomAtoms(seed, 3000, span)
                double dist = 1.5d
                List<Atom> got = AtomDeduplicator.sparsify(atoms, dist).list
                List<Atom> ref = bruteForceSparsify(atoms.list, dist)
                assertSameKept(ref, got, "seed=$seed span=$span")
            }
        }
    }

    @Test
    void exactGreedyEquivalence_realAtoms() {
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        for (double dist : [0.5d, 1.5d, 3.0d]) {
            List<Atom> got = AtomDeduplicator.sparsify(atoms, dist).list
            List<Atom> ref = bruteForceSparsify(atoms.list, dist)
            assertSameKept(ref, got, "dist=$dist")
        }
    }

    @Test
    void dedupInvariant() {
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        double dist = 1.5d
        List<Atom> kept = AtomDeduplicator.sparsify(atoms, dist).list

        // no two kept points are within dist of each other
        for (int i = 0; i < kept.size(); i++) {
            for (int j = i + 1; j < kept.size(); j++) {
                assertTrue(PerfUtils.dist(kept.get(i), kept.get(j)) > dist, "kept $i,$j too close")
            }
        }
        // every dropped point has at least one kept point within dist
        Set<Atom> accepted = new HashSet<>(kept)
        for (Atom a : atoms) {
            if (!accepted.contains(a)) {
                boolean near = false
                for (Atom b : kept) { if (PerfUtils.dist(a, b) <= dist) { near = true; break } }
                assertTrue(near, "dropped atom has no kept neighbor within $dist")
            }
        }
    }

    @Test
    void edgeCases() {
        assertEquals(0, AtomDeduplicator.sparsify(new Atoms(new ArrayList<Atom>()), 1.0d).count)
        Atoms one = new Atoms([new Point(0.0d, 0.0d, 0.0d)] as List<Atom>)
        assertEquals(1, AtomDeduplicator.sparsify(one, 1.0d).count)
    }
}
