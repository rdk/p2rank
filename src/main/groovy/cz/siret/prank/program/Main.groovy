package cz.siret.prank.program

import cz.siret.prank.domain.AminoAcidMapper
import cz.siret.prank.domain.CofactorHandler
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.implementation.conservation.provider.ConservationProviderFactory
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.PreloadConservationRoutine
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.analyze.AnalyzeRoutine
import cz.siret.prank.program.routines.analyze.PrintRoutine
import cz.siret.prank.program.routines.analyze.TransformRoutine
import cz.siret.prank.program.routines.benchmark.Benchmarks
import cz.siret.prank.program.routines.predict.ExportPointsRoutine
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

        String lastConfigDir = Futils.dir(lastConfigPath)
        params.dataset_base_dir = evalDirParam(params.dataset_base_dir, lastConfigDir)
        params.output_base_dir = evalDirParam(params.output_base_dir, lastConfigDir)

        params.updateFromCommandLine(args)
        if (args.hasNamedArg("dataset_base_dir")) {
            params.dataset_base_dir = evalDirParam(params.dataset_base_dir, ".")
        }
        if (args.hasNamedArg("output_base_dir")) {
            params.output_base_dir = evalDirParam(params.output_base_dir, ".")
        }

        String mod = args.get('m')
        if (mod != null) {
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

        // Initialize amino acid mapper based on aa_mapping parameter
        AminoAcidMapper.initialize(params.aa_mapping)

        // Parse and validate cofactor specifiers (Issue #79 part 2).
        // parseAndValidate throws PrankException on malformed input via LigandDefinition.parse().
        // The result is discarded here - re-parsing happens per-item in
        // Dataset.resolveCofactorDefinitions so dataset-column overrides go through the
        // same validation.
        if (params.cofactors != null && !params.cofactors.isEmpty()) {
            CofactorHandler.parseAndValidate(params.cofactors)
            log.info "Cofactors to include as protein surface: {}", params.cofactors

            // R19: warn if a cofactor specifier's group name is also in the active aa_mapping.
            // Cofactor atoms would silently inherit the mapped AA's features rather than
            // cofactor defaults - defined behaviour, but invisible without a warning.
            Set<String> activeMappings = AminoAcidMapper.getInstance().getMappings().keySet()
            Set<String> overlapping = new LinkedHashSet<>()
            for (String spec : params.cofactors) {
                String trimmed = spec?.trim()
                if (trimmed == null || trimmed.isEmpty()) continue
                try {
                    String name = Dataset.LigandDefinition.parse(trimmed).groupName?.toUpperCase()
                    if (name != null && activeMappings.contains(name)) overlapping.add(name)
                } catch (Exception ignored) {
                    // parse error already surfaced by parseAndValidate above
                }
            }
            if (!overlapping.isEmpty()) {
                log.warn "Cofactor specifier(s) name(s) {} are also covered by the active " +
                        "aa_mapping. Cofactor atom features will be computed using the mapped " +
                        "AA's table entries instead of cofactor defaults. Remove the entry " +
                        "from aa_mapping or change the cofactor specifier to fix.", overlapping
            }
        }

        validateVisParams()
        validatePocketGridParams()
        validatePocketFilterParams()
    }

    /**
     * Fail-fast validation for visualization params. Caught at startup so a typo
     * (e.g. -vis_renderers pmol) doesn't silently produce no output after a
     * full prediction run — the consumption sites check `'pymol' in vis_renderers`
     * by strict membership, so an unknown name is a quiet no-op.
     */
    private void validateVisParams() {
        Set<String> knownRenderers = ['pymol', 'chimerax'] as Set
        List<String> rs = params.vis_renderers
        if (rs == null) {
            throw new PrankException("-vis_renderers is null. Expected a list of: ${knownRenderers.sort()}")
        }
        Set<String> seen = new HashSet<>()
        for (String r : rs) {
            if (r == null || r.trim().isEmpty()) {
                throw new PrankException(
                        "-vis_renderers contains an empty/null entry. Known: ${knownRenderers.sort()}")
            }
            if (!knownRenderers.contains(r)) {
                throw new PrankException(
                        "Unknown renderer in -vis_renderers: '${r}'. Known: ${knownRenderers.sort()}")
            }
            if (!seen.add(r)) {
                throw new PrankException(
                        "-vis_renderers contains duplicate '${r}'.")
            }
        }
    }

    /** Fail-fast validation for the pocket-grid export feature. */
    private void validatePocketGridParams() {
        // pocket_grid_format must be one of the values supported by TableExporter.
        Set<String> allowedFormats = ['csv', 'csv.gz', 'csv.zst',
                                       'arrow', 'arrow.gz', 'arrow.zst',
                                       'parquet'] as Set
        if (!allowedFormats.contains(params.pocket_grid_format)) {
            throw new PrankException(
                    "Invalid -pocket_grid_format '${params.pocket_grid_format}'. " +
                    "Expected one of: ${allowedFormats.sort()}")
        }

        // pocket_grid_fill must be a name registered in PocketShapeFillerRegistry.
        Set<String> knownFills = cz.siret.prank.program.routines.predict.output.grid.fill.PocketShapeFillerRegistry.knownNames()
        if (!knownFills.contains(params.pocket_grid_fill)) {
            throw new PrankException(
                    "Invalid -pocket_grid_fill '${params.pocket_grid_fill}'. " +
                    "Known: ${knownFills}")
        }

        // pocket_grid_assigner must be a name registered in PocketAssignerRegistry.
        Set<String> knownAssigners = cz.siret.prank.program.routines.predict.output.grid.assign.PocketAssignerRegistry.knownNames()
        if (!knownAssigners.contains(params.pocket_grid_assigner)) {
            throw new PrankException(
                    "Invalid -pocket_grid_assigner '${params.pocket_grid_assigner}'. " +
                    "Known: ${knownAssigners}")
        }

        cz.siret.prank.program.routines.predict.output.DescriptorListValidator.validate(
                params.pocket_descriptors,
                cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptorRegistry.knownNames(),
                "pocket_descriptors")

        // Numeric ranges: catch values that would silently produce a broken/empty grid
        // (≤0 lattice edge → NaN lattice; ≤0 distance bounds → empty grid) or that are
        // outside the algorithm's defined domain (26-neighborhood for morph closing).
        if (params.pocket_grid_spacing <= 0d) {
            throw new PrankException(
                    "-pocket_grid_spacing must be > 0 (got ${params.pocket_grid_spacing}).")
        }
        if (params.pocket_grid_max_dist <= 0d) {
            throw new PrankException(
                    "-pocket_grid_max_dist must be > 0 (got ${params.pocket_grid_max_dist}).")
        }
        if (params.pocket_grid_atom_buffer < 0d) {
            throw new PrankException(
                    "-pocket_grid_atom_buffer must be ≥ 0 (got ${params.pocket_grid_atom_buffer}).")
        }
        if (params.pocket_grid_assign_cutoff <= 0d) {
            throw new PrankException(
                    "-pocket_grid_assign_cutoff must be > 0 (got ${params.pocket_grid_assign_cutoff}).")
        }
        if (params.pocket_grid_fill_min_neighbors < 1 || params.pocket_grid_fill_min_neighbors > 26) {
            throw new PrankException(
                    "-pocket_grid_fill_min_neighbors must be in [1, 26] " +
                    "(26-neighborhood lattice; got ${params.pocket_grid_fill_min_neighbors}).")
        }
        if (params.pocket_grid_fill_max_iters < 0) {
            throw new PrankException(
                    "-pocket_grid_fill_max_iters must be ≥ 0 (got ${params.pocket_grid_fill_max_iters}).")
        }
        if (params.pocket_grid_fill_close_radius < 0) {
            throw new PrankException(
                    "-pocket_grid_fill_close_radius must be ≥ 0 (got ${params.pocket_grid_fill_close_radius}).")
        }
        // -1 is the auto-scale sentinel; any other non-positive value would silently
        // disable the surface (radius 0) or pass garbage to PyMOL/ChimeraX (negative vdw).
        if (params.vis_pocket_grid_volume_radius != -1d && params.vis_pocket_grid_volume_radius <= 0d) {
            throw new PrankException(
                    "-vis_pocket_grid_volume_radius must be -1 (auto-scale) or > 0 " +
                    "(got ${params.vis_pocket_grid_volume_radius}).")
        }
        if (params.vis_pocket_grid_gaussian_iso <= 0d) {
            throw new PrankException(
                    "-vis_pocket_grid_gaussian_iso must be > 0 (got ${params.vis_pocket_grid_gaussian_iso}).")
        }

        cz.siret.prank.program.routines.predict.output.DescriptorListValidator.validate(
                params.pocket_grid_point_descriptors,
                cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptorRegistry.knownNames(),
                "pocket_grid_point_descriptors")
        if (params.pocket_grid_volsite_radius <= 0d) {
            throw new PrankException(
                    "-pocket_grid_volsite_radius must be > 0 (got ${params.pocket_grid_volsite_radius}).")
        }
        if (params.pocket_grid_volsite_sigma <= 0d) {
            throw new PrankException(
                    "-pocket_grid_volsite_sigma must be > 0 (got ${params.pocket_grid_volsite_sigma}).")
        }

        // Grid viz depends on the grid export being enabled.
        if (params.vis_pocket_grid && !params.export_pocket_grid) {
            throw new PrankException(
                    "-vis_pocket_grid=true requires -export_pocket_grid=true " +
                    "(the grid renderers derive their PDB sidecar from the grid).")
        }
    }

    /** Fail-fast validation for pocket output filter params. */
    private void validatePocketFilterParams() {
        if (params.pred_max_pockets < 0) {
            throw new PrankException(
                    "-pred_max_pockets must be >= 0 (got ${params.pred_max_pockets}).")
        }
        if (params.pred_min_pockets < 0) {
            throw new PrankException(
                    "-pred_min_pockets must be >= 0 (got ${params.pred_min_pockets}).")
        }
        double p = params.pred_min_pocket_probability
        if (!Double.isNaN(p) && (p < 0d || p > 1d)) {
            throw new PrankException(
                    "-pred_min_pocket_probability must be NaN (disabled) or in [0, 1] (got ${p}).")
        }
    }

    String evalDirParam(String dirParam, String relativePrefixDir) {
        if (dirParam == null) {
            dirParam = "."
        } else {
            if (!Futils.isAbsolute(dirParam)) {
                dirParam = "$relativePrefixDir/$dirParam"
            }
        }

        //write "DIR: $dirParam"

        dirParam = dirParam.replace("{version}", version)

        //write "DIR2: $dirParam"

        dirParam = Futils.absPath(Futils.normalize(dirParam))
        return dirParam
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

        if (Futils.isAbsolute(dataf)) {
            log.info "using provided absolute path to the dataset [${dataf}]"
            return dataf
        }

        if (!Futils.exists(dataf)) {
            log.info "looking for the dataset in working dir [${Futils.absPath(dataf)}] failed"
            dataf = "${Params.inst.dataset_base_dir}/$dataf"
            log.info "looking for the dataset in dataset_base_dir [${Futils.absPath(dataf)}]..."
        }
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

    static String findInstallDir() {
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
        checkCommandConfig()
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("${label}_$dataset.label")
        configureLoggers(outdir)
        ConservationProviderFactory.checkProviderHealthIfConfigured()

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
        finalizeDatasetResult(result, outdir)
    }

    void finalizeDatasetResult(Dataset.Result result, String outdir) {
        if (result.hasErrors()) {
            error = true
            write "ERROR on processing $result.errorCount file(s):"

            for (def itemError : result.errorItems) {
                write "    [$itemError.item.label]"
            }

            result.writeErrorCsvs(outdir)
            write "See error details in: $outdir/errors.csv, $outdir/errors_full.txt.gz"
        }
    }

//===========================================================================================================//

    void runPredict() {
        doRunPredict("predict", false)
    }

    void runExportPoints() {
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("export_points_$dataset.label")
        configureLoggers(outdir)

        Dataset.Result result = new ExportPointsRoutine(dataset, outdir).execute()
        finalizeDatasetResult(result, outdir)
    }

    void runEvalPredict() {
        doRunPredict("eval_predict", true)
    }

    void runRescore() {
        initRescoreDefaultParams()
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("rescore_$dataset.label")
        configureLoggers(outdir)
        ConservationProviderFactory.checkProviderHealthIfConfigured()

        Dataset.Result result = new RescorePocketsRoutine(
                dataset,
                findModel(),
                outdir,
                params.run_fpocket_ad_hoc).execute()

        finalizeDatasetResult(result, outdir)
    }

    void runFpocketRescore() {
        initRescoreDefaultParams()
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("fpocket_rescore_$dataset.label")
        configureLoggers(outdir)

        Dataset.Result result = new RescorePocketsRoutine(
                dataset,
                findModel(),
                outdir, true).execute()

        finalizeDatasetResult(result, outdir)
    }

    void runEvalRescore() {
        initRescoreDefaultParams()
        Dataset dataset = loadDataset()
        String outdir = findOutdir("eval_rescore_$dataset.label")
        configureLoggers(outdir)
        ConservationProviderFactory.checkProviderHealthIfConfigured()

        new EvalPocketsRoutine(
                dataset,
                Model.load(findModel()),
                outdir,
                params.run_fpocket_ad_hoc).execute()

    }

    void runEval() {
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("eval_$dataset.label")
        configureLoggers(outdir)
        ConservationProviderFactory.checkProviderHealthIfConfigured()

        Model model = Model.load(findModel())

        EvalRoutine evalRoutine = EvalRoutine.create(params.predict_residues, dataset, model, outdir)
        EvalResults res = evalRoutine.execute()

        finalizeDatasetResult(res.datasetResult, outdir)
    }

    private runCrossvalidation() {
        Dataset dataset = loadDataset()
        String outdir = findOutdir("crossval_" + dataset.label)

        configureLoggers(outdir)
        ConservationProviderFactory.checkProviderHealthIfConfigured()

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

    private runBenchmark() {
        new Benchmarks(args, this).execute()
    }

    void runPreloadConservation() {
        Dataset dataset = loadDatasetOrFile()
        String outdir = findOutdir("preload_conservation_$dataset.label")
        configureLoggers(outdir)
        ConservationProviderFactory.checkProviderHealthIfConfigured()

        new PreloadConservationRoutine(dataset).execute()
    }

    void runHelp() {
        println Futils.readResource('/help.txt')
    }

    void initRescoreDefaultParams() {
        initParams(params, "$installDir/config/default_rescore.groovy")
        checkCommandConfig()
    }

    /**
     * Reject a command run with a config of the wrong purpose (e.g. `rescore -c alphafold`).
     * Must be called after the command's params are fully resolved (rescore commands re-base
     * on default_rescore inside initRescoreDefaultParams). See issue #73.
     */
    private void checkCommandConfig() {
        CommandConfigCompatibility.check(command, params.config_purpose, configFileParam, params.fail_on_wrong_config)
    }

    /**
     * @return false if successful, true if there was some (recoverable) error during execution
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
            case 'predict':         runPredict()
                break
            case 'export-points':   runExportPoints()
                break
            case 'eval-predict':    runEvalPredict()
                break
            case 'rescore':         runRescore()
                break
            case 'fpocket-rescore': runFpocketRescore()
                break
            case 'eval-rescore':    runEvalRescore()
                break
            case 'crossval':        runCrossvalidation()
                break
            case 'eval':            runEval()
                break
            case 'analyze':         runAnalyze()
                break
            case 'transform':       runTransform()
                break
            case 'print':           runPrint()
                break
            case 'bench':           runBenchmark()
                break
            case 'preload-conservation': runPreloadConservation()
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

    private static cmdVersion() {
        System.out.println getVersionName()
        System.out.println ""
        System.out.println "Home: " + findInstallDir()
        System.out.println "JVM: " + SysUtils.getJavaRuntimeNameVersionVendor()
        System.out.println "OS: " + SysUtils.getOsInfo()
        System.out.println "CPUs: " + SysUtils.getAvailableProcessors()
        System.out.println "Max Memory: ${SysUtils.getMaxMemoryGB()} GiB"
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
            cmdVersion()
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
