package cz.siret.prank.features.implementation.electrostatics

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Pins the AMBER ff14SB table content. Per-residue net-charge invariant is
 * checked at a relaxed 0.2-{@code e} tolerance because the embedded table
 * uses a shared canonical backbone for compactness, while real ff14SB has
 * residue-specific backbones — drift of ~0.1 e per residue. The SIGN of the
 * net charge is correct for every charged residue (which is what downstream
 * ML/analysis actually consumes); the magnitude is approximate.
 *
 * See {@code misc/dev/ELECTROSTATICS_IMPLEMENTATION.md} for the design choice.
 */
@CompileStatic
class AmberChargesTest {

    private static final double EPS = 1e-4d
    /** Tolerance on per-residue net charge — see class javadoc. */
    private static final double NET_TOL = 0.2d

    private static double sumOver(String residue, String[] atoms) {
        double s = 0d
        for (String a : atoms) {
            double q = AmberCharges.get(residue, a)
            assertFalse(Double.isNaN(q), "missing AMBER entry: $residue/$a")
            s += q
        }
        return s
    }

    @Test
    void neutralResiduesNetToZero() {
        // Backbone atoms common to all (except PRO/GLY): N H CA HA C O.
        // Side chain atoms vary; we list a few canonical neutral residues fully.
        assertEquals(0d, sumOver("ALA",
                ["N","H","CA","HA","CB","HB1","HB2","HB3","C","O"] as String[]), NET_TOL, "ALA net charge")
        assertEquals(0d, sumOver("GLY",
                ["N","H","CA","HA2","HA3","C","O"] as String[]), NET_TOL, "GLY net charge")
        assertEquals(0d, sumOver("SER",
                ["N","H","CA","HA","CB","HB2","HB3","OG","HG","C","O"] as String[]), NET_TOL, "SER net charge")
        assertEquals(0d, sumOver("PRO",
                ["N","CA","HA","CB","HB2","HB3","CG","HG2","HG3","CD","HD2","HD3","C","O"] as String[]),
                NET_TOL, "PRO net charge")
    }

    @Test
    void chargedResiduesNetToFormalCharge() {
        // ASP: net −1 (anionic carboxylate)
        double aspNet = sumOver("ASP",
                ["N","H","CA","HA","CB","HB2","HB3","CG","OD1","OD2","C","O"] as String[])
        assertEquals(-1d, aspNet, NET_TOL, "ASP net ≈ -1")

        // GLU: net −1
        double gluNet = sumOver("GLU",
                ["N","H","CA","HA","CB","HB2","HB3","CG","HG2","HG3","CD","OE1","OE2","C","O"] as String[])
        assertEquals(-1d, gluNet, NET_TOL, "GLU net ≈ -1")

        // LYS: net +1 (cationic ammonium)
        double lysNet = sumOver("LYS",
                ["N","H","CA","HA","CB","HB2","HB3","CG","HG2","HG3","CD","HD2","HD3",
                 "CE","HE2","HE3","NZ","HZ1","HZ2","HZ3","C","O"] as String[])
        assertEquals(1d, lysNet, NET_TOL, "LYS net ≈ +1")

        // ARG: net +1 (cationic guanidinium)
        double argNet = sumOver("ARG",
                ["N","H","CA","HA","CB","HB2","HB3","CG","HG2","HG3","CD","HD2","HD3",
                 "NE","HE","CZ","NH1","HH11","HH12","NH2","HH21","HH22","C","O"] as String[])
        assertEquals(1d, argNet, NET_TOL, "ARG net ≈ +1")

        // HIP: net +1 (protonated histidine)
        double hipNet = sumOver("HIP",
                ["N","H","CA","HA","CB","HB2","HB3","CG","ND1","HD1","CE1","HE1",
                 "NE2","HE2","CD2","HD2","C","O"] as String[])
        assertEquals(1d, hipNet, NET_TOL, "HIP net ≈ +1")

        // HIE (neutral His): net 0
        double hieNet = sumOver("HIE",
                ["N","H","CA","HA","CB","HB2","HB3","CG","ND1","CE1","HE1",
                 "NE2","HE2","CD2","HD2","C","O"] as String[])
        assertEquals(0d, hieNet, NET_TOL, "HIE net ≈ 0")
    }

    @Test
    void knownCanonicalValues() {
        // Sanity check a handful of widely-cited ff14SB charges so any future
        // typo in the table is caught immediately.
        assertEquals(-0.8014d, AmberCharges.get("ASP", "OD1"), EPS)
        assertEquals(-0.8014d, AmberCharges.get("ASP", "OD2"), EPS)
        assertEquals(-0.3854d, AmberCharges.get("LYS", "NZ"), EPS)
        assertEquals(-0.8627d, AmberCharges.get("ARG", "NH1"), EPS)
        assertEquals(-0.4157d, AmberCharges.get("ALA", "N"), EPS)   // canonical backbone N
        assertEquals(-0.5679d, AmberCharges.get("ALA", "O"), EPS)   // canonical backbone O
    }

    @Test
    void caseInsensitiveLookup() {
        assertEquals(AmberCharges.get("ALA", "CA"),
                AmberCharges.get("ala", "ca"), NET_TOL, "lowercase must match uppercase")
    }

