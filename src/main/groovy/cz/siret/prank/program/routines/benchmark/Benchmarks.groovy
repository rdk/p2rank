package cz.siret.prank.program.routines.benchmark

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.electrostatics.DelphiCubeLoader
import cz.siret.prank.domain.loaders.electrostatics.GaussianCube
import cz.siret.prank.program.Main
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.CmdLineArgs
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Bench.timeitLog
import static cz.siret.prank.utils.Futils.*

/**
 * Old benchmarks moved from Experiments routine
 * TODO revive
 */
@Slf4j
@CompileStatic
class Benchmarks extends Routine {

    String command

    Dataset trainDataset
    Dataset evalDataset
    boolean doCrossValidation = false

    String outdirRoot
    String datadirRoot

    String label

    Main main
    CmdLineArgs cmdLineArgs

    public Benchmarks(CmdLineArgs args, Main main, String command) {
        super(null)
        this.cmdLineArgs = args
        this.command = command
        this.main = main

//        if (!commandRegister.containsKey(command)) {
//            throw new PrankException("Invalid command: " + command)
//        }
//
//        //if (command in ['traineval', 'ploop', 'hopt']) {
//        prepareDatasets(main)
//        //}
    }


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
