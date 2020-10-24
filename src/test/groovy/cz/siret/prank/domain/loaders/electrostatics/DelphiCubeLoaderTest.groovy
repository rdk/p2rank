package cz.siret.prank.domain.loaders.electrostatics

import cz.siret.prank.utils.Bench
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test

import java.util.zip.Deflater

import static cz.siret.prank.utils.Bench.timeit
import static cz.siret.prank.utils.Futils.deserializeFromFile
import static cz.siret.prank.utils.Futils.serializeToFile
import static cz.siret.prank.utils.Futils.serializeToGzip
import static cz.siret.prank.utils.Futils.serializeToLzma
import static cz.siret.prank.utils.Futils.serializeToZstd
import static org.junit.Assert.assertEquals

/**
 *
 */
@Slf4j
@CompileStatic
class DelphiCubeLoaderTest {

    static String dir = 'src/test/resources/data/electrostatics/delphi'

    GaussianCube cube

    @Before
    void loadCube() {
       // cube = DelphiCubeLoader.loadFromFile("$dir/delphi-2src.cube.gz")
    }

    //@Test
    //void testLoadFromFileGz() {
    //    test2srcCube(cube)
    //}
    //
    //void test2srcCube(GaussianCube cube) {
    //    assertEquals 205, cube.sizeX
    //    assertEquals 205, cube.sizeY
    //    assertEquals 205, cube.sizeZ
    //}

//    @Test
//    void testSerializeCubeBzip2() {
//        def fname = "$dir/delphi-2src.jser.bz2"
//        Futils.serializeToBzip2(fname, cube)
//        cube = Futils.deserializeFromFile(fname)
//        test2srcCube(cube)
//    }
//
//
//    @Test
//    void testSerializeCubeGz() {
//        def fname = "$dir/delphi-2src.jser.gz"
//        Futils.serializeToGzip(fname, cube, Deflater.BEST_COMPRESSION)
//        cube = Futils.deserializeFromFile(fname)
//        test2srcCube(cube)
//    }
//
//    @Test
//    void testSerializeCubeLzma() {
//        def fname = "$dir/delphi-2src.jser.lzma"
//        Futils.serializeToLzma(fname, cube, 9)
//        cube = Futils.deserializeFromFile(fname)
//        test2srcCube(cube)
//    }

    @Test
    void testSerializeBig() {
        def fname = "$dir/delphi-6PW2.cube"

        GaussianCube cube

        timeit("loading cube", 1, {
            cube = DelphiCubeLoader.loadFromFile(fname)
        })

        timeit("saving cube", 1, {
            serializeToFile(fname+".jser", cube)
        })

        timeit("saving cube", 1, {
            serializeToGzip(fname+".jser", cube, 1)
        })

        cube = null

        timeit("deserializing cube", 1, {
            cube = deserializeFromFile(fname+".jser")
        })
    }

    @Test
    void testBench() {
        def fname = "$dir/delphi-6PW2.cube"

        GaussianCube cube

        int n = 1

        timeit("loading from text",    n, { cube = DelphiCubeLoader.loadFromFile(fname     )      })
        //timeit("loading from gz text", n, { cube = DelphiCubeLoader.loadFromFile(fname+".gz")      })

        timeit("saving to ser",     n, { serializeToFile("${fname}.jser", cube)      })
        timeit("loading from ser",  n, { cube = deserializeFromFile("${fname}.jser")     })

        //timeit("saving to gz",      n, { serializeToGzip(fname+".jser.gz", cube, 6)    })
        //timeit("loading from gz",   n, { cube = deserializeFromFile(fname+".jser.gz")   })
        //
        //timeit("saving to lzma",    n, { serializeToLzma(fname+".jser.lzma", cube, 6)    })
        //timeit("loading from lzma", n, { cube = deserializeFromFile(fname+".jser.lzma")   })

        timeit("saving to zstd",    n, { serializeToZstd(fname+".jser.zstd", cube, 4)    })
        timeit("loading from zstd", n, { cube = deserializeFromFile(fname+".jser.zstd")   })

        //Futils.serializeToFile(fname+".jser", cube)
        //cube = Futils.deserializeFromFile(fname)
        //test2srcCube(cube)
    }
//
//    @Test
//    void testBenchBig2() {
//        def fname = "$dir/delphi-2src.cube"
//
//        GaussianCube cube
//
//        int n = 3
//
//        timeit("loading from text",    n, { cube = DelphiCubeLoader.loadFromFile(fname     )      })
//        timeit("loading from gz text", n, { cube = DelphiCubeLoader.loadFromFile(fname+".gz")      })
//
//        timeit("saving to ser",     n, { serializeToFile("${fname}.jser", cube)      })
//        timeit("loading from ser",  n, { cube = deserializeFromFile("${fname}.jser")     })
//
//        timeit("saving to gz",      n, { serializeToGzip(fname+".jser.gz", cube, 6)    })
//        timeit("loading from gz",   n, { cube = deserializeFromFile(fname+".jser.gz")   })
//
//        timeit("saving to lzma",    n, { serializeToLzma(fname+".jser.lzma", cube, 6)    })
//        timeit("loading from lzma", n, { cube = deserializeFromFile(fname+".jser.lzma")   })
//
//        timeit("saving to zstd",    n, { serializeToZstd(fname+".jser.zstd", cube, 6)    })
//        timeit("loading from zstd", n, { cube = deserializeFromFile(fname+".jser.zstd")   })
//
//        //Futils.serializeToFile(fname+".jser", cube)
//        //cube = Futils.deserializeFromFile(fname)
//        //test2srcCube(cube)
//    }


//    @Test
//    void benchmark() {
//        int n = 10
//        for
//    }

}
