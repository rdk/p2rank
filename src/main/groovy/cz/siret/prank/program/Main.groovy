package cz.siret.prank.program

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.analyze.AnalyzeRoutine
import cz.siret.prank.program.routines.analyze.PrintRoutine
import cz.siret.prank.program.routines.analyze.TransformRoutine
import cz.siret.prank.program.routines.predict.PredictPocketsRoutine
import cz.siret.prank.program.routines.predict.PredictResiduesRoutine
import cz.siret.prank.program.routines.predict.RescorePocketsRoutine
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.program.routines.traineval.*
import cz.siret.prank.utils.*
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import java.text.DateFormat
import java.text.SimpleDateFormat

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Console.write
import static cz.siret.prank.utils.Console.writeError
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

@Slf4j
@CompileStatic
class Main implements Parametrized, Writable {

    static Properties buildProperties = Futils.loadProperties('/build.properties')

    CmdLineArgs args
    String command
    String installDir

    LogManager logManager = new LogManager()

    boolean error = false

//===========================================================================================================//

    static boolean _do_stdout_timestamp = false
    static DateFormat _timestamp_format = null

//===========================================================================================================//

    String getInstallDir() {
        return installDir
    }

    String getConfigFileParam() {
        args.get('config','c')
    }

    private File findConfigFile(List<String> paths) {
        for (String path : paths) {
            log.info "Looking for config in " + Futils.absPath(path)
            if (Futils.exists(path)) {
                return new File(path)
            }
        }
        return null
    }

    private File findConfigFile(String configParam) {
        String path = configParam

        File configFile = findConfigFile([
            path,
            "${path}.groovy",
            "$installDir/config/${path}",
            "$installDir/config/${path}.groovy"
        ] as List<String>)

        if (configFile == null) {
            throw new PrankException("Config file not found '$configParam'")
        }
        return configFile
    }


    void initParams(Params params, String defaultConfigFile) {

        File fdefault = new File(defaultConfigFile)
        log.info "loading default config from [$fdefault.absolutePath]"
        ConfigLoader.overrideConfig(params, fdefault)
        String lastConfigPath = fdefault.absolutePath

        String configParam = configFileParam
        if (configParam != null) {
            // TODO allow multiple -c variables override default+dev+working
            File fcustom = findConfigFile(configParam)
            log.info "overriding default config with [$fcustom.absolutePath]"
            ConfigLoader.overrideConfig(params, fcustom)
            lastConfigPath = fcustom.absolutePath
        }

        params.dataset_base_dir = evalDirParam(params.dataset_base_dir, Futils.dir(lastConfigPath))
        params.output_base_dir = evalDirParam(params.output_base_dir, Futils.dir(lastConfigPath))

        params.updateFromCommandLine(args)
        if (args.namedArgs.contains("dataset_base_dir")) {
            params.dataset_base_dir = evalDirParam(params.dataset_base_dir, ".")
        }
        if (args.namedArgs.contains("output_base_dir")) {
            params.output_base_dir = evalDirParam(params.output_base_dir, ".")
        }


        String mod = args.get('m')
        if (mod!=null) {
            params.model = mod
        }

        if (params.predict_residues && !params.ligand_derived_point_labeling) { // TODO move
            LoaderParams.ignoreLigandsSwitch = true
        }

        if (StringUtils.isNotBlank(params.stdout_timestamp)) {
            _do_stdout_timestamp = true
            _timestamp_format = new SimpleDateFormat(params.stdout_timestamp)
        }

        log.debug "CMD LINE ARGS: " + args
    }

    String evalDirParam(String dir, String relativePrefixDir) {
        if (dir == null) {
            dir = "."
        } else {
            if (!new File(dir).isAbsolute()) {
                dir = "$relativePrefixDir/$dir"
            }
        }
        assert dir != null
        dir = Futils.absPath(Futils.normalize(dir))
        assert dir != null
        return dir
    }

