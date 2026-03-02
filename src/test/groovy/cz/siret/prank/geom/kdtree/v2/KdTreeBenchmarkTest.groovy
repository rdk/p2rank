package cz.siret.prank.geom.kdtree.v2

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.geom.kdtree.v1.AtomKdTreeV1
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*
import static org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Comparative correctness and performance benchmark for v1 vs v2 KdTree.
 *
 * Generates random point sets, builds both trees, verifies identical results
 * for all query types, and measures relative performance.
 *
 * Skipped during normal test runs. Run via:
 *   ./misc/test-scripts/kdtree-benchmark.sh [OPTIONS]
 */
@CompileStatic
class KdTreeBenchmarkTest {

    @BeforeAll
    static void checkEnabled() {
        assumeTrue("true" == System.getProperty("kdtree.benchmark"),
                "Skipped: set -Dkdtree.benchmark=true to run")
    }

    // Configuration from system properties (with defaults)
    static final int POINT_COUNT = Integer.getInteger("kdtree.points", 5000)
    static final int QUERY_COUNT = Integer.getInteger("kdtree.queries", 500)
    static final int ITERATIONS = Integer.getInteger("kdtree.iterations", 5)
    static final long BASE_SEED = Long.getLong("kdtree.seed", 42L)
    static final double[] RADII = parseRadii(System.getProperty("kdtree.radii", "2.0,6.0,10.0"))
    static final int[] KNN_KS = [1, 5, 9, 20] as int[]

    // Timing accumulators (indexed by iteration, excluding warmup)
    // Operations: build, findNearest, radius×N, knn
    static final int OP_BUILD = 0
    static final int OP_NEAREST = 1
    // OP_RADIUS_BASE + i for each radius
    // OP_KNN after radii

    @Test
    void compareV1vsV2() {
        int radiusCount = RADII.length
        int opCount = 2 + radiusCount + 1 // build, nearest, radii..., knn(k=9)
        int OP_KNN = 2 + radiusCount

        // Timing arrays: [operation][iteration] in nanoseconds
        long[][] v1Times = new long[opCount][ITERATIONS]
        long[][] v2Times = new long[opCount][ITERATIONS]
        long totalChecks = 0

        println "=== KdTree v1 vs v2 Benchmark ==="
        println "Points: $POINT_COUNT  Queries: $QUERY_COUNT  Iterations: $ITERATIONS  Base seed: $BASE_SEED"
        println "Radii: ${RADII.collect { String.format('%.1f', it) }.join(', ')}"
        println ""

        for (int iter = 0; iter < ITERATIONS; iter++) {
            long seed = BASE_SEED + iter
            Random rng = new Random(seed)

            // Generate random data points and query points
            List<Point> dataPoints = generatePoints(rng, POINT_COUNT)
            List<Point> queryPoints = generatePoints(rng, QUERY_COUNT)
            // Also include some data points as queries (tests self-match behavior)
            for (int i = 0; i < Math.min(50, dataPoints.size()); i++) {
                queryPoints.add(dataPoints.get(i))
            }
            Atoms atoms = new Atoms(dataPoints as List<Atom>)

            // --- Build both trees ---
            long t0, t1

            t0 = System.nanoTime()
            AtomKdTreeV1 v1Tree = AtomKdTreeV1.build(atoms)
            t1 = System.nanoTime()
            v1Times[OP_BUILD][iter] = t1 - t0

            t0 = System.nanoTime()
            AtomKdTreeV2 v2Tree = AtomKdTreeV2.build(atoms)
            t1 = System.nanoTime()
            v2Times[OP_BUILD][iter] = t1 - t0

            // --- findNearest correctness + timing ---
            int checks = 0

            t0 = System.nanoTime()
            for (Point q : queryPoints) {
                v1Tree.findNearest(q)
            }
            t1 = System.nanoTime()
            v1Times[OP_NEAREST][iter] = t1 - t0

            t0 = System.nanoTime()
            for (Point q : queryPoints) {
                v2Tree.findNearest(q)
            }
            t1 = System.nanoTime()
            v2Times[OP_NEAREST][iter] = t1 - t0

            // Correctness check (separate pass to not pollute timing)
            for (Point q : queryPoints) {
                double v1dist = v1Tree.nearestSqrDist(q)
                double v2dist = v2Tree.nearestSqrDist(q)
                assertEquals(v1dist, v2dist, 1e-10,
                        "nearestSqrDist mismatch, iter=$iter, seed=$seed, q=(${q.getX()},${q.getY()},${q.getZ()})")
                checks++
            }

            // --- findAtomsWithinRadius correctness + timing ---
            for (int ri = 0; ri < radiusCount; ri++) {
                double radius = RADII[ri]
                int opIdx = 2 + ri

                t0 = System.nanoTime()
                for (Point q : queryPoints) {
                    v1Tree.findAtomsWithinRadius(q, radius, false)
                }
                t1 = System.nanoTime()
                v1Times[opIdx][iter] = t1 - t0

                t0 = System.nanoTime()
                for (Point q : queryPoints) {
                    v2Tree.findAtomsWithinRadius(q, radius, false)
                }
                t1 = System.nanoTime()
                v2Times[opIdx][iter] = t1 - t0

                // Correctness
                for (Point q : queryPoints) {
                    Set<Atom> v1set = v1Tree.findAtomsWithinRadius(q, radius, false).list.toSet()
                    Set<Atom> v2set = v2Tree.findAtomsWithinRadius(q, radius, false).list.toSet()
                    assertEquals(v1set, v2set,
                            "radius=$radius mismatch, iter=$iter, seed=$seed, q=(${q.getX()},${q.getY()},${q.getZ()})")
                    checks++
                }
            }

            // --- findNearestNAtoms correctness + timing (k=9 for timing) ---
            t0 = System.nanoTime()
            for (Point q : queryPoints) {
                v1Tree.findNearestNAtoms(q, 9, false)
            }
            t1 = System.nanoTime()
            v1Times[OP_KNN][iter] = t1 - t0

            t0 = System.nanoTime()
            for (Point q : queryPoints) {
                v2Tree.findNearestNAtoms(q, 9, false)
            }
            t1 = System.nanoTime()
            v2Times[OP_KNN][iter] = t1 - t0

            // Correctness for all k values
            for (int k : KNN_KS) {
                for (Point q : queryPoints) {
                    Set<Atom> v1set = v1Tree.findNearestNAtoms(q, k, false).list.toSet()
                    Set<Atom> v2set = v2Tree.findNearestNAtoms(q, k, false).list.toSet()
                    assertEquals(v1set, v2set,
                            "kNN k=$k mismatch, iter=$iter, seed=$seed, q=(${q.getX()},${q.getY()},${q.getZ()})")
                    checks++
                }
            }

            totalChecks += checks
            println "  Iteration ${iter + 1}/$ITERATIONS (seed=$seed): $checks checks passed"
        }

        // --- Generate report ---
        println ""
        String report = generateReport(v1Times, v2Times, RADII, totalChecks)
        println report

        // Write to file if local/ directory exists
        File reportFile = new File("local/kdtree-benchmark-report.txt")
        if (reportFile.parentFile.exists()) {
            reportFile.text = report
            println "(Report written to local/kdtree-benchmark-report.txt)"
        }
    }

