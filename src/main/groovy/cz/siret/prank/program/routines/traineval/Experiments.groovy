package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.DatasetCachedLoader
import cz.siret.prank.domain.loaders.electrostatics.DelphiCubeLoader
import cz.siret.prank.domain.loaders.electrostatics.GaussianCube
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.ListParam
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.optimize.GridOptimizer
import cz.siret.prank.program.routines.optimize.HyperOptimizer
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Bench
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Bench.timeit
import static cz.siret.prank.utils.Bench.timeitLog
import static cz.siret.prank.utils.Futils.*
import static cz.siret.prank.utils.ThreadUtils.async

/**
 * ploop, hopt and traineval routines for optimization experiments
 */
@Slf4j
@CompileStatic
class Experiments extends Routine {

    String command

    Dataset trainDataset
    Dataset evalDataset
    boolean doCrossValidation = false

    String outdirRoot
    String datadirRoot

    String label

    Main main
    CmdLineArgs cmdLineArgs

    public Experiments(CmdLineArgs args, Main main, String command) {
        super(null)
        this.cmdLineArgs = args
        this.command = command
        this.main = main

        if (command in ['traineval', 'ploop', 'hopt']) {
            prepareDatasets(main)
        }
    }

    void prepareDatasets(Main main) {

        String trainSetArg =  cmdLineArgs.get('train', 't')
        if (trainSetArg == null) {
            throw new PrankException("Training dataset is not specified (-t/--train)")
        }
        trainDataset = prepareDataset(trainSetArg)

        // TODO: enable executing 'prank ploop crossval'
        // (now ploop with crossvalidation is possible only implicitly by not specifying eval dataset)

        String evalSetArg  =  cmdLineArgs.get('eval', 'e')
        if (evalSetArg!=null) { // no eval dataset -> do crossvalidation
            evalDataset = prepareDataset(evalSetArg)
        } else {
            doCrossValidation = true
        }

        outdirRoot = params.output_base_dir
        datadirRoot = params.dataset_base_dir
        label = command + "_" + safe(trainDataset.label) + "_" + (doCrossValidation ? "crossval" : safe(evalDataset.label))

        outdir = main.findOutdir(label)
        main.configureLoggers(outdir)
        main.writeCmdLineArgs(outdir)
        writeParams(outdir)
    }

    Dataset prepareDataset(String datasetArg) {
        assert datasetArg!=null
        if (datasetArg.contains('+')) {
            // joined dataset
            List<Dataset> datasets = Sutils.split(datasetArg, '+').collect { prepareSingleDataset(it) }.toList()
            return Dataset.createJoined(datasets)
        } else {
            return prepareSingleDataset(datasetArg)
        }
    }

    Dataset prepareSingleDataset(String datasetArg) {
        try {
            String file = Main.findDataset(datasetArg)
            return DatasetCachedLoader.loadDataset(file)
        } catch (Exception e) {
            throw new PrankException("Failed to load dataset '${datasetArg}'", e)
        }
    }

    @CompileStatic(value = TypeCheckingMode.SKIP)
    void execute() {
        log.info "executing $command()"

        this."$command"()  // dynamic exec method

        if (outdir != null) {
            writeFile "$outdir/status.done", "done"
            log.info "results saved to directory [${Futils.absPath(outdir)}]"
        }
    }

//===========================================================================================================//

    /**
     * train/eval on different datasets for different seeds
     * collecting train vectors only once and training+evaluating many times
     */
    private static EvalResults doTrainEval(String outdir, Dataset trainData, Dataset evalData) {

        TrainEvalRoutine iter = new TrainEvalRoutine(outdir, trainData, evalData)

        if (Params.inst.collect_only_once) {
            iter.collectTrainVectors()
            if (Params.inst.collect_eval_vectors) {
                iter.collectEvalVectors() // collect and save to disk for further inspection
            }
        }

        EvalRoutine trainRoutine = new EvalRoutine(outdir) {
            @Override
            EvalResults execute() {
                if (!Params.inst.collect_only_once) { // ensures that if subsampling is turned on it is done before each training
                    iter.collectTrainVectors()
                }

                iter.outdir = getEvalRoutineOutdir() // is set to "../seed.xx" by SeedLoop
                def res = iter.trainAndEvalModel()
                return res
            }
        }

        return new SeedLoop(trainRoutine, outdir).execute()
    }

//===========================================================================================================//

    /**
     * implements command: 'prank traineval...  '
     */
    public EvalResults traineval() {
        doTrainEval(outdir, trainDataset, evalDataset)
    }


    /**
     *  iterative parameter optimization
     */
    public ploop() {

        gridOptimize(ListParam.parseListArgs(cmdLineArgs))
    }

//===========================================================================================================//

    private void gridOptimize(List<ListParam> rparams) {

        write "List variables: " + rparams.toListString()

        String topOutdir = outdir

        GridOptimizer go = new GridOptimizer(topOutdir, rparams)
        go.init()
        go.runGridOptimization { String iterDir ->
            return runExperimentStep(iterDir, trainDataset, evalDataset, doCrossValidation)
        }
    }

    /**
     * run trineval or crossvalidation with current parameter assignment
     */
    private static EvalResults runExperimentStep(String dir, Dataset trainData, Dataset evalData, boolean doCrossValidation) {
        EvalResults res

        if (doCrossValidation) {
            EvalRoutine routine = new CrossValidation(dir, trainData)
            res = new SeedLoop(routine, dir).execute()
        } else {
            res = doTrainEval(dir, trainData, evalData)
        }

        if (Params.inst.ploop_delete_runs) {
            async { Futils.delete(dir) }
        }

        if (Params.inst.clear_prim_caches) {
            trainData.clearPrimaryCaches()
            evalData?.clearPrimaryCaches()
        } else if (Params.inst.clear_sec_caches) {
            trainData.clearSecondaryCaches()
            evalData?.clearSecondaryCaches()
        }

        return res
    }

//===========================================================================================================//

    /**
     *  hyperparameter optimization
     */
    public hopt() {
        HyperOptimizer ho = new HyperOptimizer(outdir, ListParam.parseListArgs(cmdLineArgs))
        ho.init()

        ho.optimizeParameters {  String stepDir ->
            return runExperimentStep(stepDir, trainDataset, evalDataset, doCrossValidation)
        }
    }

//===========================================================================================================//

    /**
     *  print parameters and exit
     */
    public params() {
        write params.toString()
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

    def bench_model_loading() {
        String modelf = main.findModel()

        def model = null
        timeitLog "loading model", params.loop, {
            model = Model.loadFromFile(modelf)
        }
    }


}








