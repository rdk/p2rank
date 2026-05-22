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
    void hisAliasesToHie() {
        // HIS is the default protonation state at physiological pH; we alias to HIE.
        // The lookup must work for both names.
        assertEquals(AmberCharges.get("HIE", "ND1"), AmberCharges.get("HIS", "ND1"), EPS)
        assertEquals(AmberCharges.get("HIE", "NE2"), AmberCharges.get("HIS", "NE2"), EPS)
    }
}
