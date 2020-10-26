package cz.siret.prank.features.implementation

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.electrostatics.DelphiCubeLoader
import cz.siret.prank.domain.loaders.electrostatics.GaussianCube
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Box
import cz.siret.prank.geom.Point
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Temporary implementation of external electrostatics feature
 */
@Slf4j
@CompileStatic
class ElectrostaticsTempFeature extends SasFeatureCalculator implements Parametrized {

    static String CUBE_ATTR = 'electrostatic-cube'

    @Override
    String getName() {
        return "electrostatics_temp"
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {

        GaussianCube cube = (GaussianCube) protein.secondaryData.get(CUBE_ATTR)

        def label = context.item.label

        if (cube == null) {
            log.info "Loading cube for protein $label"

            try {
                cube = loadCube(context) 
            } catch (Exception e) {
                fail("Error while loading cube for ${protein.name}", e, log)
            }

            if (cube == null) {
                log.error("Cube for ${protein.name} not loaded")
            }
            protein.secondaryData.put(CUBE_ATTR, cube)
        } else {
            log.info "Cube for protein $label already loaded"
        }
    }

    GaussianCube loadCube(ProcessedItemContext context) {
        def pname = Futils.shortNameWo1CExt(context.item.proteinFile)

        GaussianCube cube = null

        // try loading serialized version

        def sfname = "${pname}/delphi-${pname}.cube.jser"
        def scubeFile = Futils.findFileInDirs(sfname, params.electrostatics_dirs)
        if (scubeFile) {
            try {
                cube = Futils.deserializeFromFile(scubeFile)
            } catch(Exception e) {
                log.error "Failed to deserialize cube from $scubeFile", e
            }
        }

        if (cube) {
            return cube
        }

        // try loading text version

        def fname = "${pname}/delphi-${pname}.cube.gz" // e.g electrostatics/delphi/3GNA/delphi-3GNA.cube.gz
        def cubeFile = Futils.findFileInDirs(fname, params.electrostatics_dirs)

        if (cubeFile) {
            cube =  DelphiCubeLoader.loadFile(cubeFile)

            if (cube) {
                def serf = Futils.removeLastExtension(cubeFile)
                Futils.serializeToFile("${serf}.jser", cube)
            }

            return cube
        }
        return null
    }

    /**
     * for now just discrete voxel matching - no interpolation
     */
    static double valueForPoint(GaussianCube c, Atom p) {

        double val = 0

        Box bb = c.boundingBox
        if (!bb.contains(p)) {
            // log.debug "Point is out of bounds."
        } else {
            Point dist = Struct.distPoint(p, bb.min)

            int ix = indexInCube(dist.x, bb.wx, c.sizeX)
            int iy = indexInCube(dist.y, bb.wy, c.sizeY)
            int iz = indexInCube(dist.z, bb.wz, c.sizeZ)

            val = c.data[ix][iy][iz]
        }

        return val
    }

    /**
     *
     * @param posOnEdge
     * @param edgeWidth
     * @param edgeSize
     * @return from [0, edgeSize-1]
     *
     */
    static int indexInCube(double posOnEdge, double edgeWidth, int edgeSize) {
        double rel = posOnEdge / edgeWidth
        int ix = (int)(rel * edgeSize)        // does floor rounding - this is what we want here
        ix = Math.min(ix, edgeSize-1)
        return ix
    }


    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        GaussianCube cube = (GaussianCube) context.protein.secondaryData.get(CUBE_ATTR)

        double val = 0
        if (cube) {
            val = valueForPoint(cube, sasPoint)
        }

        return [val] as double[]
    }

}
