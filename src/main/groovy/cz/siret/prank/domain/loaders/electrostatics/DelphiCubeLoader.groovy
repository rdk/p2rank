package cz.siret.prank.domain.loaders.electrostatics

import com.google.common.base.CharMatcher
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.text.CharMatchers
import cz.siret.prank.utils.text.FastTokenizer2
import cz.siret.prank.utils.text.Tokenizer
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.Sutils.splitOnWhitespace

/**
 *
 */
@CompileStatic
class DelphiCubeLoader {

    private static int HEADER_SIZE = 7

    private static final CharMatcher WHITESPACE_MATCHER = CharMatchers.SPACE

    static GaussianCube loadFile(String fname) {
        return new DelphiCubeLoader().loadFromFile(fname)
    }


    /**
     * Load from *.cube or *.cube.gz file
     * @return
     */
    GaussianCube loadFromFile(String fname) {
        return loadFromStream(Futils.inputStream(fname))
    }

    GaussianCube loadFromStream(InputStream is) {
        Reader reader = new BufferedReader(new InputStreamReader(is), 1024*1024)

        try {
            List<String> header = loadHeader(reader)

            if (header.size() != HEADER_SIZE) {
                throw new PrankException("Cube file header is too short.")
            }

            GaussianCube cube = createFromHeader(header)
            cube.data = loadData(reader, cube.sizeX, cube.sizeY, cube.sizeZ)
            return cube
        } finally {
            reader.close()
        }
    }

    /**
     * Delphi cube file format.
     * 
     * <pre>
     * 0|   2.000000   205 20.279000 34.476500 65.844500                                   |
     * 1| Gaussian cube format phimap                                                      |
     * 2|     1    -58.054276    -31.224890     28.052039                                  |            , origin coordinates (x,y,z)
     * 3|   205      0.944863      0.000000      0.000000                                  | x grid size, x step
     * 4|   205      0.000000      0.944863      0.000000                                  | y grid size,       , y step
     * 5|   205      0.000000      0.000000      0.944863                                  | z grid size,               , z step
     * 6|     1      0.000000      0.000000      0.000000      0.000000                    |
     * 7|  -3.54102e+01 -3.55270e+01 -3.56440e+01 -3.57610e+01 -3.58780e+01 -3.59949e+01   | data ...
     *  ....
     * </pre>
     */
    private GaussianCube createFromHeader(List<String> header) {
        GaussianCube cube = new GaussianCube()

        List<List<String>> tokens = header.collect { splitOnWhitespace(it) }

        cube.header = header

        cube.sizeX = tokens[3][0].toInteger()
        cube.sizeY = tokens[4][0].toInteger()
        cube.sizeZ = tokens[5][0].toInteger()

        cube.deltaX = tokens[3][1].toDouble()
        cube.deltaY = tokens[4][2].toDouble()
        cube.deltaZ = tokens[5][3].toDouble()

        cube.originX = tokens[2][1].toDouble()
        cube.originY = tokens[2][2].toDouble()
        cube.originZ = tokens[2][3].toDouble()

        return cube
    }

    private List<String> loadHeader(Reader reader) {
        List<String> res = new ArrayList<>()
        for (int i=0; i!=HEADER_SIZE; i++) {
            res.add(reader.readLine())
        }
        return res.findAll {it != null }
    }

    private float[][][] loadData(Reader reader, int nx, int ny, int nz) {
//        Tokenizer tokenizer = new FastTokenizer(reader)
        Tokenizer tokenizer = new FastTokenizer2(reader, WHITESPACE_MATCHER)
        float[][][] data = new float[nx][ny][nz]

        String token = null

        for (int i=0; i!=nx; ++i) {
            for (int j=0; j!=ny; ++j) {
                for (int k=0; k!=nz; ++k) {
                    token = tokenizer.nextToken()
                    if (token == null) {
                        throw new PrankException("Not enough data in the cube file. Missing data for cell ($i,$j,$k) of grid of dimensions ($nx,$ny,$nz).")
                    }
                    data[i][j][k] = Float.parseFloat(token)
                }
            }
        }

        return data
    }

}
