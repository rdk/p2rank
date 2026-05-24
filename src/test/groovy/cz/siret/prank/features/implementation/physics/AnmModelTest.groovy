package cz.siret.prank.features.implementation.physics

import org.ejml.data.DMatrixRMaj
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for the AnmModel pipeline. We bypass Protein construction and
 * feed synthetic Cα coordinates directly into computeFromCoords / buildHessian.
 *
 * The 4-residue test system is a tetrahedron-ish arrangement (not collinear,
 * so the rotation modes are well-defined). With a 10 Å cutoff every pair is
 * connected, producing exactly 6 zero modes and 6 vibrational modes.
 */
class AnmModelTest {

    /** Tetrahedron-ish arrangement of 4 Cα-equivalent points. */
    private static List<double[]> tetraCoords() {
        return [
                [0d, 0d, 0d] as double[],
                [3.8d, 0d, 0d] as double[],
                [1.9d, 3.3d, 0d] as double[],
                [1.9d, 1.1d, 3.1d] as double[]
        ] as List<double[]>
    }

    @Test
    void hessianIsSymmetric() {
        DMatrixRMaj H = AnmModel.buildHessian(tetraCoords(), 10.0d, 1.0d)
        int dim = H.numRows
        assertEquals(12, dim)
        assertEquals(dim, H.numCols)
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                assertEquals(H.get(i, j), H.get(j, i), 1e-12, "H[$i,$j] != H[$j,$i]")
            }
        }
    }

    @Test
    void hessianRowsSumToZero() {
        // Each row of the ANM Hessian sums to zero (translational invariance).
        DMatrixRMaj H = AnmModel.buildHessian(tetraCoords(), 10.0d, 1.0d)
        int dim = H.numRows
        for (int i = 0; i < dim; i++) {
            double s = 0d
            for (int j = 0; j < dim; j++) s += H.get(i, j)
            assertEquals(0d, s, 1e-10, "row $i sums to $s, expected 0")
        }
    }

    @Test
    void noEdgesWhenCutoffTooSmall() {
        // With a sub-Ångström cutoff no pair is connected → Hessian is all zeros.
        DMatrixRMaj H = AnmModel.buildHessian(tetraCoords(), 0.5d, 1.0d)
        for (int i = 0; i < H.numRows; i++) {
            for (int j = 0; j < H.numCols; j++) {
                assertEquals(0d, H.get(i, j), 1e-12)
            }
        }
    }

    @Test
    void sixZeroModesOnFullyConnectedSystem() {
        // 4-residue fully connected (cutoff 10 Å covers every pair) → exactly
        // 6 zero modes (3 translation + 3 rotation), 6 non-zero modes.
        AnmModel.Result r = AnmModel.computeFromCoords(
                tetraCoords(), 10.0d, 1.0d, /*requestedModes*/ 6, /*zeroThr*/ 1e-6, "test")
        assertEquals(6, r.keptModes, "should keep all 6 non-trivial modes")
    }

    @Test
    void msfIsNonNegative() {
        AnmModel.Result r = AnmModel.computeFromCoords(
                tetraCoords(), 10.0d, 1.0d, 6, 1e-6, "test")
        for (int i = 0; i < r.msf.length; i++) {
            assertTrue(r.msf[i] > 0d, "MSF[$i] = ${r.msf[i]} should be strictly positive")
            assertTrue(Double.isFinite(r.msf[i]), "MSF[$i] non-finite")
        }
    }

    @Test
    void prsSensorAndEffectivenessAreNonNegative() {
        AnmModel.Result r = AnmModel.computeFromCoords(
                tetraCoords(), 10.0d, 1.0d, 6, 1e-6, "test")
        for (int i = 0; i < r.sensor.length; i++) {
            assertTrue(r.sensor[i] >= 0d, "sensor[$i] = ${r.sensor[i]} negative")
            assertTrue(r.effectiveness[i] >= 0d, "effectiveness[$i] = ${r.effectiveness[i]} negative")
            assertTrue(Double.isFinite(r.sensor[i]))
            assertTrue(Double.isFinite(r.effectiveness[i]))
        }
    }

    @Test
    void prsSymmetricForUniformSpringSystem() {
        // For a system with uniform γ and a symmetric Hessian, the PRS magnitudes
        // averaged over isotropic random forces are symmetric: PRS_ij == PRS_ji.
        // Hence sensor and effectiveness vectors should coincide (column avg ==
        // row avg of a symmetric matrix).
        AnmModel.Result r = AnmModel.computeFromCoords(
                tetraCoords(), 10.0d, 1.0d, 6, 1e-6, "test")
        for (int i = 0; i < r.sensor.length; i++) {
            assertEquals(r.effectiveness[i], r.sensor[i], 1e-9,
                    "sensor[$i] != effectiveness[$i] for symmetric PRS")
        }
    }

    @Test
    void tooFewResiduesReturnsZeros() {
        AnmModel.Result r = AnmModel.computeFromCoords(
                [[0d, 0d, 0d] as double[], [1d, 0d, 0d] as double[]] as List<double[]>,
                10.0d, 1.0d, 20, 1e-6, "tiny")
        assertEquals(0, r.keptModes)
        assertEquals(2, r.msf.length)
        assertEquals(0d, r.msf[0])
        assertEquals(0d, r.msf[1])
    }
}
