package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.kdtree.v1.AtomKdTreeV1
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.PerfUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for Atoms.countWithinSphere and the underlying count-only k-d-tree query.
 *
 * countWithinSphere is the allocation-free replacement for cutoutSphere(...).count used
 * by the protrusion features and PocketPredictor. This verifies it returns the SAME count
 * as cutoutSphere on every path: serial branch, KD branch, the top-level threshold switch
 * (both below and above use_kdtree_cutout_sphere_thrashold), and the v1 fallback.
 */
@CompileStatic
class AtomsCountWithinSphereTest {

    private static int bruteCount(Atoms atoms, Atom c, double r) {
        double sqr = r * r
        int n = 0
        for (Atom a : atoms) {
            if (PerfUtils.sqrDist(a, c) <= sqr) n++
        }
        return n
    }

    @Test
    void countWithinSphere_matchesCutoutSphere_bothBranches() {
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        assertTrue(atoms.count >= Params.INSTANCE.use_kdtree_cutout_sphere_thrashold,
                "2W83 should exceed the KD threshold so the top-level call uses the KD path")

        List<Atom> centers = new ArrayList<>()
        for (int i = 0; i < atoms.count; i += 20) centers.add(atoms.list.get(i))   // ~200 sampled centers

        for (double r : [2.0d, 6.0d, 10.0d]) {
            for (Atom c : centers) {
                int brute = bruteCount(atoms, c, r)
                // exact API equivalence on each path
                assertEquals(atoms.cutoutSphereSerial(c, r).count, atoms.countWithinSphereSerial(c, r), "serial @r=$r")
                assertEquals(atoms.cutoutSphereKD(c, r).count,     atoms.countWithinSphereKD(c, r),     "KD @r=$r")
                assertEquals(atoms.cutoutSphere(c, r).count,       atoms.countWithinSphere(c, r),       "top-level @r=$r")
                // ground truth (existing findWithinRadius_parity proves KD==serial on real data)
                assertEquals(brute, atoms.countWithinSphere(c, r), "vs brute force @r=$r")
            }
        }
    }

    @Test
    void countWithinSphere_smallSet_usesSerialBranch() {
        // A set below the threshold makes the top-level countWithinSphere take the serial branch.
        Random rnd = new Random(11)
        int n = Math.min(30, Params.INSTANCE.use_kdtree_cutout_sphere_thrashold - 1)
        List<Atom> list = new ArrayList<>(n)
        for (int i = 0; i < n; i++) {
            list.add(new Point((rnd.nextDouble() - 0.5d) * 20d, (rnd.nextDouble() - 0.5d) * 20d, (rnd.nextDouble() - 0.5d) * 20d))
        }
        Atoms atoms = new Atoms(list)
        assertTrue(atoms.count < Params.INSTANCE.use_kdtree_cutout_sphere_thrashold)

        for (double r : [3.0d, 8.0d]) {
            for (Atom c : list) {
                assertEquals(atoms.cutoutSphere(c, r).count, atoms.countWithinSphere(c, r), "small-set top-level @r=$r")
                assertEquals(bruteCount(atoms, c, r), atoms.countWithinSphere(c, r), "small-set vs brute @r=$r")
            }
        }
    }

    @Test
    void v1FallbackCount_matchesFindCount() {
        // The AtomKdTree interface default (used by the opt-in v1 impl) builds the set and counts it.
        Protein p = Protein.load('distro/test_data/2W83.pdb')
        Atoms atoms = p.proteinAtoms
        AtomKdTreeV1 v1 = AtomKdTreeV1.build(atoms)
        for (double r : [2.0d, 6.0d]) {
            for (int i = 0; i < atoms.count; i += 50) {
                Atom c = atoms.list.get(i)
                assertEquals(v1.findAtomsWithinRadius(c, r, false).count, v1.countAtomsWithinRadius(c, r), "v1 fallback @r=$r")
                assertEquals(bruteCount(atoms, c, r), v1.countAtomsWithinRadius(c, r), "v1 vs brute @r=$r")
            }
        }
    }
}
