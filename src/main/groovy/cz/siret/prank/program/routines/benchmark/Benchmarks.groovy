package cz.siret.prank.program.routines.benchmark

import cz.cuni.cusbg.surface.FasterNumericalSurface
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.electrostatics.DelphiCubeLoader
import cz.siret.prank.domain.loaders.electrostatics.GaussianCube
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptor
import cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptorRegistry
import cz.siret.prank.program.routines.predict.output.descriptors.PocketGridContext
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import cz.siret.prank.program.routines.predict.output.grid.PocketGridBuilder
import cz.siret.prank.program.routines.predict.output.grid.PocketGridConfig
import cz.siret.prank.utils.Bench
import cz.siret.prank.utils.CdkUtils
import cz.siret.prank.utils.CmdLineArgs
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.openscience.cdk.geometry.surface.NumericalSurface
import org.openscience.cdk.interfaces.IAtomContainer

import static cz.siret.prank.utils.Bench.timeitLog
import static cz.siret.prank.utils.Futils.*

/**
 * 
 */
@Slf4j
@CompileStatic
class Benchmarks extends Routine {

    Main main
    CmdLineArgs args

    Benchmarks(CmdLineArgs args, Main main) {
        super(null)
        this.args = args
        this.main = main


    }

    @CompileDynamic
    void execute() {

        String subCommand = args.unnamedArgs[0]

        log.info "executing bench $subCommand command"

        this."$subCommand"()

    }

//===========================================================================================================//

