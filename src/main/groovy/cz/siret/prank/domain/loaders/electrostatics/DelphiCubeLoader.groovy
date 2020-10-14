package cz.siret.prank.domain.loaders.electrostatics

import cz.siret.prank.geom.Point
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic

import java.util.zip.GZIPInputStream

import static cz.siret.prank.utils.Sutils.splitOnWhitespace

/**
 *
 */
@CompileStatic
class DelphiCubeLoader {

    private static int HEADER_SIZE = 7


    /**
     * Load from *.cube or *.cube.gz file
     * @return
     */
    static GaussianCube loadFromFile(String fname) {
        File file = new File(fname)
        InputStream is
        if (fname.endsWith(".gz")) {
            is = new GZIPInputStream(new FileInputStream(file));
        } else {
            is = new FileInputStream(file);
        }
        return loadFromStream(is)
    }

    static GaussianCube loadFromStream(InputStream is) {
        Reader reader = new BufferedReader(new InputStreamReader(is))

        List<String> header = loadHeader(reader)
        GaussianCube cube = createFromHeader(header)
        cube.data = loadData(reader, cube.nx, cube.ny, cube.nz)

        reader.close()

        return cube
    }

    /**
     *
     * <pre>
     *   2.000000   205 20.279000 34.476500 65.844500
     * Gaussian cube format phimap
     *     1    -58.054276    -31.224890     28.052039
     *   205      0.944863      0.000000      0.000000
     *   205      0.000000      0.944863      0.000000
     *   205      0.000000      0.000000      0.944863
     *     1      0.000000      0.000000      0.000000      0.000000
     *  -3.54102e+01 -3.55270e+01 -3.56440e+01 -3.57610e+01 -3.58780e+01 -3.59949e+01
     *  ....
     * </pre>
     */
    private static GaussianCube createFromHeader(List<String> header) {
        GaussianCube cube = new GaussianCube()

        List<List<String>> tokens = header.collect { splitOnWhitespace(it) }

        cube.header = header

        cube.nx = tokens[3][0].toInteger()
        cube.ny = tokens[4][0].toInteger()
        cube.nz = tokens[5][0].toInteger()

        cube.dx = tokens[3][1].toDouble()
        cube.dy = tokens[4][2].toDouble()
        cube.dz = tokens[5][3].toDouble()

        double ox = tokens[2][1].toDouble()     // ??
        double oy = tokens[2][2].toDouble()     // ??
        double oz = tokens[2][3].toDouble()     // ??

        cube.origin = new Point(ox, oy, oz)

        return cube
    }

    private static List<String> loadHeader(Reader reader) {
        List<String> res = new ArrayList<>()
        for (int i=0; i!=HEADER_SIZE; i++) {
            res.add(reader.readLine())
        }
        return res
    }

    private static float[][][] loadData(Reader reader, int nx, int ny, int nz) {
        Scanner scanner = new Scanner(reader)

        float[][][] data = new float[nx][ny][nz]

        for (int i=0; i!=nx; ++i) {
            for (int j=0; j!=ny; ++j) {
                for (int k=0; k!=nz; ++k) {
                    if (!scanner.hasNext()) {
                        throw new PrankException("Not enough data in the cube file. Missing data for cell ($i,$j,$k) of grid of dimensions ($nx,$ny,$nz).")
                    }

                    float val = (float) scanner.nextDouble()
                    data[i][j][k] = val
                }
            }
        }

        return data
    }
}
