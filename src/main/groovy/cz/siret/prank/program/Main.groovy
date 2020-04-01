package cz.siret.prank.program

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.*
import cz.siret.prank.utils.*
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

@Slf4j
class Main implements Parametrized, Writable {

    static Properties buildProperties = Futils.loadProperties('/build.properties')

    CmdLineArgs args
    String command
    String installDir

    LogManager logManager = new LogManager()

    boolean error = false

//===========================================================================================================//

    String getInstallDir() {
        return installDir
    }

    String getConfigFileParam() {
        args.get('config','c')
    }

    void initParams(Params params, String defaultConfigFile) {

        log.info "loading default config from [${Futils.absPath(defaultConfigFile)}]"
        File defaultParams = new File(defaultConfigFile)
        ConfigLoader.overrideConfig(params, defaultParams)
        String configParam = configFileParam

        // TODO allow multiple -c variables override default+dev+working
        if (configParam!=null) {

            if (!configParam.endsWith(".groovy") && Futils.exists(configParam+".groovy"))  {
                configParam = configParam + ".groovy"
            }

            File customParams = new File(configParam)
            if (!customParams.exists()) {
                customParams = new File("$installDir/config/$configParam")
            }

            log.info "overriding default config with [${Futils.absPath(customParams.path)}]"
            ConfigLoader.overrideConfig(params, customParams)
        }

        params.updateFromCommandLine(args)
        params.with {
            dataset_base_dir = evalDirParam(dataset_base_dir)
            output_base_dir = evalDirParam(output_base_dir)
        }

        String mod = args.get('m')
        if (mod!=null) {
            params.model = mod
        }

        log.debug "CMD LINE ARGS: " + args
    }

    String evalDirParam(String dir) {
        if (dir==null) {
            dir = "."
        } else {
            if (!new File(dir).isAbsolute()) {
                dir = "$installDir/$dir"
            }
        }
        assert dir != null
        dir = Futils.normalize(dir)
        assert dir != null
        return dir
    }

    public static String findModel(String installDir, Params params) {
        String modelName = params.model

        String modelf = modelName
        if (!Futils.exists(modelf)) {
            modelf = "$installDir/models/$modelf"
        }
        if (!Futils.exists(modelf)) {
            log.error "Model file [$modelName] not found!"
            throw new PrankException("model not found")
        }
        return modelf
    }

    String findModel() {
        return findModel(installDir, params)
    }

    static String findDataset(String dataf) {
        if (dataf==null) {
            throw new PrankException('dataset not specified!')
        }

        if (!Futils.exists(dataf)) {
            log.info "looking for dataset in working dir [${Futils.absPath(dataf)}]... failed"
            dataf = "${Params.inst.dataset_base_dir}/$dataf"
        }
        log.info "looking for dataset in dataset_base_dir [${Futils.absPath(dataf)}]..."
        return dataf
    }

    /**
     * -o makes outdir in wrking path
     * -l makes outdir in output_base_dir
     * @param defaultName of dir created in output_base_dir
     */
    String findOutdir(String defaultName) {
        String outdir = null
        String label = args.get('label','l')

        String prefixdate = ""
        if (params.out_prefix_date) {
            prefixdate = Sutils.timeLabel() + "_"
        }

        if (label==null) {
            label = args.get('model','m')  // use model name as label
        }

        String base = params.output_base_dir
        if (StringUtils.isNotEmpty(params.out_subdir)) {
            base += "/" + params.out_subdir
        }

        if (label!=null) {
            outdir = "${base}/${prefixdate}${defaultName}_$label"
        } else {
            outdir = args.get('o')
            if (outdir==null) {
                outdir = "${base}/${prefixdate}$defaultName"
            }
        }

        mkdirs(outdir)
        return outdir
    }

    Dataset loadDataset() {
        Dataset.loadFromFile(findDataset(args.unnamedArgs[0])) // by default dataset is the first unnamed argument after command
    }

    Dataset loadDatasetOrFile() {
        String fparam = args.namedArgMap.get("f")  // single file param -f
        if (fparam!=null) {
            return Dataset.createSingleFileDataset(fparam)
        } else {
            return loadDataset()
        }
    }

    String findInstallDir() {

        String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decodedPath = URLDecoder.decode(path, "UTF-8");

        return Futils.normalize(Futils.dir(decodedPath) + "/../")

    }

    void writeCmdLineArgs(String outdir) {
        writeFile("$outdir/cmdline_args.txt", args)
    }

//===========================================================================================================//

    void doRunPredict(String label, boolean evalPredict) {

        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("${label}_$dataset.label")

        configureLoggers(outdir)

        PredictRoutine predictRoutine = new PredictRoutine(
                dataset,
                findModel(),
                outdir)

        if (evalPredict) {
            predictRoutine.collectStats = true
        }

        Dataset.Result result = predictRoutine.execute()
        finalizeDatasetResult(result)
    }

