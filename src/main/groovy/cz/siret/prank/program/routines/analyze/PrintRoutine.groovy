package cz.siret.prank.program.routines.analyze

import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.DatasetCachedLoader
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Various tools that print some information to stdout.
 * Routine with sub-commands.
 */
@Slf4j
@CompileStatic
class PrintRoutine extends Routine {

    PrintRoutine(CmdLineArgs args, Main main) {
        super(null)



    }

    void execute() {

//        String subCommand = args.unnamedArgs[0]
//        if (!commandRegister.containsKey(subCommand)) {
//            write "Invalid analyze sub-command '$subCommand'! Available commands: "+commandRegister.keySet()
//            throw new PrankException("Invalid command.")
//        }

//        write "executing analyze $subCommand command"

//        writeParams(outdir)
//        commandRegister.get(subCommand).call()

//        write "results saved to directory [${Futils.absPath(outdir)}]"
    }

//===========================================================================================================//
// Sub-Commands
//===========================================================================================================//

    static final Map<String, Closure> commandRegister = ImmutableMap.copyOf([
            "features" : this.&features,
    ])

//===========================================================================================================//
     void features() {

     }

}
