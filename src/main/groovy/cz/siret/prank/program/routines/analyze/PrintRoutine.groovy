package cz.siret.prank.program.routines.analyze

import com.google.common.collect.ImmutableMap
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.Bench
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Bench.timeit

/**
 * Various tools that print some information to stdout.
 * Routine with sub-commands.
 */
@Slf4j
@CompileStatic
class PrintRoutine extends Routine {

    Main main
    CmdLineArgs args

    PrintRoutine(CmdLineArgs args, Main main) {
        super(null)

        this.main = main
        this.args = args
    }

    void execute() {

        String subCommand = args.unnamedArgs[0]
        if (!commandRegister.containsKey(subCommand)) {
            write "Invalid print sub-command '$subCommand'! Available commands: "+commandRegister.keySet()
            throw new PrankException("Invalid command.")
        }

        log.info "executing print $subCommand command"

        commandRegister.get(subCommand).call()

    }

//===========================================================================================================//
// Sub-Commands
//===========================================================================================================//

    final Map<String, Closure> commandRegister = ImmutableMap.copyOf([
            "features" : this.&features,
            //"feature-sets" : this.&feature_sets,
            "model-info" : this.&model_info
    ])

//===========================================================================================================//

    void features() {
        def features = FeatureExtractor.createFactory().vectorHeader

        write "List of individual features"
        write ""
        write features.join("\n")
        write ""
        write "n = ${features.size()}"
    }

//    void feature_sets() {
//        def features = FeatureExtractor.createFactory().extraFeaturesHeader
//
//        write features.join("\n")
//        write ""
//        write "n = ${features.size()}"
//    }

    void model_info() {
        String modelf = main.findModel()
        Model model = null

        long loadingTime = timeit {
            model = Model.loadFromFile(modelf)
        }

        if (!model) {
            throw new PrankException("Model not found.")
        }

        Model.Info info = model.info

        write "Model Info"
        write ""
        write "File: ${Futils.absPath(modelf)}"
        write "Class: $model.classifier.class.simpleName ($model.classifier.class.name)"
        write "Size: ${Futils.sizeMBFormatted(modelf)} MB"
        write "Loading time: ${Formatter.formatSeconds(loadingTime)} s"
        write ""
        write "Num. features: $info.numFeatures"
        if (info.isForest) {
            write "Num. trees: $info.numTrees"
            write "Max depth: $info.maxDepth"
        }
    }

}
