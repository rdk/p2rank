package cz.siret.prank.geom

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.CdkUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.openscience.cdk.geometry.surface.NumericalSurface
import org.openscience.cdk.interfaces.IAtomContainer

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
    int tesslevel

    Surface(double surfaceArea, Atoms surfacePoints, double solventRadius, int tesslevel) {
        this.surfaceArea = surfaceArea
        this.points = surfacePoints
        this.solventRadius = solventRadius
        this.tesslevel = tesslevel
    }

    Atoms computeExposedAtoms(Atoms proteinAtoms) {
        return proteinAtoms.cutoutShell(points, solventRadius + VAN_DER_WAALS_COMPENSATION)
    }

    /**
     * computes solvent accessible surface
     */
    static Surface computeAccessibleSurface(Atoms proteinAtoms, double solventRadius, int tesslevel) {

        log.debug "proteinAtoms.count:" + proteinAtoms.count

        IAtomContainer container = CdkUtils.toAtomContainer(proteinAtoms)
        NumericalSurface numericalSurface = new NumericalSurface(container, solventRadius, tesslevel)
        numericalSurface.calculateSurface()  // for CDK since 2 or so surface is calculated in constructor, left here in case of temporary switch to cdk 1.*

        Atoms surfacePoints = CdkUtils.toAtomPoints(numericalSurface.allSurfacePoints)

        log.debug "numerical surface: {} points", surfacePoints.count
        // CDK returns lots of duplicate or too-close atoms (bug in the implementation?)
        surfacePoints = Atoms.consolidate(surfacePoints, CONSOLIDATE_DIST)
        log.debug "surface after consolidation: {} points", surfacePoints.count

        Surface res = new Surface(numericalSurface.totalSurfaceArea, surfacePoints, solventRadius, tesslevel)

        return res
    }

}
