package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.DatasetCachedLoader
import cz.siret.prank.features.FeatureSetup
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.implementation.table.AAIndexFeature
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.IterativeParam
import cz.siret.prank.program.params.ListParam
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.optimize.GridOptimizerRoutine
import cz.siret.prank.program.routines.optimize.HyperOptimizerRoutine
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import org.paukov.combinatorics3.Generator

import javax.annotation.Nonnull
import java.util.stream.Collectors

import static cz.siret.prank.utils.Futils.safe
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.ThreadUtils.async
import static java.util.Collections.unmodifiableMap

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

        //if (command in ['traineval', 'ploop', 'hopt']) {
            prepareDatasets(main)
        //}
    }

//===========================================================================================================//
// Sub-Commands
//===========================================================================================================//

    final Map<String, Closure> commandRegister = unmodifiableMap([
        "traineval" : { traineval() },
        "ploop" :     { ploop() },
        "hopt" :      { hopt() },

        "ploop-features-tryeach" :        { ploop_features_tryeach() },
        "ploop-features-tryeach-pairs" :  { ploop_features_tryeach_pairs() },
        "ploop-features-subsets" :        { ploop_features_subsets() },
        "ploop-features-leaveoneout" :    { ploop_features_leaveoneout() },
        "ploop-subfeatures-tryeach" :     { ploop_subfeatures_tryeach() },
        "ploop-subfeatures-leaveoneout" : { ploop_subfeatures_leaveoneout() },

        "ploop-features-random" : { ploop_features_random() },
        "ploop-subfeatures-random" : { ploop_subfeatures_random() },

        "ploop-aa-index" :                { ploop_aa_index() },
    ])

