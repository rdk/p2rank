package cz.siret.prank.program.routines

import cz.siret.prank.utils.ThreadUtils
import groovy.util.logging.Slf4j
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.DatasetCachedLoader
import cz.siret.prank.program.Main
import cz.siret.prank.program.params.RangeParam
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.futils

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
        log.info "results saved to directory [${futils.absPath(outdir)}]"
    }

//===========================================================================================================//

    /**
     * train/eval on different datasets for different seeds
     */
    CompositeRoutine.Results traineval() {

        TrainEvalIteration iter = new TrainEvalIteration()
        iter.outdir = outdir
        iter.trainDataSet = trainDataSet
        iter.evalDataSet = evalDataSet

        iter.collectTrainVectors()
        //iter.collectEvalVectors() // for further inspetion

        String loop_outdir = outdir

        CompositeRoutine trainRoutine = new CompositeRoutine() {
            @Override
            Results execute() {
                iter.label = "seed.${params.seed}"
                iter.outdir = "$loop_outdir/$iter.label"
                iter.trainAndEvalModel()

                return iter.evalRoutine.results
            }
        }

        return new SeedLoop(trainRoutine, outdir).execute()
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

        new ParamLooper(topOutdir, rparams).iterateParams { String label ->
            outdir = "$topOutdir/$label"

            CompositeRoutine.Results res

            if (doCrossValidation) {
                CompositeRoutine routine = new CrossValidation(outdir, trainDataSet)
                res = new SeedLoop(routine, outdir).execute()
            } else {
                res = traineval()
            }

            if (params.ploop_delete_runs) {
                async { futils.delete(outdir) }
            } else if (params.ploop_zip_runs) {
                async { futils.zipAndDelete(outdir) }
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








