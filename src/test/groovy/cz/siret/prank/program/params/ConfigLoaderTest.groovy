package cz.siret.prank.program.params

import groovy.transform.CompileStatic;
import org.junit.Test

@CompileStatic
public class ConfigLoaderTest {

    @Test
    public void testOverride() throws Exception {
        File f = new File("./src/test/resources/test-params.groovy")
        Params p = new Params()
        ConfigLoader.overrideConfig(p, f)

        assert p.seed == 23
    }

    @Test
    public void testDefaultParams() throws Exception {
        File f = new File("./distro/config/default.groovy")
        Params p = new Params()
        ConfigLoader.overrideConfig(p, f)

        assert p.seed == 42
    }

}