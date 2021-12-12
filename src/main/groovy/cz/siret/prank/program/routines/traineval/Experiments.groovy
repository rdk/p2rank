package cz.siret.prank.program.routines.traineval

import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.DatasetCachedLoader
import cz.siret.prank.domain.loaders.electrostatics.DelphiCubeLoader
import cz.siret.prank.domain.loaders.electrostatics.GaussianCube
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.ListParam
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.optimize.GridOptimizer
import cz.siret.prank.program.routines.optimize.HyperOptimizer
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j

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

        if (!commandRegister.containsKey(command)) {
            throw new PrankException("Invalid command: " + command)
        }

        if (command in ['traineval', 'ploop', 'hopt']) {
            prepareDatasets(main)
        }
    }

//===========================================================================================================//
// Sub-Commands
//===========================================================================================================//

    final Map<String, Closure> commandRegister = ImmutableMap.copyOf([
        "traineval" : { traineval() },
        "ploop" :     { ploop() },
        "hopt" :      { hopt() },
    ])

//===========================================================================================================//

    @CompileStatic(value = TypeCheckingMode.SKIP)
    void execute() {
        log.info "executing $command()"

        main.configureLoggers(outdir)

        commandRegister.get(command).call()

        if (outdir != null) {
            writeFile "$outdir/status.done", "done"
            log.info "results saved to directory [${Futils.absPath(outdir)}]"
        }
    }


//===========================================================================================================//

    void prepareDatasets(Main main) {

        String trainSetArg =  cmdLineArgs.get('train', 't')
        if (trainSetArg == null) {
            throw new PrankException("Training dataset is not specified (-t/-train)")
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

//===========================================================================================================//

    /**
     * train/eval on different datasets for different seeds
     * collecting train vectors only once and training+evaluating many times
     */
    private EvalResults doTrainEvalSeedloop(String outdir, Dataset trainData, Dataset evalData, TrainEvalContext context) {

        TrainEvalRoutine iter = new TrainEvalRoutine(outdir, trainData, evalData)

        if (params.collect_only_once) {
            if (params.hopt_train_only_once && context.trainVectorsCollected) {
                // use pre-collected vectors from the context
                // basically just for stats // TODO: remove need to keep all vectors in memory, stats are enough
                iter.trainVectors = context.trainVectors
            } else {
                // collect new vectors at the beginning of the seedloop and put to cache
                iter.collectTrainVectors()
                context.trainVectorsCollected = true
                context.trainVectors = iter.trainVectors
            }
        }

        if (params.collect_eval_vectors) {
            iter.collectEvalVectors() // collect and save to disk for further inspection
        }

        if (context.cacheModels) {
            iter.withModelCache(context.modelCache)
        }

        EvalRoutine trainEvalRoutine = new EvalRoutine(outdir) {
            @Override
            EvalResults execute() {
                if (!params.collect_only_once) { // ensures that if subsampling is turned on it is done before each training
                    iter.collectTrainVectors()
                }

                iter.outdir = getEvalRoutineOutdir() // is set to "../seed.xx" by SeedLoop
                def res = iter.trainAndEvalModel()
                return res
            }
        }

        return new SeedLoop(trainEvalRoutine, outdir).execute()
    }

//===========================================================================================================//

    /**
     * implements command: 'prank traineval...  '
     */
    public EvalResults traineval() {
        TrainEvalContext context = new TrainEvalContext()
        doTrainEvalSeedloop(outdir, trainDataset, evalDataset, context)
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

        TrainEvalContext context = createOptimizationContext()

        GridOptimizer go = new GridOptimizer(topOutdir, rparams)
        go.init()
        go.runGridOptimization { String iterDir ->
            return runExperimentStep(iterDir, trainDataset, evalDataset, context, doCrossValidation)
        }
    }

    private TrainEvalContext createOptimizationContext() {
        TrainEvalContext context = TrainEvalContext.create()
        if (params.hopt_train_only_once && params.loop > 1) {
            context.cacheModels = true
            context.modelCache = ModelCache.create()
        }
        return context
    }

    /**
     * run traineval or crossvalidation with current parameter assignment
     */
    private EvalResults runExperimentStep(String dir, Dataset trainData, Dataset evalData, TrainEvalContext context, boolean doCrossValidation) {
        EvalResults res

        if (doCrossValidation) {
            EvalRoutine routine = new CrossValidation(dir, trainData)
            res = new SeedLoop(routine, dir).execute()
        } else {
            res = doTrainEvalSeedloop(dir, trainData, evalData, context)
        }

        if (params.ploop_delete_runs) {
            async { Futils.delete(dir) }
        }

        if (params.clear_sec_caches) {
            trainData.clearSecondaryCaches()
            evalData?.clearSecondaryCaches()
        }
        if (params.clear_prim_caches) {
            trainData.clearPrimaryCaches()
            evalData?.clearPrimaryCaches()
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

        TrainEvalContext context = createOptimizationContext()

        ho.optimizeParameters { String stepDir ->
            return runExperimentStep(stepDir, trainDataset, evalDataset, context, doCrossValidation)
        }
    }

//===========================================================================================================//

    // TODO move

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








