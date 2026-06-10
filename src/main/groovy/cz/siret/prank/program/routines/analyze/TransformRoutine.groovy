package cz.siret.prank.program.routines.analyze


import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.AA
import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.implementation.table.AAIndex1
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.FlattenComparison
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.ml.ModelConverter
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Structure

import javax.annotation.Nullable

import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Various tools for analyzing datasets.
 * Routine with sub-commands.
 */
@Slf4j
@CompileStatic
class TransformRoutine extends Routine {

    CmdLineArgs args
    Main main

    String subCommand
    String label
    @Nullable Dataset dataset

    TransformRoutine(CmdLineArgs args, Main main) {
        super(null)

        this.args = args
        this.main = main

        subCommand = args.popFirstUnnamedArg() // next if present should be dataset
        if (!commandRegister.containsKey(subCommand)) {
            write "Invalid transform sub-command '$subCommand'! Available commands: " + commandRegister.keySet()
            throw new PrankException("Invalid command.")
        }

        if (!args.unnamedArgs.empty || args.get('f') != null) {
            dataset = main.loadDatasetOrFile()
        }

        label = "transform_" + subCommand + (dataset!=null ? "_"+dataset.label : "")
        outdir = main.findOutdir(label)
        main.configureLoggers(outdir)
    }

    void execute() {
        write "executing transform $subCommand command"

        commandRegister.get(subCommand).call()
    }

 //===========================================================================================================//
 // Sub-Commands
 //===========================================================================================================//

    final Map<String, Closure> commandRegister = ImmutableMap.copyOf([
        "reduce-to-chains" : { cmdReduceToChains() },
        "aaindex1-to-csv" : { cmdAAIndex1ToCsv() },
        "flatten-rf-model" : { cmdFlattenRfModel() },
        "model-to-v3-format" : { cmdModelToV3Format() },
        "loop-flatten-rf-model" : { cmdLoopFlattenRfModel() },
        "bench-flatten-optimizers" : { cmdBenchFlattenOptimizers() },
        "compare-flatten-eval" : { cmdCompareFlattenEval() }
    ])

//===========================================================================================================//

    /**
     * chain label = "<author_id>(<mmcif_id>)"
     */
    private List<String> chainLabels(Structure structure) {
        return structure.chains.collect { Struct.getAuthorId(it) + "(" + Struct.getMmcifId(it) + ")" }
    }

    private void cmdReduceToChains() {
        String file = args.get("f")
        String outFormatParam = params.out_format
        String outFileParam = params.out_file
        String chainsParam = params.chains

        def validVals = ["keep", "pdb", "pdb.gz", "cif", "cif.gz"]
        if (!(outFormatParam in validVals)) {
            throw new PrankException("Invalid value of out_format param: '$outFormatParam'. Valid values: $validVals")
        }

        write "processing file [${Futils.absPath(file)}]"

        Structure structure = PdbUtils.loadFromFile(file)
        String baseFileName = Futils.baseName(file)
        String outFileBaseName // without extension

        List<String> schains = structure.chains.collect { Struct.getAuthorId(it) }.toUnique().toSorted()
        write "chains: " + chainLabels(structure)
        write "atoms: " + Atoms.allFromStructure(structure).count

        if (chainsParam == "keep") {
            write "keeping the structure as is / not reducing to chains"
            outFileBaseName = baseFileName
        } else {
            List<String> newChains
            if (chainsParam == "all") {
                write "selecting all the chains"
                newChains = schains
                outFileBaseName = baseFileName + "_all"
            } else {
                newChains = Sutils.split(chainsParam, ",")
                outFileBaseName = baseFileName + "_" + newChains.join(",")
            }

            write "reducing to chains: " + newChains

            structure = PdbUtils.reduceStructureToChains(structure, newChains)
            write "chains (after reduction): " + chainLabels(structure)
            write "atoms (after reduction): " + Atoms.allFromStructure(structure).count
        }

        boolean compress = false
        String outFormat = "pdb"
        String outExt
        if (outFormatParam == "keep") {
            compress = Futils.isCompressed(file)
            outFormat = Futils.realExtension(file)
            outExt = Futils.realExtension(file) + ((compress) ? ".gz" : "")
            if (outFormat == "ent") {
                outFormat = "pdb"
            }
        } else {
            compress = Futils.isCompressed(outFormatParam)
            outFormat = Sutils.removeSuffix(outFormatParam, ".gz")
            outExt = outFormat + ((compress) ? ".gz" : "")
        }

        String outFilePath
        if (outFileParam != null) {
            outFilePath = outFileParam
        } else {
            mkdirs(outdir)
            writeParams(outdir)
            String outFileName = outFileBaseName + "." + outExt
            outFilePath = outdir + "/" + outFileName
        }

        write "Output file: " + Futils.absPath(outFilePath)

        PdbUtils.saveToFile(structure, outFormat, outFilePath, compress)
    }

//===========================================================================================================//