    void finalizeDatasetResult(Dataset.Result result) {
        if (result.hasErrors()) {
            error = true
            write "ERROR on processing $result.errorCount file(s):"

            for (Dataset.Item item : result.errorItems) {
                write "    [$item.label]"
            }
        }
    }

//===========================================================================================================//

    void runPredict() {
        doRunPredict("predict", false)
    }

    void runEvalPredict() {
        doRunPredict("eval_predict", true)
    }

    void runRescore() {

        initRescoreDefaultParams()

        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("rescore_$dataset.label")

        configureLoggers(outdir)

        Dataset.Result result = new RescoreRoutine(
                dataset,
                findModel(),
                outdir).execute()

        finalizeDatasetResult(result)
    }

    void runEvalRescore() {

        initRescoreDefaultParams()

        Dataset dataset = loadDataset()
        String outdir = findOutdir("eval_rescore_$dataset.label")

        configureLoggers(outdir)

        new EvalPocketsRoutine(
                dataset,
                Model.loadFromFile(findModel()),
                outdir).execute()

    }

    private runExperiment(String routineName) {

        new Experiments(args, this, routineName).execute()
    }

    private runCrossvalidation() {
        Dataset dataset = loadDataset()
        String outdir = findOutdir("crossval_" + dataset.label)

        configureLoggers(outdir)

        CrossValidation routine = new CrossValidation(outdir, dataset)
        new SeedLoop(routine, outdir).execute()
    }

    private runAnalyze() {
        new AnalyzeRoutine(args, this).execute()
    }

    void runHelp() {
        println Futils.readResource('/help.txt')
    }


    void initRescoreDefaultParams() {
        initParams(params, "$installDir/config/default-rescore.groovy")
    }

    /**
     * @return false if successful, true it there was some (recoverable) error during execution
     */
    boolean run() {

        command = args.unnamedArgs.size()>0 ? args.unnamedArgs.first() : "help"
        args.shiftUnnamedArgs()

        installDir = findInstallDir()
        params.installDir = installDir // TODO refactor

        if (command in ["ploop","hopt"]) {
            args.hasListParams = true
        }

        if (command=='help' || args.hasSwitch('h','help')) {
            runHelp()
            return true
        }

        initParams(params, "$installDir/config/default.groovy")

        if (params.predict_residues && !params.ligand_derived_point_labeling) { // TODO move
            LoaderParams.ignoreLigandsSwitch = true
        }

        switch (command) {
            case 'predict':       runPredict()
                break
            case 'eval-predict':  runEvalPredict()
                break
            case 'rescore':       runRescore()
                break
            case 'eval-rescore':  runEvalRescore()
                break
            case 'crossval':      runCrossvalidation()
                break
            case 'analyze':       runAnalyze()
                break
            case 'run':           runExperiment(args.unnamedArgs[0])
                break
            default:
                runExperiment(command)
        }

        finalizeLog()

        return error
    }

    void configureLoggers(String outdir) {
        logManager.configureLoggers(params.log_level, params.log_to_console, params.log_to_file, outdir)
    }

    void finalizeLog() {
        if (logManager.loggingToFile && params.zip_log_file) {
            logManager.stopFileAppender()
            Futils.zipAndDelete(logManager.logFile, Futils.ZIP_BEST_COMPRESSION)
        }
    }

//===========================================================================================================//

    Main(CmdLineArgs args) {
        this.args = args
    }

    static String getVersion() {
        return buildProperties.getProperty('version')
    }

    static String getVersionName() {
        return "P2Rank $version"
    }

    static void main(String[] args) {
        ATimer timer = startTimer()
        CmdLineArgs parsedArgs = CmdLineArgs.parse(args)

        if (parsedArgs.hasSwitch("v", "version")) {
            write "$versionName"
            return
        }

        write "----------------------------------------------------------------------------------------------"
        write " $versionName"
        write "----------------------------------------------------------------------------------------------"
        write ""

        boolean error = false

        Main main
        try {

            main = new Main(parsedArgs)
            error = main.run()

        } catch (Throwable e) {

            error = true

            if (e instanceof PrankException) {
                writeError e.message
                log.error(e.message, e)
            } else {
                writeError e.getMessage(), e // on unknown exception also print stack trace
            }

            if (main!=null) {
                String logLocation = "$main.installDir/log/prank.log"
                if (main.logManager.loggingToFile) {
                    logLocation = main.logManager.logFile
                }
                write "For details see log file: $logLocation"
            }

        }

        write ""
        write "----------------------------------------------------------------------------------------------"
        write " finished ${error?"with ERROR":"successfully"} in $timer.formatted"
        write "----------------------------------------------------------------------------------------------"

        if (error) {
            System.exit(1)
        }

    }

}
