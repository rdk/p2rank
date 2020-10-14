package cz.siret.prank.domain.loaders.electrostatics


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 *
 */
@Slf4j
@CompileStatic
class DelphiCubeLoaderTest {

    @Test
    void testLoadFromFile() {
        GaussianCube cube = DelphiCubeLoader.loadFromFile('src/test/resources/data/electrostatics/delphi/delphi-2src.cube')

        assertEquals 205, cube.nx
        assertEquals 205, cube.ny
        assertEquals 205, cube.nz

    }

    @Test
    void testLoadFromFileGz() {
        GaussianCube cube = DelphiCubeLoader.loadFromFile('src/test/resources/data/electrostatics/delphi/delphi-2src.cube.gz')

        assertEquals 205, cube.nx
        assertEquals 205, cube.ny
        assertEquals 205, cube.nz

    }

}