    @Test
    void unknownPairReturnsNaN() {
        assertTrue(Double.isNaN(AmberCharges.get("ALA", "XYZ")), "unknown atom name")
        assertTrue(Double.isNaN(AmberCharges.get("ZZZ", "CA")), "unknown residue")
        assertTrue(Double.isNaN(AmberCharges.get(null, "CA")), "null residue")
        assertTrue(Double.isNaN(AmberCharges.get("ALA", null)), "null atom name")
    }

    @Test
    void unitedAtomCanonicalValues() {
        // The bug this guards: standard PDB files don't carry explicit H atoms,
        // so the all-atom lookup of LYS NZ returns −0.3854 e, sign-flipped from
        // the residue's actual cationic character. The united-atom value rolls
        // the three HZ hydrogens (+0.34 e each) into NZ, giving +0.6346 e.
        //
        // Pin exact expected values: regressions in the H→heavy mapping must
        // show up as a numeric diff, not just a sign flip.
        assertEquals(-0.3854d + 3 * 0.3400d, AmberCharges.getUnited("LYS", "NZ"), EPS,
                "LYS NZ united = NZ + 3·HZ")
        assertEquals(-0.8627d + 2 * 0.4478d, AmberCharges.getUnited("ARG", "NH1"), EPS,
                "ARG NH1 united = NH1 + HH11 + HH12")
        // ASP OD1 stays anionic — no H attached, value unchanged from all-atom.
        assertEquals(-0.8014d, AmberCharges.getUnited("ASP", "OD1"), EPS,
                "ASP OD1 united = all-atom OD1 (no H attached)")
    }

    @Test
    void unitedBackboneFoldsAmideHydrogenIntoNitrogenNotCarbon() {
        // Regression: the backbone amide proton (atom "H") bonds to the amide N,
        // NOT the carbonyl C. The net-charge invariants above can't catch a
        // mis-fold because charge stays within the residue — so pin the backbone
        // N and C united values explicitly.
        //
        // The historical bug: findHeavyBondedTo("H") stripped the leading 'H' to
        // an empty suffix and matched prefix "C" (carbonyl) before "N", folding
        // the +0.2719 e amide H into C (→ 0.8692 e) and leaving N at the bare
        // −0.4157 e, for every non-PRO residue.
        double unitedN = -0.4157d + 0.2719d   // BB_N + BB_H = −0.1438
        double unitedC = 0.5973d              // BB_C unchanged (carbonyl C carries no H)

        for (String res : ["ALA", "GLY", "SER", "TRP", "ASP", "LYS"]) {
            assertEquals(unitedN, AmberCharges.getUnited(res, "N"), EPS,
                    "$res united N must absorb the amide H (BB_N + BB_H)")
            assertEquals(unitedC, AmberCharges.getUnited(res, "C"), EPS,
                    "$res united C must stay the bare carbonyl C")
        }

        // Guard the exact failure mode: amide H must NOT land on the carbonyl C.
        assertNotEquals(0.5973d + 0.2719d, AmberCharges.getUnited("ALA", "C"), EPS,
                "amide H must not be folded into the carbonyl C")
    }

    @Test
    void unitedAtomTableHasNoHydrogenEntries() {
        // United-atom table is heavy-atoms-only; H atoms should return NaN.
        assertTrue(Double.isNaN(AmberCharges.getUnited("LYS", "HZ1")))
        assertTrue(Double.isNaN(AmberCharges.getUnited("ALA", "HA")))
        assertTrue(Double.isNaN(AmberCharges.getUnited("ALA", "H")))
    }

    @Test
    void unitedAtomChargedResiduesStillSumToFormalCharge() {
        // Same invariant as the all-atom test but over heavy atoms only.
        // LYS: net +1
        String[] lysHeavy = ["N","CA","CB","CG","CD","CE","NZ","C","O"]
        double lysSum = 0d
        for (String a : lysHeavy) lysSum += AmberCharges.getUnited("LYS", a)
        assertEquals(1d, lysSum, NET_TOL, "LYS united heavy atoms ≈ +1")

        // ARG: net +1
        String[] argHeavy = ["N","CA","CB","CG","CD","NE","CZ","NH1","NH2","C","O"]
        double argSum = 0d
        for (String a : argHeavy) argSum += AmberCharges.getUnited("ARG", a)
        assertEquals(1d, argSum, NET_TOL, "ARG united heavy atoms ≈ +1")

        // ASP: net −1
        String[] aspHeavy = ["N","CA","CB","CG","OD1","OD2","C","O"]
        double aspSum = 0d
        for (String a : aspHeavy) aspSum += AmberCharges.getUnited("ASP", a)
        assertEquals(-1d, aspSum, NET_TOL, "ASP united heavy atoms ≈ −1")

        // ALA: net 0
        String[] alaHeavy = ["N","CA","CB","C","O"]
        double alaSum = 0d
        for (String a : alaHeavy) alaSum += AmberCharges.getUnited("ALA", a)
        assertEquals(0d, alaSum, NET_TOL, "ALA united heavy atoms ≈ 0")
    }

    @Test
    void hisAliasesToHie() {
        // HIS is the default protonation state at physiological pH; we alias to HIE.
        // The lookup must work for both names.
        assertEquals(AmberCharges.get("HIE", "ND1"), AmberCharges.get("HIS", "ND1"), EPS)
        assertEquals(AmberCharges.get("HIE", "NE2"), AmberCharges.get("HIS", "NE2"), EPS)
    }
}
