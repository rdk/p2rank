package cz.siret.prank.program.params.optimizer.bayesian


import cz.siret.prank.program.params.optimizer.HVariable
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.ProcessRunner
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.ProcessRunner.process

/**
 * Optimizer based on https://github.com/josejimenezluna/pyGPGO
 */
@Slf4j
@CompileStatic
class HPyGpgoOptimizer extends HExternalOptimizerBase {

    ProcessRunner pygpgoProcess

    HPyGpgoOptimizer(String experimentDir) {
        super(experimentDir)
    }

    @Override
    void start(String varsDir, String evalDir) {
        writeFile("$experimentDir/pygpgo.py", genEvalCode(variables))

        write "Starting pyGPGO python process"
        String cmd = "$params.hopt_python_command pygpgo.py"
        write "  executing '$cmd' in '$experimentDir'"
        pygpgoProcess = process(cmd, experimentDir).inheritIO()
        pygpgoProcess.execute()
    }

    @Override
    void finalizeAndCleanup() {
        pygpgoProcess.kill()
    }

//===========================================================================================================//

    private static final Map<HVariable.Type, String> TYPE_MAP = [
            (HVariable.Type.FLOAT) : 'cont',
            (HVariable.Type.INT) : 'int',
    ]

    private String genVariableDef(HVariable v) {
        String type = TYPE_MAP.get(v.type)
        return " '$v.name': ('$type', [$v.min, $v.max]) "
    }

    /**
     # {
     #     'x': ('cont', [0, 1]),
     #     'y': ('int', [0, 1])
     # }
     */
    private String genVariableDefs(List<HVariable> vars) {
        String vs = vars.collect { genVariableDef(it) }.join(",\n")
        return "{ $vs }"
    }

    private String genEvalCode(List<HVariable> vars) {
        String template = Futils.readResource("/hopt/pygpgo/pygpgo_template.py")

        String varDefs = genVariableDefs(vars)
        
        return Sutils.replaceEach(template, [
            '@@param.seed@@' : params.seed,
            '@@param.max_iters@@' : params.hopt_max_iterations,
            '@@param.constraints@@' : varDefs,
        ] as Map<String, Object>)
    }

}