    // ==================== Helpers ====================

    private static List<Point> generatePoints(Random rng, int count) {
        List<Point> points = new ArrayList<>(count)
        for (int i = 0; i < count; i++) {
            double x = rng.nextDouble() * 100.0 - 50.0  // [-50, 50]
            double y = rng.nextDouble() * 100.0 - 50.0
            double z = rng.nextDouble() * 100.0 - 50.0
            points.add(new Point(x, y, z))
        }
        return points
    }

    private static double[] parseRadii(String s) {
        String[] parts = s.split(",")
        double[] result = new double[parts.length]
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim())
        }
        return result
    }

    /**
     * Compute average time in ms, skipping the first iteration (warmup).
     */
    private static double avgMs(long[] nanoTimes) {
        if (nanoTimes.length <= 1) {
            return nanoTimes[0] / 1_000_000.0
        }
        long sum = 0
        for (int i = 1; i < nanoTimes.length; i++) {
            sum += nanoTimes[i]
        }
        return (sum / (nanoTimes.length - 1)) / 1_000_000.0
    }

    private static String generateReport(long[][] v1Times, long[][] v2Times, double[] radii, long totalChecks) {
        StringBuilder sb = new StringBuilder()
        sb.append("=== KdTree Benchmark Report ===\n")
        sb.append("Points: $POINT_COUNT  Queries: $QUERY_COUNT  Iterations: $ITERATIONS  Base seed: $BASE_SEED\n")
        if (ITERATIONS > 1) {
            sb.append("(First iteration is warmup, excluded from averages)\n")
        }
        sb.append("\n")
        sb.append(String.format("%-28s %10s %10s %10s\n", "Operation", "v1 (ms)", "v2 (ms)", "speedup"))
        sb.append("-".multiply(60))
        sb.append("\n")

        // Build
        appendRow(sb, "Build", v1Times[OP_BUILD], v2Times[OP_BUILD])

        // findNearest
        appendRow(sb, "findNearest", v1Times[OP_NEAREST], v2Times[OP_NEAREST])

        // Radius queries
        for (int i = 0; i < radii.length; i++) {
            appendRow(sb, "radius r=${String.format('%.1f', radii[i])}", v1Times[2 + i], v2Times[2 + i])
        }

        // k-NN
        int opKnn = 2 + radii.length
        appendRow(sb, "findNearestN k=9", v1Times[opKnn], v2Times[opKnn])

        sb.append("-".multiply(60))
        sb.append("\n")
        sb.append("\nAll correctness checks passed: $totalChecks total\n")
        return sb.toString()
    }

    private static void appendRow(StringBuilder sb, String name, long[] v1Nanos, long[] v2Nanos) {
        double v1ms = avgMs(v1Nanos)
        double v2ms = avgMs(v2Nanos)
        double speedup = v1ms > 0 ? v1ms / v2ms : 0
        sb.append(String.format("%-28s %10.1f %10.1f %9.2fx\n", name, v1ms, v2ms, speedup))
    }
}
