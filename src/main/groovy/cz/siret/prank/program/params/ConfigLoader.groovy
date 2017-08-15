package cz.siret.prank.program.params

import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

@Slf4j
class ConfigLoader {

    static overrideConfig(Params ps, File paramsGroovyFile) {

        log.debug("Overriding default config with [$paramsGroovyFile.path]")

        assert paramsGroovyFile.exists(), "config file not fund! ($paramsGroovyFile.path)"

        def imports = new ImportCustomizer()
        imports.addImport(Params.class.simpleName, Params.class.name )

        def customizer = new CompilerConfiguration()
        customizer.addCompilationCustomizers(imports)

        Binding binding = new Binding([
                params : ps
        ])

        GroovyShell shell = new GroovyShell(binding, customizer)

        try {
            shell.evaluate(paramsGroovyFile)
        } catch (Exception e) {
            log.error("Error in config file [$paramsGroovyFile.path]", e)
            return false
        }

        return true
    }

}
