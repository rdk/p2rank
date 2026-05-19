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
                    totalAssignedPairs += indices.cardinality()
                }
                long t3 = System.nanoTime()

                loadNs       += t1 - t0
                buildNs      += t2 - t1
                descriptorNs += t3 - t2
                totalGridPoints += grid.allPoints.count
                totalPockets += pockets.size()
                processed++
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
