package cz.siret.prank.geom

import cz.cuni.cusbg.surface.FasterNumericalSurface
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.CdkUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.openscience.cdk.geometry.surface.NumericalSurface
import org.openscience.cdk.interfaces.IAtomContainer

import javax.vecmath.Point3d

/**
 * Point surface 
 */
@Slf4j
@CompileStatic
class Surface implements Parametrized {

    public static final double CONSOLIDATE_DIST = 0.05
    public final double VAN_DER_WAALS_COMPENSATION = params.surface_additional_cutoff

    Atoms points
    double surfaceArea

    double solventRadius
    int tesselationLevel

    Surface(double surfaceArea, Atoms surfacePoints, double solventRadius, int tesselationLevel) {
        this.surfaceArea = surfaceArea
        this.points = surfacePoints
        this.solventRadius = solventRadius
        this.tesselationLevel = tesselationLevel
    }

    Atoms computeExposedAtoms(Atoms proteinAtoms) {
        return proteinAtoms.cutoutShell(points, solventRadius + VAN_DER_WAALS_COMPENSATION)
    }

    /**
     * computes solvent accessible surface
     */
    static Surface computeAccessibleSurface(Atoms proteinAtoms, double solventRadius, int tesselationLevel) {

        log.debug "proteinAtoms.count:" + proteinAtoms.count

        IAtomContainer container = CdkUtils.toAtomContainer(proteinAtoms)


        double totalSurfaceArea
        Point3d[] allSurfacePoints

        if (Params.inst.use_optimized_surface) {
            FasterNumericalSurface numericalSurface = new FasterNumericalSurface(container, solventRadius, tesselationLevel)
            totalSurfaceArea = numericalSurface.totalSurfaceArea
            allSurfacePoints = numericalSurface.allSurfacePoints
        } else {
            NumericalSurface numericalSurface = new NumericalSurface(container, solventRadius, tesselationLevel)
            totalSurfaceArea = numericalSurface.totalSurfaceArea
            allSurfacePoints = numericalSurface.allSurfacePoints
        }



        Atoms surfacePoints = CdkUtils.toAtomPoints(allSurfacePoints)

        log.debug "numerical surface: {} points", surfacePoints.count
        // CDK returns lots of duplicate or too-close atoms (bug in the implementation?)
        surfacePoints = Atoms.consolidate(surfacePoints, CONSOLIDATE_DIST)
        log.debug "surface after consolidation: {} points", surfacePoints.count

        Surface res = new Surface(totalSurfaceArea, surfacePoints, solventRadius, tesselationLevel)

        return res
    }

}
