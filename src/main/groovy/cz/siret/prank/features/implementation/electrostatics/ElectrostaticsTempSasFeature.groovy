package cz.siret.prank.features.implementation.electrostatics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.electrostatics.DelphiCubeLoader
import cz.siret.prank.domain.loaders.electrostatics.GaussianCube
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Box
import cz.siret.prank.geom.Point
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.Failable
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.features.implementation.electrostatics.ElectrostaticsTempSasFeature.CubePreloader.ensureCubeLoaded

/**
 * Temporary implementation of external electrostatics feature
 */
@Slf4j
@CompileStatic
class ElectrostaticsTempSasFeature extends SasFeatureCalculator implements Parametrized, Failable {

    static final String CUBE_ATTR = 'electrostatics_cube'

    @Override
    String getName() {
        return "electrostatics_temp"
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        ensureCubeLoaded(protein, context)
    }

//===========================================================================================================//

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
        int ix = (int)Math.floor(rel * edgeSize)        // does floor rounding - this is what we want here
        ix = Math.min(ix, edgeSize-1)
        return ix
    }

    static double cubeValueForPoint(Atom point, Protein protein) {
        GaussianCube cube = (GaussianCube) protein.secondaryData.get(CUBE_ATTR)

        double val = 0
        if (cube) {
            val = valueForPoint(cube, point)
        }
        return val
    }


    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        return [cubeValueForPoint(sasPoint, context.protein)] as double[]
    }

//===========================================================================================================//

    @CompileStatic
    static class CubePreloader implements Parametrized, Failable {

        static ensureCubeLoaded(Protein protein, ProcessedItemContext context) {
            new CubePreloader().preloadCube(protein, context)
        }

        void preloadCube(Protein protein, ProcessedItemContext context) {

            GaussianCube cube = (GaussianCube) protein.secondaryData.get(CUBE_ATTR)

            def label = context.item.label

            if (cube == null) {
                log.info "Loading cube for protein $label"

                try {
                    cube = loadCube(context, Params.inst.electrostatics_dirs)
                } catch (Exception e) {
                    fail("Error while loading cube for ${protein.name}", e, log)
                }

                if (cube == null) {
                    log.error("Cube for ${protein.name} not loaded")
                }
                protein.secondaryData.put((String)CUBE_ATTR, cube)
            } else {
                log.info "Cube for protein $label already loaded"
            }
        }

        private GaussianCube loadCube(ProcessedItemContext context, List<String> electrostatics_dirs) {
            def pname = Futils.baseName(context.item.proteinFile)

            GaussianCube cube = null

            // try loading serialized version

            def sfname = "${pname}/delphi-${pname}.cube.jser"
            def scubeFile = Futils.findCompressedFileInDirs(sfname, electrostatics_dirs)
            if (scubeFile) {
                log.info "Cube file found in: [{}]", scubeFile
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

            def fname = "${pname}/delphi-${pname}.cube" // e.g electrostatics/delphi/3GNA/delphi-3GNA.cube.gz
            def cubeFile = Futils.findCompressedFileInDirs(fname, electrostatics_dirs)

            if (cubeFile) {
                log.info "Cube file found in: [{}]", cubeFile

                cube =  DelphiCubeLoader.loadFile(cubeFile)

                if (cube) {
                    def serf = Futils.removeCompressExt(cubeFile)
                    if (!Futils.exists(serf)) {
                        log.info "Serializing cube to [{}]", serf
                        Futils.serializeToZstd("${serf}.jser.zstd", cube, 2)
                    } else {
                        log.info "Serialized cube already exists in [{}]", serf
                    }
                }

                return cube
            }
            return null
        }
    }

}