    static String findModel(String installDir, Params params) {
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
     * Generate name of the output directory.
     *
     * -o ... explicit output directory parameter, overrides all
     * -l/-label ... label that is added as suffix to the output directory created in output_base_dir
     *
     * @param defaultName of dir created in output_base_dir
     */
    String findOutdir(String defaultName) {
        String outdir = null

        String explicitOutdir = args.get('o')
        if (explicitOutdir != null) {
            log.debug("Explicit output directory specified: {}", explicitOutdir)
            outdir = explicitOutdir
        } else {
            String label = args.get('label','l')
            if (label == null) {
                label = args.get('model','m')
                log.debug("Label not specified. Using model name from cmd line as label: {}", label)
            }

            String prefixdate = (params.out_prefix_date) ? Sutils.timeLabel() + "_" :  ""
            String base = params.output_base_dir
            if (StringUtils.isNotEmpty(params.out_subdir)) {
                base += "/" + params.out_subdir
            }

            if (label != null) {
                outdir = "${base}/${prefixdate}${defaultName}_$label"
            } else {
                outdir = "${base}/${prefixdate}${defaultName}"
            }
        }

        mkdirs(outdir)
        return outdir
    }

    Dataset loadDataset() {
        Dataset.loadFromFile(findDataset(args.unnamedArgs[0])) // by default dataset is the first unnamed argument after command
    }

    Dataset loadDatasetOrFile() {
        String fparam = args.get('f')  // single file param -f
        if (fparam != null) {
            return Dataset.createSingleFileDataset(fparam)
        } else {
            return loadDataset()
        }
    }

    String findInstallDir() {
        String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()
        String decodedPath = URLDecoder.decode(path, "UTF-8")

        return Futils.normalize(Futils.dir(decodedPath) + "/../")

    }

    void writeCmdLineArgs(String outdir) {
        writeFile("$outdir/cmdline_args.txt", args)
    }

//===========================================================================================================//

    /**
     * TODO refactor predict routines
     * @param label
     * @param evalPredict
     */
    @CompileDynamic
    void doRunPredict(String label, boolean evalPredict) {
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("${label}_$dataset.label")
        configureLoggers(outdir)

        Routine predictRoutine

        if (params.predict_residues) {
            predictRoutine = new PredictResiduesRoutine(dataset, findModel(), outdir)
        } else {
            predictRoutine = new PredictPocketsRoutine(dataset, findModel(), outdir)
        }

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

        Dataset.Result result = new RescorePocketsRoutine(
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
                Model.load(findModel()),
                outdir).execute()

    }

    void runEval() {
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("eval_$dataset.label")
        configureLoggers(outdir)

        Model model = Model.load(findModel())
        model.disableParalelism()

        EvalRoutine evalRoutine = EvalRoutine.create(params.predict_residues, dataset, model, outdir)
        EvalResults res = evalRoutine.execute()

        finalizeDatasetResult(res.datasetResult)
    }

    private runCrossvalidation() {
        Dataset dataset = loadDataset()
        String outdir = findOutdir("crossval_" + dataset.label)

        configureLoggers(outdir)

        CrossValidation routine = new CrossValidation(outdir, dataset)
        new SeedLoop(routine, outdir).execute()
    }

    private runExperiment(String routineName) {
        new Experiments(args, this, routineName).execute()
    }

    private runAnalyze() {
        new AnalyzeRoutine(args, this).execute()
    }

    private runTransform() {
        new TransformRoutine(args, this).execute()
    }

    private runPrint() {
        new PrintRoutine(args, this).execute()
    }

    void runHelp() {
        println Futils.readResource('/help.txt')
    }

    void initRescoreDefaultParams() {
        initParams(params, "$installDir/config/default_rescore.groovy")
    }

    /**
     * @return false if successful, true it there was some (recoverable) error during execution
     */
    boolean run() {

        if (args.unnamedArgs.empty) {
            throw new PrankException("No command specified. See the usage information by running 'prank help'")
        }

        command = args.unnamedArgs.first()
        args.shiftUnnamedArgs()

        installDir = findInstallDir()
        params.installDir = installDir // TODO refactor

        if (command in ["ploop", "hopt"]) {
            args.hasListParams = true
        }

        if (command=='help' || args.hasSwitch('h', 'help')) {
            runHelp()
            return true
        }

        initParams(params, "$installDir/config/default.groovy")


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
            case 'eval':          runEval()
                break
            case 'analyze':       runAnalyze()
                break
            case 'transform':     runTransform()
                break
            case 'print':         runPrint()
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

        // force proper decimal formatting (. as separator) in printf
        Locale.setDefault(new Locale("en", "US"))

        CmdLineArgs parsedArgs = CmdLineArgs.parse(args)

        if (parsedArgs.hasSwitch("v", "version")) {
            write "$versionName"
            return
        }

        write "$versionName"
        write ""

        boolean error = false

        Main main
        try {

            main = new Main(parsedArgs)
            error = main.run()

            if (P2Rank.isShuttingDown()) {
                error = true
            }

        } catch (Throwable e) {

            error = true

            if (e instanceof PrankException) {
                log.error(e.message, e)
                writeError e.message, null  // don't print stacktrace to stdout
            } else {
                writeError e.message, e       // on unknown exception also print stack trace
                writeError e.message, null // print just error message again at the end for readability
            }

            if (main!=null) {
                //String logLocation = "$main.installDir/log/prank.log"
                if (main.logManager.loggingToFile) {
                    String logLocation = main.logManager.logFile
                    write "For details see log file: $logLocation"
                }
            }

        }

        write ""
        write "Finished ${error?"with ERROR":"successfully"} in ${timer.formatted}."

        if (error) {
            System.exit(1)
        }

    }

}