//===========================================================================================================//

    @CompileStatic(value = TypeCheckingMode.SKIP)
    void execute() {
        log.info "executing $command()"

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
        trainDataset = prepareDataset(trainSetArg).forTraining(true)

        // TODO: enable executing 'prank ploop crossval'
        // (now ploop with crossvalidation is possible only implicitly by not specifying eval dataset)

        String evalSetArg  =  cmdLineArgs.get('eval', 'e')
        if (evalSetArg!=null) { // no eval dataset -> do crossvalidation
            evalDataset = prepareDataset(evalSetArg).forTraining(false)
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

    private void gridOptimize(List<? extends IterativeParam> rparams) {

        write "List variables: " + rparams.toListString()

        String topOutdir = outdir

        TrainEvalContext context = createOptimizationContext()

        GridOptimizerRoutine go = new GridOptimizerRoutine(topOutdir, rparams)
        go.init()
        go.runGridOptimization { String iterDir ->
            return runExperimentStep(iterDir, trainDataset, evalDataset, context, doCrossValidation)
        }
    }

    private TrainEvalContext createOptimizationContext() {
        TrainEvalContext context = TrainEvalContext.create()
        if (params.hopt_train_only_once) {
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
        HyperOptimizerRoutine ho = new HyperOptimizerRoutine(outdir, ListParam.parseListArgs(cmdLineArgs))
        ho.init()

        TrainEvalContext context = createOptimizationContext()

        ho.optimizeParameters { String stepDir ->
            return runExperimentStep(stepDir, trainDataset, evalDataset, context, doCrossValidation)
        }
    }



//===========================================================================================================//

    private checkNoListParams() {
        List<String> listParamNames = ListParam.parseListArgs(cmdLineArgs)*.name

        if (!listParamNames.empty) {
            throw new PrankException("No list params should be specified when running $command command. Specified list params: " + listParamNames)
        }
    }

    private runPloopWithFeatureFilters(List<List<String>> filters) {

        List<String> sFilters = filters.collect {toListLiteral(it) }

        write "Generated feature filters: " + sFilters

        gridOptimize([new ListParam("feature_filters", sFilters)])
    }

    private FeatureSetup getCurrentFeatureSetup() {
        new PrankFeatureExtractor().featureSetup
    }

    public ploop_features_tryeach() {
        checkNoListParams()

        List<String> names = currentFeatureSetup.enabledFeatures*.name
        List<String> all = names.collect { it + ".*" }

        List<List<String>> filters = [["*"]] + all.collect { [it] }

        runPloopWithFeatureFilters(filters)
    }

    public ploop_features_tryeach_pairs() {
        checkNoListParams()

        List<String> names = currentFeatureSetup.enabledFeatures*.name
        List<String> all = names.collect { it + ".*" }

        List<List<String>> filters = [["*"]] + all.collect { [it] } + generatePairs(all)

        runPloopWithFeatureFilters(filters)
    }

    public ploop_features_subsets() {
        checkNoListParams()

        List<String> names = currentFeatureSetup.enabledFeatures*.name
        List<String> all = names.collect { it + ".*" }

        List<List<String>> filters = [["*"]] + generateMiddleSubsets(all)

        runPloopWithFeatureFilters(filters)
    }


    public ploop_subfeatures_tryeach() {
        checkNoListParams()

        List<String> names = currentFeatureSetup.subFeaturesHeader

        List<List<String>> filters = [["*"]] + names.collect { [it] }

        runPloopWithFeatureFilters(filters)
    }

    public ploop_features_leaveoneout() {
        checkNoListParams()

        List<String> names = currentFeatureSetup.enabledFeatures*.name
        List<String> all = names.collect { it + ".*" }

        List<List<String>> filters = [["*"]] + all.collect { ["-$it" as String] }

        runPloopWithFeatureFilters(filters)
    }

    public ploop_subfeatures_leaveoneout() {
        checkNoListParams()

        List<String> names = currentFeatureSetup.subFeaturesHeader

        List<List<String>> filters = [["*"]] + names.collect { ["-$it" as String] }

        runPloopWithFeatureFilters(filters)
    }

    private List<List<String>> generatePairs(List<String> list) {
        if (list.size() < 2) return []

        return Generator.combination(list)
                .simple(2)
                .stream()
                .collect(Collectors.toList())
    }

    private List<List<String>> generateSubsets(List<String> list) {
        return Generator.subset(list)
                .simple()
                .stream()
                .collect(Collectors.toList())
    }

    /**
     * subsets except all and empty
     */
    private List<List<String>> generateMiddleSubsets(List<String> list) {
        generateSubsets(list).findAll { it.size()>0 && it.size()<list.size() }
    }

//===========================================================================================================//

    public ploop_features_random() {
        checkNoListParams()

        List<String> features = currentFeatureSetup.enabledFeatureNames
        features = features.collect { it + ".*" }


        gridOptimize([new RandomSublistGenerator("feature_filters", features)])
    }

    public ploop_subfeatures_random() {
        checkNoListParams()

        List<String> subFeatureNames = currentFeatureSetup.subFeaturesHeader

        gridOptimize([new RandomSublistGenerator("feature_filters", subFeatureNames)])
    }

    static class RandomSublistGenerator implements IterativeParam<String>, Writable  {

        String name
        List<String> fullList

        Random random = new Random()
        List<String> generatedValues = []

        RandomSublistGenerator(String name, List<String> fullList) {
            this.name = name
            this.fullList = fullList
        }

        @Override
        String getName() {
            return name
        }

        @Override
        List getValues() {
            return generatedValues
        }

        @Override
        String getNextValue() {
            List<String> randList = randomSublistNonempty(fullList, random)

            write "Generated random sublist: " + randList

            String val = toListLiteral(randList)
            generatedValues.add(val)
            return val
        }

    }

    static String toListLiteral(List<String> list) {
        return "(" + list.join(",") + ")"
    }


    @Nonnull
    static <E> List<E> randomSublistNonempty(List<E> fullList, Random random) {
        List<E> res = []
        while (res.empty) {
            res = randomSublist(fullList, random)
        }
        return res
    }

    @Nonnull
    static <E> List<E> randomSublist(List<E> fullList, Random random) {
        if (fullList == null || fullList.empty) {
            throw new IllegalArgumentException("fullList cannot empty")
        }

        def res = new ArrayList<E>()
        for (E el : fullList) {
            if (random.nextBoolean()) {
                res.add(el)
            }
        }
        return res
    }

//===========================================================================================================//

    public ploop_aa_index() {
        checkNoListParams()

        List<String> lprops = AAIndexFeature.allPropertyNames.collect { "($it)".toString() }

        write "Generated feat_aa_properties: " + lprops

        gridOptimize([new ListParam("feat_aa_properties", lprops)])
    }

}








