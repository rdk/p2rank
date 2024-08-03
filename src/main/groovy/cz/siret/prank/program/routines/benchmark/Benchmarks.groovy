package cz.siret.prank.program.routines.benchmark

import cz.cuni.cusbg.surface.FasterNumericalSurface
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.electrostatics.DelphiCubeLoader
import cz.siret.prank.domain.loaders.electrostatics.GaussianCube
import cz.siret.prank.program.Main
import cz.siret.prank.program.routines.Routine
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