    /**
     * Pure grid-build benchmark: loads each dataset item, runs PocketGridBuilder.build,
     * and (for completeness) computes each requested descriptor. Reports per-phase
     * timings. Skips writers, rescoring, ML, visualizations. Single-threaded for
     * reproducibility — use the sh wrapper (pocket_grid_dataset_bench.sh) for
     * end-to-end multi-threaded numbers.
     *
     * Usage: prank bench pocket_grid <dataset.ds>
     */
    void pocket_grid() {
        String datasetArg = args.unnamedArgs.size() > 1 ? args.unnamedArgs[1] : args.get("f")
        if (datasetArg == null) {
            throw new PrankException("Usage: prank bench pocket_grid <dataset.ds>")
        }

        String resolved = Main.findDataset(datasetArg)
        Dataset dataset = Dataset.loadFromFile(resolved)
        log.info "Benchmarking pocket grid build on {} items from [{}]", dataset.items.size(), dataset.label

        PocketGridConfig config = PocketGridConfig.fromParams(Params.inst)

        List<PocketDescriptor> descriptors = new ArrayList<>()
        for (String name : Params.inst.pocket_descriptors) {
            descriptors.add(PocketDescriptorRegistry.get(name))
        }

        long startMs = System.currentTimeMillis()
        long loadNs = 0, buildNs = 0, descriptorNs = 0
        long totalGridPoints = 0, totalAssignedPairs = 0, totalPockets = 0
        int processed = 0, errors = 0

        // Diagnostics (collected in an untimed pass below so they never inflate the phase timings):
        //   assignedPerPocket — post-fill, post-cross-pocket-rule point count per pocket (the "pairs" unit)
        //   rawPerPocket      — pre-fill raw-shell point count per pocket (points within assignCutoff of SAS)
        // Totals let us separate "how many points were kept" from "how many got assigned" from
        // "how many assignments are genuine multi-pocket overlap".
        List<Integer> assignedPerPocket = new ArrayList<>()
        List<Integer> rawPerPocket = new ArrayList<>()
        long totalRawPairs = 0, totalDistinctAssigned = 0, totalUnassignedKept = 0

        for (Dataset.Item item : dataset.items) {
            try {
                long t0 = System.nanoTime()
                PredictionPair pair = item.predictionPair
                Protein protein = pair.protein
                List<? extends Pocket> pockets = pair.prediction.pockets
                long t1 = System.nanoTime()

                PocketGrid grid = PocketGridBuilder.build(protein, pockets, config)
                long t2 = System.nanoTime()

                for (Pocket pocket : pockets) {
                    BitSet indices = grid.indicesForPocket(pocket.rank)
                    PocketGridContext ctx = new PocketGridContext(pocket, protein, grid, indices)
                    for (PocketDescriptor d : descriptors) {
                        d.compute(ctx)
                    }
                }
                long t3 = System.nanoTime()

                loadNs       += t1 - t0
                buildNs      += t2 - t1
                descriptorNs += t3 - t2
                totalGridPoints += grid.allPoints.count
                totalPockets += pockets.size()
                processed++

                // ---- untimed diagnostics pass ----
                // protUnion is the per-protein union of all pocket assignments; its cardinality is the
                // number of DISTINCT kept points that landed in at least one pocket. (kept - distinct)
                // is the outer-shell remainder: points kept by max_dist but never assigned to any pocket.
                // NOTE: BitSet '|' here returns a NEW BitSet (Groovy DefaultGroovyMethods) — we reassign,
                // never .or() in place (see CLAUDE.md Groovy gotcha).
                BitSet protUnion = new BitSet()
                for (Pocket pocket : pockets) {
                    BitSet indices = grid.indicesForPocket(pocket.rank)
                    int card = indices.cardinality()
                    totalAssignedPairs += card
                    assignedPerPocket.add(card)
                    int rawCard = grid.rawShellForPocket(pocket.rank).cardinality()
                    totalRawPairs += rawCard
                    rawPerPocket.add(rawCard)
                    protUnion = protUnion | indices
                }
                long distinct = protUnion.cardinality()
                totalDistinctAssigned += distinct
                totalUnassignedKept += (grid.allPoints.count - distinct)
            } catch (Exception e) {
                log.error "Failed on item [{}]: {}", item.label, e.message
                errors++
            }
        }

        long totalMs = System.currentTimeMillis() - startMs
        int n = Math.max(processed, 1)
        // Per-item averages: FP division so small averages don't collapse to "0 ms"
        // (e.g. 47 ms total over 100 items). Locale.ROOT on the format() call below
        // keeps the output stable across JVM locales.
        // Totals stay as integer ms — sub-ms precision is meaningless at the aggregate
        // level, and long→{} slf4j formatting is already locale-independent.
        double loadMsAvg = loadNs / 1e6 / n
        double buildMsAvg = buildNs / 1e6 / n
        double descMsAvg = descriptorNs / 1e6 / n
        log.info "===== Pocket Grid Build Benchmark ====="
        log.info "  Items processed:        {} (errors: {})", processed, errors
        log.info "  Total pockets:          {}", totalPockets
        log.info "  Total kept grid points: {}", totalGridPoints
        log.info "  Total (point,pocket) pairs after fill: {}", totalAssignedPairs
        log.info ""
        log.info "  Load + parse pockets:   {} ms total, {} ms/protein avg",
                loadNs / 1_000_000, String.format(java.util.Locale.ROOT, "%.2f", loadMsAvg)
        log.info "  Grid build + assign:    {} ms total, {} ms/protein avg",
                buildNs / 1_000_000, String.format(java.util.Locale.ROOT, "%.2f", buildMsAvg)
        log.info "  Descriptors:            {} ms total, {} ms/protein avg",
                descriptorNs / 1_000_000, String.format(java.util.Locale.ROOT, "%.2f", descMsAvg)
        log.info "  Wall (incl. logging):   {} ms", totalMs

        // ---- coverage diagnostics ----
        // overlapPairs = assigned pairs counted more than once because a point sits in >1 pocket.
        // netFill = how much filling grew (or, if negative, how much the cross-pocket rule clawed back)
        // the assignment relative to the pre-fill raw shells.
        long overlapPairs = totalAssignedPairs - totalDistinctAssigned
        long netFill = totalAssignedPairs - totalRawPairs
        log.info ""
        log.info "===== Coverage ====="
        log.info "  Raw-shell pairs (pre-fill):      {}", totalRawPairs
        log.info "  Assigned pairs (post-fill):      {}", totalAssignedPairs
        log.info "  Net fill effect (post - raw):    {} ({})", netFill, pctStr(netFill, totalRawPairs)
        log.info "  Distinct assigned points:        {}", totalDistinctAssigned
        log.info "  Multi-pocket overlap pairs:      {} ({} of assigned)", overlapPairs, pctStr(overlapPairs, totalAssignedPairs)
        log.info "  Kept grid points (all):          {}", totalGridPoints
        log.info "  Unassigned kept points:          {} ({} of kept)", totalUnassignedKept, pctStr(totalUnassignedKept, totalGridPoints)

        logStats("Assigned points / pocket (post-fill)", assignedPerPocket)
        logStats("Raw-shell points / pocket (pre-fill)", rawPerPocket)
    }

    /** Format ratio as a percentage string, guarding divide-by-zero. */
    private static String pctStr(long num, long den) {
        if (den == 0) return "n/a"
        return String.format(java.util.Locale.ROOT, "%.1f%%", 100.0d * num / den)
    }

    /**
     * Log descriptive stats for a per-pocket count distribution: n, min, percentiles
     * (p10/p25/median/p75/p90/p95/p99), max, mean, stddev, zero-count and sum. Percentiles
     * use nearest-rank on the sorted array. Helps diagnose skew (a few huge pockets vs many
     * tiny ones) and dead pockets (zeros) that aggregate totals hide.
     */
    private static void logStats(String title, List<Integer> values) {
        log.info ""
        log.info "===== ${title} ====="
        int nv = values.size()
        if (nv == 0) {
            log.info "  (no data)"
            return
        }
        int[] a = new int[nv]
        for (int i = 0; i < nv; i++) a[i] = values.get(i)
        java.util.Arrays.sort(a)
        long sum = 0
        for (int v : a) sum += v
        double mean = sum / (double) nv
        double variance = 0
        for (int v : a) { double d = v - mean; variance += d * d }
        double sd = Math.sqrt(variance / nv)
        // sorted ascending, so any zeros form the prefix: count until the first non-zero
        int zeros = 0
        for (int v : a) {
            if (v != 0) break
            zeros++
        }
        log.info "  n={}  min={}  p10={}  p25={}  median={}  p75={}  p90={}  p95={}  p99={}  max={}",
                nv, a[0], pctile(a, 10), pctile(a, 25), pctile(a, 50), pctile(a, 75),
                pctile(a, 90), pctile(a, 95), pctile(a, 99), a[nv - 1]
        log.info "  mean={}  stddev={}  zeros={} ({})  sum={}",
                String.format(java.util.Locale.ROOT, "%.1f", mean),
                String.format(java.util.Locale.ROOT, "%.1f", sd),
                zeros, pctStr(zeros, nv), sum
    }

