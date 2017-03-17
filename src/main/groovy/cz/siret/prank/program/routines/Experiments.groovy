package cz.siret.prank.program.routines

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.DatasetCachedLoader
import cz.siret.prank.program.Main
import cz.siret.prank.program.params.RangeParam
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ThreadUtils.async

/**
 * ploop and traineval routines for oprimization experiments
 */
@Slf4j
class Experiments extends Routine {

    Dataset trainDataSet
    Dataset evalDataSet
    boolean doCrossValidation = false

    String trainSetFile
    String evalSetFile
    String outdirRoot
    String datadirRoot

    String label

    CmdLineArgs cmdLineArgs

    public Experiments(CmdLineArgs args, Main main) {
        super(null)
        this.cmdLineArgs = args

        trainSetFile =  cmdLineArgs.get('train', 't')
        trainSetFile = Main.findDataset(trainSetFile)
        trainDataSet = DatasetCachedLoader.loadDataset(trainSetFile)

        // TODO: enable executing 'prank ploop crossval'
        // (now ploop with crossvalidation is possible only implicitly by not specifying eval dataset)

        evalSetFile  =  cmdLineArgs.get('eval', 'e')
        if (evalSetFile!=null) { // no eval dataset -> do crossvalidation
            evalSetFile = Main.findDataset(evalSetFile)
            evalDataSet = DatasetCachedLoader.loadDataset(evalSetFile)
        } else {
            doCrossValidation = true
        }

        outdirRoot = params.output_base_dir
        datadirRoot = params.dataset_base_dir
        label = "run_" + trainDataSet.label + "_" + (doCrossValidation ? "crossval" : evalDataSet.label)
        outdir = main.findOutdir(label)

        main.configureLoggers(outdir)
    }

    void execute(String routineName) {
        log.info "executing $routineName()"
        this."$routineName"()  // dynamic exec method
        log.info "results saved to directory [${Futils.absPath(outdir)}]"
    }

//===========================================================================================================//

    /**
     * train/eval on different datasets for different seeds
     *
     *
     * collecting train vectors only once and training+evaluatng many times
     */
    private EvalResults doTrainEval(String outdir) {

        TrainEvalRoutine iter = new TrainEvalRoutine(outdir)
        iter.trainDataSet = trainDataSet
        iter.evalDataSet = evalDataSet
        iter.collectTrainVectors()
        //iter.collectEvalVectors() // for further inspetion

        EvalRoutine trainRoutine = new EvalRoutine(outdir) {
            @Override
            EvalResults execute() {
                iter.outdir = this.outdir // is set to "../seed.xx" by SeedLoop
                iter.trainAndEvalModel()
                return iter.evalRoutine.results
            }
        }

        return new SeedLoop(trainRoutine, outdir).execute()
    }

    /**
     * implements command: 'prank traineval...  '
     */
    EvalResults traineval() {
        doTrainEval(outdir)
    }

//===========================================================================================================//

    /**
     *  iterative parameter optimization
     */
    public ploop() {

        loopParams(RangeParam.parseRangedArgs(cmdLineArgs))
    }

    private void loopParams(List<RangeParam> rparams) {

        log.info "Ranged params: " + rparams.toListString()

        String topOutdir = outdir

        new ParamLooper(topOutdir, rparams).iterateSteps { String iterDir ->
            EvalResults res

            if (doCrossValidation) {
                EvalRoutine routine = new CrossValidation(iterDir, trainDataSet)
                res = new SeedLoop(routine, iterDir).execute()
            } else {
                res = doTrainEval(iterDir)
            }

            if (params.ploop_delete_runs) {
                async { Futils.delete(iterDir) }
            }

            if (params.clear_prim_caches) {
                trainDataSet.clearPrimaryCaches()
                evalDataSet?.clearPrimaryCaches()
            } else if (params.clear_sec_caches) {
                trainDataSet.clearSecondaryCaches()
                evalDataSet?.clearSecondaryCaches()
            }

            return res
        }
    }

}








