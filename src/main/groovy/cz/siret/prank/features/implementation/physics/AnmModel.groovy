package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.ejml.data.DMatrixRMaj
import org.ejml.dense.row.factory.DecompositionFactory_DDRM
import org.ejml.interfaces.decomposition.EigenDecomposition_F64

/**
 * Per-protein Anisotropic Network Model precomputation shared by the three
 * ANM-derived residue features (anm_sensor, anm_effectiveness, anm_msf).
 *
 * Built once per Protein in preProcessProtein() and cached in
 * Protein.secondaryData[CACHE_KEY] so that enabling any subset of the three
 * features costs a single eigendecomposition.
 *
 * Anisotropic Network Model (ANM):
 *   Atilgan, A.R. et al. (2001). Anisotropy of Fluctuation Dynamics of Proteins
 *   with an Elastic Network Model. Biophys. J. 80(1), 505-515.
 *   https://doi.org/10.1016/S0006-3495(01)76033-X
 *
 * Perturbation Response Scanning (PRS):
 *   Atilgan, C. & Atilgan, A.R. (2009). Perturbation-Response Scanning Reveals
 *   Ligand Entry-Exit Mechanisms of Ferric Binding Protein. PLoS Comput. Biol.
 *   5(10), e1000544. https://doi.org/10.1371/journal.pcbi.1000544
 *
 * Adaptation in P2Rank: residues without a Cα atom (e.g. UNK or incomplete
 * model) are excluded from the network; their feature values default to 0.
 * Sensor/effectiveness are computed globally (no predefined active site).
 */
@Slf4j
@CompileStatic
class AnmModel {

    private static final String CACHE_KEY = "anm_model"

    /** Map: Residue.Key → index in the ANM (only residues with a Cα are present). */
    final Map<Residue.Key, Integer> indexByKey

    /** PRS column average — residue receives perturbations from all others. */
    final double[] sensor

    /** PRS row average — residue's perturbation propagates to all others. */
    final double[] effectiveness

    /** Mean-square fluctuation from the ANM (Σ over kept modes and x/y/z). */
    final double[] msf

    private AnmModel(Map<Residue.Key, Integer> indexByKey,
                     double[] sensor, double[] effectiveness, double[] msf) {
        this.indexByKey = indexByKey
        this.sensor = sensor
        this.effectiveness = effectiveness
        this.msf = msf
    }

    /**
     * Look up the per-residue value, returning 0.0 for residues missing from
     * the network (e.g. no Cα atom).
     */
    double sensorFor(Residue r)        { Integer i = indexByKey.get(r.key); i == null ? 0d : sensor[i] }
    double effectivenessFor(Residue r) { Integer i = indexByKey.get(r.key); i == null ? 0d : effectiveness[i] }
    double msfFor(Residue r)           { Integer i = indexByKey.get(r.key); i == null ? 0d : msf[i] }

    /**
     * Returns the cached AnmModel for this protein, computing it on first access.
     */
    static AnmModel getOrCompute(Protein protein, Params params) {
        (AnmModel) protein.secondaryData.computeIfAbsent(CACHE_KEY, { k -> compute(protein, params) })
    }

    //---------------------------------------------------------------------//
    //  Build pipeline
    //---------------------------------------------------------------------//

    private static AnmModel compute(Protein protein, Params params) {
        // 1. collect (residue, Cα) pairs in residue-list order
        List<Residue> residues = protein.residues.list
        List<Residue> withCa = new ArrayList<>(residues.size())
        List<double[]> coords = new ArrayList<>(residues.size())
        int skipped = 0
        for (Residue r : residues) {
            Atom ca = r.aminoAcid?.getCA()
            if (ca == null) { skipped++; continue }
            withCa.add(r)
            coords.add([ca.x, ca.y, ca.z] as double[])
        }
        if (skipped > 0) {
            log.warn "ANM: {} of {} residues lack a Cα atom and are excluded from the network", skipped, residues.size()
        }

        long t0 = System.currentTimeMillis()
        Result r = computeFromCoords(coords,
                params.feat_anm_cutoff, params.feat_anm_gamma,
                params.feat_anm_n_modes, params.feat_anm_zero_mode_threshold,
                protein.name)
        log.debug "ANM: N={} kept={} modes, computed in {} ms (protein {})",
                coords.size(), r.keptModes, System.currentTimeMillis() - t0, protein.name

        Map<Residue.Key, Integer> indexByKey = new HashMap<>(withCa.size())
        for (int i = 0; i < withCa.size(); i++) indexByKey.put(withCa.get(i).key, i)
        return new AnmModel(indexByKey, r.sensor, r.effectiveness, r.msf)
    }

    /**
     * Bundle of per-residue ANM outputs plus the actual mode count used.
     * Exposed for unit tests that build a Hessian from synthetic coordinates.
     */
    static class Result {
        double[] sensor
        double[] effectiveness
        double[] msf
        int keptModes
    }