    private void cmdAAIndex1ToCsv() {
        mkdirs(outdir)
        writeParams(outdir)
        String file = args.get("f")

        AAIndex1 aaindex = AAIndex1.parse(Futils.readFile(file))

        StringBuilder csv = new StringBuilder()
        csv << "indexId," + AA.values().join(",") + "\n"

        for (AAIndex1.Entry entry : aaindex.entries) {
            csv << entry.id + "," + AA.values().collect{ entry.values.get(it) }.join(",") + "\n"
        }

        String outFilePath = outdir + "/aaindex1.csv"
        write "Output file: " + Futils.absPath(outFilePath)
        writeFile outFilePath, csv.toString()
    }

//===========================================================================================================//

    void cmdModelToV3Format() {
        mkdirs(outdir)
        writeParams(outdir)


        String modelFile = main.findModel()
        Model model = Model.loadFromFileOrDir(modelFile)
        model = new ModelConverter().applyConversions(model)

        write "Converting model $modelFile ($model.label) to v3 format"
        write "params.rf_flatten: $params.rf_flatten"
        write "params.rf_flatten_as_legacy: $params.rf_flatten_as_legacy"

        String newModelDir = outdir + '/' + Futils.baseName(modelFile)

        model.saveToDirectoryV3(newModelDir)

        write "Original model: $modelFile"
        write "Original size: " + Futils.sizeMBFormatted(modelFile) + " MB"
        write "New model: $newModelDir"
        write "New size: " + Futils.sizeMBFormatted("$newModelDir/model.zst") + " MB"

        write "Test loading new model"

        Model.loadFromDirectoryV3(newModelDir)

    }

    private void cmdFlattenRfModel() {
        mkdirs(outdir)
        writeParams(outdir)


        params.rf_flatten = true

        String modelFile = main.findModel()

        Model model = Model.loadFromFileOrDir(modelFile)
        model = new ModelConverter().applyConversions(model)

        String newModelFile = "$outdir/$model.label"

        model.saveToFile(newModelFile)

        write "Original model: $modelFile"
        write "Original size:" + Futils.sizeMBFormatted(modelFile)
        write "New model: $newModelFile"
        write "New size:" + Futils.sizeMBFormatted(newModelFile)

        
    }

    private void cmdLoopFlattenRfModel() {

        params.rf_flatten = true

        String modelFile = main.findModel()
        Model model = Model.loadFromFileOrDir(modelFile)

        while (true) {
            new ModelConverter().applyConversions(model)
        }
    }


    private void cmdBenchFlattenOptimizers() {
        // TODO
    }

    /**
     * G0 gate: run pocket prediction + eval with the (unflattened) model and with it re-flattened to each
     * faithful target, on the given dataset, and print a pocket-level comparison (DCA top-N + point AUC).
     *
     *   prank transform compare-flatten-eval -f <dataset.ds> -m <model>
     *       [-rf_flatten_target SoaLegacyFlatBinaryForest,Int16LeafSoaLegacyFlatBinaryForest]
     *
     * Targets are taken from rf_flatten_target (comma-separated) or default to the recommended faithful
     * pair. Stay within the faithful family — score targets shift the operating point (see Params doc).
     */
    private void cmdCompareFlattenEval() {
        mkdirs(outdir)
        writeParams(outdir)

        if (dataset == null) {
            throw new PrankException("compare-flatten-eval requires a liganated dataset passed as an " +
                    "UNNAMED arg, e.g.: prank transform compare-flatten-eval <dataset.ds>  (do NOT use -f, " +
                    "which loads a single structure file, not a .ds dataset)")
        }

        String modelFile = main.findModel()
        Model baseModel = Model.loadFromFileOrDir(modelFile)   // unflattened baseline (the shipped faithful default)

        // Targets: a comma-separated list via rf_flatten_target, else the recommended faithful pair.
        // (rf_flatten_target defaults to a single non-blank value, so only treat it as a list when it
        // actually contains a comma — otherwise compare against SoaLegacy + Int16LeafSoa.)
        String t = params.rf_flatten_target
        List<String> targets = (t != null && t.contains(",")) ?
                Sutils.split(t, ",").findAll { !it.isBlank() } : FlattenComparison.DEFAULT_FAITHFUL_TARGETS

        write "G0 compare-flatten-eval: base model [$baseModel.label] vs targets $targets on dataset [$dataset.name]"
        new FlattenComparison().compare(baseModel, dataset, targets, outdir)
    }


}