    /** Nearest-rank percentile on an already-sorted ascending int[]. */
    private static int pctile(int[] sorted, int p) {
        int n = sorted.length
        int rank = (int) Math.ceil(p / 100.0d * n) - 1
        if (rank < 0) rank = 0
        if (rank >= n) rank = n - 1
        return sorted[rank]
    }

//===========================================================================================================//

    /**
     * Benchmark FasterNumericalSurface against NumericalSurface
     */
    void faster_surface() {

        String structFile = args.get("f") ?: "$main.installDir/test_data/2W83.pdb"

        log.info "Benchmarking faster surface o file [$structFile]"

        Protein protein = Protein.load(structFile)


        IAtomContainer cdkAtoms = CdkUtils.toAtomContainer(protein.proteinAtoms)


        double solventRadius = 1.6
        int outerReps = 5
        int reps = 16

        for(int tesslevel in 2..4) {
            double oldTime = Bench.timeitLogWithHeatup("OLD tess:" + tesslevel, outerReps, {
                reps.times {
                    NumericalSurface numericalSurface = new NumericalSurface(cdkAtoms, solventRadius, tesslevel)
                    numericalSurface.getAllSurfacePoints()
                }
            })

            double newTime = Bench.timeitLogWithHeatup("NEW tess:" + tesslevel, outerReps, {
                reps.times {
                    FasterNumericalSurface numericalSurface = new FasterNumericalSurface(cdkAtoms, solventRadius, tesslevel)
                    numericalSurface.getAllSurfacePoints()
                }
            })

            double timeMult = oldTime / newTime
            log.info("Tessellation $tesslevel SPEEDUP: {}", Math.round(timeMult * 1000)/1000 )
        }

    }

//===========================================================================================================//

    /**
     * for jvm profiler
     */
    def bench_delphi_loading() {
        def fname = 'src/test/resources/data/electrostatics/delphi/tmp/delphi-6PW2.cube'
        GaussianCube cube
        int n = 5
        timeitLog("loading from text",    n, { cube = DelphiCubeLoader.loadFile(fname)      })
    }

    /**
     * Benchmark compression algorithms on small binary file
     */
    def bench_compression_large() {
        _benchmarkCompression('src/test/resources/data/electrostatics/delphi/tmp/delphi-6PW2.cube', 1)

    }

    /**
     * Benchmark compression algorithms on small binary file
     */
    def bench_compression_small() {
        _benchmarkCompression("src/test/resources/data/electrostatics/delphi/tmp/delphi-2src.cube", 10)
    }

    private _benchmarkCompression(String fname, int n) {
        GaussianCube cube
        timeitLog("loading from text",    n, { cube = DelphiCubeLoader.loadFile(fname     )      })
        //timeit("loading from gz text", n, { cube = DelphiCubeLoader.loadFile(fname+".gz")      })

        timeitLog("saving to ser",     n, { serializeToFile("${fname}.jser", cube)      })
        timeitLog("loading from ser",  n, { cube = deserializeFromFile("${fname}.jser")     })

        timeitLog("saving to gz",      n, { serializeToGzip(fname+".jser.gz", cube, 6)    })
        timeitLog("loading from gz",   n, { cube = deserializeFromFile(fname+".jser.gz")   })

        timeitLog("saving to lzma",    n, { serializeToLzma(fname+".jser.lzma", cube, 3)    })
        timeitLog("loading from lzma", n, { cube = deserializeFromFile(fname+".jser.lzma")   })

        //timeit("saving to zstd",    n, { serializeToZstd(fname+".jser.zstd", cube, 6)    })
        //timeit("loading from zstd", n, { cube = deserializeFromFile(fname+".jser.zstd")   })

        (1..9).each { l ->
            timeitLog("   saving to zstd$l", n, { serializeToZstd(fname+".jser.${l}.zstd", cube, l)    })
            timeitLog("loading from zstd$l", n, { cube = deserializeFromFile(fname+".jser.${l}.zstd")   })
        }
    }

//    def bench_model_loading() {
//        String modelf = main.findModel()
//
//        def model = null
//        timeitLog "loading model", params.loop, {
//            model = Model.loadFromFile(modelf)
//        }
//    }

}