    /**
     * Full ANM pipeline from raw Cα coordinates: build Hessian, eigendecompose,
     * extract sensor / effectiveness / MSF. Caller is responsible for the
     * residue↔index mapping. Used by both the production path and tests.
     *
     * @param logTag short identifier (e.g. protein name) for warning messages
     */
    static Result computeFromCoords(List<double[]> coords,
                                     double cutoff, double gamma,
                                     int requestedModes, double zeroThr,
                                     String logTag) {
        int n = coords.size()
        Result out = new Result()
        if (n < 4) {
            log.warn "ANM: only {} Cα atoms — too few for a meaningful ANM (logTag={})", n, logTag
            out.sensor = new double[n]
            out.effectiveness = new double[n]
            out.msf = new double[n]
            out.keptModes = 0
            return out
        }

        DMatrixRMaj H = buildHessian(coords, cutoff, gamma)

        EigenDecomposition_F64<DMatrixRMaj> eig =
                DecompositionFactory_DDRM.eig(3 * n, true, true)
        if (!eig.decompose(H)) {
            log.warn "ANM: eigendecomposition failed (logTag={}) — returning zero features", logTag
            out.sensor = new double[n]
            out.effectiveness = new double[n]
            out.msf = new double[n]
            out.keptModes = 0
            return out
        }

        int total = eig.getNumberOfEigenvalues()
        double[] eigvals = new double[total]
        DMatrixRMaj[] eigvecs = new DMatrixRMaj[total]
        Integer[] order = new Integer[total]
        for (int k = 0; k < total; k++) {
            eigvals[k] = eig.getEigenvalue(k).getReal()
            eigvecs[k] = eig.getEigenVector(k)
            order[k] = k
        }
        Arrays.sort(order, { Integer a, Integer b -> Double.compare(eigvals[a], eigvals[b]) } as Comparator<Integer>)

        int firstNonZero = 0
        while (firstNonZero < total && Math.abs(eigvals[order[firstNonZero]]) < zeroThr) firstNonZero++
        if (firstNonZero != 6) {
            log.warn "ANM: expected 6 zero modes, found {} below threshold {} (logTag={})", firstNonZero, zeroThr, logTag
        }
        int kept = Math.min(requestedModes, total - firstNonZero)
        if (kept < requestedModes) {
            log.warn "ANM: requested {} modes but only {} non-zero modes available (logTag={})",
                    requestedModes, kept, logTag
        }
        if (kept <= 0) {
            out.sensor = new double[n]
            out.effectiveness = new double[n]
            out.msf = new double[n]
            out.keptModes = 0
            return out
        }

        // residueModes[j][a][k] = u_k[3j+a] / sqrt(λ_k)
        double[][][] residueModes = new double[n][3][kept]
        for (int k = 0; k < kept; k++) {
            int origIdx = order[firstNonZero + k]
            double lambda = eigvals[origIdx]
            double invSqrt = 1d / Math.sqrt(lambda)
            DMatrixRMaj v = eigvecs[origIdx]
            for (int j = 0; j < n; j++) {
                for (int a = 0; a < 3; a++) {
                    residueModes[j][a][k] = v.unsafe_get(3 * j + a, 0) * invSqrt
                }
            }
        }

        // PRS: sensor = col avg, effectiveness = row avg of PRS_ij.
        // Computed on-the-fly without materializing the N×N matrix.
        double[] sensor = new double[n]
        double[] effectiveness = new double[n]
        double oneThird = 1d / 3d
        double invN = 1d / n
        for (int i = 0; i < n; i++) {
            double rowSum = 0d
            for (int j = 0; j < n; j++) {
                double sumSq = 0d
                for (int a = 0; a < 3; a++) {
                    double[] mj = residueModes[j][a]
                    for (int b = 0; b < 3; b++) {
                        double[] mi = residueModes[i][b]
                        double dot = 0d
                        for (int kk = 0; kk < kept; kk++) dot += mj[kk] * mi[kk]
                        sumSq += dot * dot
                    }
                }
                double prsVal = Math.sqrt(oneThird * sumSq)
                rowSum += prsVal
                sensor[j] += prsVal
            }
            effectiveness[i] = rowSum * invN
        }
        for (int j = 0; j < n; j++) sensor[j] *= invN

        // MSF = Σ_a Σ_k residueModes[j][a][k]² = Σ_a Σ_k u_k[3j+a]²/λ_k
        double[] msf = new double[n]
        for (int j = 0; j < n; j++) {
            double s = 0d
            for (int a = 0; a < 3; a++) {
                double[] mj = residueModes[j][a]
                for (int kk = 0; kk < kept; kk++) s += mj[kk] * mj[kk]
            }
            msf[j] = s
        }

        out.sensor = sensor
        out.effectiveness = effectiveness
        out.msf = msf
        out.keptModes = kept
        return out
    }

    /**
     * Builds the 3N×3N ANM Hessian. For each pair (i<j) within `cutoff`, the
     * 3×3 off-diagonal block is H_ij = −γ·(r_ij ⊗ r_ij)/|r_ij|² and the same
     * is subtracted from the diagonal blocks H_ii and H_jj.
     */
    static DMatrixRMaj buildHessian(List<double[]> coords, double cutoff, double gamma) {
        int n = coords.size()
        int dim = 3 * n
        DMatrixRMaj H = new DMatrixRMaj(dim, dim)
        double cutoffSq = cutoff * cutoff

        for (int i = 0; i < n; i++) {
            double[] ri = coords.get(i)
            for (int j = i + 1; j < n; j++) {
                double[] rj = coords.get(j)
                double dx = rj[0] - ri[0]
                double dy = rj[1] - ri[1]
                double dz = rj[2] - ri[2]
                double d2 = dx*dx + dy*dy + dz*dz
                if (d2 > cutoffSq || d2 == 0d) continue

                double s = gamma / d2
                // 3×3 block contributions
                double[][] blk = [
                        [-s*dx*dx, -s*dx*dy, -s*dx*dz],
                        [-s*dy*dx, -s*dy*dy, -s*dy*dz],
                        [-s*dz*dx, -s*dz*dy, -s*dz*dz]
                ] as double[][]
                int ii = 3 * i, jj = 3 * j
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        double v = blk[a][b]
                        // off-diagonal blocks H_ij and H_ji
                        H.add(ii + a, jj + b, v)
                        H.add(jj + a, ii + b, v)
                        // diagonal blocks accumulate -H_ij (= +s*da*db)
                        H.add(ii + a, ii + b, -v)
                        H.add(jj + a, jj + b, -v)
                    }
                }
            }
        }
        return H
    }

}
