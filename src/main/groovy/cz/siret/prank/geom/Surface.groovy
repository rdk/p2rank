package cz.siret.prank.geom

import cz.cuni.cusbg.surface.FasterNumericalSurface
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.CdkUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.openscience.cdk.interfaces.IAtomContainer

import javax.vecmath.Point3d

/**
 * Point surface 
 */
@Slf4j
@CompileStatic
class Surface implements Parametrized {

    public static final double SPARSIFY_DIST = 0.05
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

        // Pluggable, CLI-selectable surface strategy (cdk | faster | packed). Each strategy builds the
        // surface and extracts its points in the cheapest way for that backend (the 'packed' strategy
        // uses the zero-copy PackedSurfaceAccess path, avoiding the per-point Point3d). The metal-VdW
        // fallback lives in the 'cdk' strategy (PatchedCdkNumericalSurface). See SurfaceStrategy.
        SurfaceStrategy strategy = SurfaceStrategy.resolve(Params.inst)
        SurfaceStrategy.RawSurface raw = strategy.compute(container, solventRadius, tesselationLevel)

        double totalSurfaceArea = raw.totalSurfaceArea
        Atoms surfacePoints = raw.points

        log.debug "numerical surface ({}): {} points", strategy.id, surfacePoints.count
        // Two-boolean gate: the strategy declares whether its points need external sparsification, and
        // the global surface_sparsify can still force it off. A future strategy that sparsifies
        // internally would set requiresSparsification=false and skip this step.
        if (strategy.requiresSparsification && Params.inst.surface_sparsify) {
            // CDK/Faster/Packed return lots of duplicate or too-close points (icosahedral tessellation)
            surfacePoints = AtomDeduplicator.sparsify(surfacePoints, SPARSIFY_DIST)
            log.debug "surface after sparsification: {} points", surfacePoints.count
        }

        Surface res = new Surface(totalSurfaceArea, surfacePoints, solventRadius, tesselationLevel)

        return res
    }

}
