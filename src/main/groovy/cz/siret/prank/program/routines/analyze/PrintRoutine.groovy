package cz.siret.prank.program.routines.analyze

import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureSetup
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Bench.timeit
import static java.util.Collections.unmodifiableMap

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

    final Map<String, Closure> commandRegister = unmodifiableMap([
            "features" : { features() },
            "feature-sets" : { feature_sets() },
            "model-info" : { model_info() },
            "params" :    { params() },
            "transform-model" : { transform_model() }     // temporary
    ])

//===========================================================================================================//

    void features() {
        PrankFeatureExtractor fe = (PrankFeatureExtractor) FeatureExtractor.createFactory()
        FeatureSetup featureSetup = fe.featureSetup
        List<String> subFeatureHeader = fe.vectorHeader
        List<FeatureSetup.Feature> enabledFeatures = featureSetup.enabledFeatures
        boolean filtering = featureSetup.filteringEnabled

        write "Effectively enabled features" + (filtering?" (after filtering)":"") + ":"
        write ""
        write ((enabledFeatures*.name).join("\n"))
        write ""
        write "Effective feature vector header (i.e. enabled sub-features):"
        write ""
        write subFeatureHeader.withIndex().collect { name, i -> sprintf("%2d: %s", i, name) }.join("\n")
    }

    void feature_sets() {
        PrankFeatureExtractor fe = (PrankFeatureExtractor) FeatureExtractor.createFactory()
        FeatureSetup featureSetup = fe.featureSetup
        List<FeatureSetup.Feature> enabledFeatures = featureSetup.enabledFeatures
        boolean filtering = featureSetup.filteringEnabled

        write "Effectively enabled features" + (filtering?" (after filtering)":"") + ":"
        write ""
        write ((enabledFeatures*.name).join("\n"))

        write "\nn = ${enabledFeatures.size()}"
    }

    void model_info() {
        String modelf = main.findModel()
        Model model = null

        long loadingTime = timeit {
            model = Model.loadFromFile(modelf)
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


    void transform_model() {
        String modelf = main.findModel()
        Model model = Model.loadFromFile(modelf)

        def newf = modelf+".model2.zst"
        Futils.serializeToZstd(newf, model.classifier, 3)

        Model.loadFromFile(newf)
        
        write "Transformed model saved to file ${Futils.absPath(newf)}"
    }

    /**
     *  print parameters and exit
     */
    public params() {
        write args.toString()
        write params.toString()
    }

}
