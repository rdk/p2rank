package cz.siret.prank.domain.loaders.electrostatics


import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals


/**
 *
 */
@Slf4j
@CompileStatic
class DelphiCubeLoaderTest {

    static String dir = 'src/test/resources/data/electrostatics/delphi'

    static GaussianCube cube

    @BeforeAll
    static void loadCube() {
       cube = DelphiCubeLoader.loadFile("$dir/delphi-2src.cube.gz")
    }

    @Test
    void testLoadFromFileGz() {
        test2srcCube(cube)
    }

    void test2srcCube(GaussianCube cube) {
        assertEquals 205, cube.sizeX
        assertEquals 205, cube.sizeY
        assertEquals 205, cube.sizeZ
        assertEquals(-3.61173e+01f, cube.data[cube.sizeX-1][cube.sizeY-1][cube.sizeZ-1], 0f)
    }

    @Test
    void testSerializeDeserialize() {
        Futils.mkdirs"$dir/tmp"
        def fname = "$dir/tmp/delphi-2src.jser"
        Futils.serializeToFile(fname, cube)
        cube = Futils.deserializeFromFile(fname)
        test2srcCube(cube)
        Futils.delete "$dir/tmp"
    }

}
