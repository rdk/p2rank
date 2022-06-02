package cz.siret.prank.program.params

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

@Slf4j
@CompileStatic
class ConfigLoader {

    static overrideConfig(Params ps, File paramsGroovyFile) {

        log.debug("Overriding default config with [$paramsGroovyFile.path]")

        assert paramsGroovyFile.exists(), "config file not found! ($paramsGroovyFile.path)"

        def imports = new ImportCustomizer()
        imports.addImport(Params.class.simpleName, Params.class.name)

        def customizer = new CompilerConfiguration()
        customizer.addCompilationCustomizers(imports)

        Binding binding = new Binding([
                params : ps
        ])

        GroovyShell shell = new GroovyShell(binding, customizer)

        try {
            shell.evaluate(paramsGroovyFile)
        } catch (Exception e) {
            throw new PrankException("Error in the config file [$paramsGroovyFile.path]: " + e.message, e)
        }

        return true
    }

}
