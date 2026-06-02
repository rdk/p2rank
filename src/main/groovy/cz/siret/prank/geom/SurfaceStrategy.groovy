package cz.siret.prank.geom

import cz.cuni.cusbg.surface.FasterNumericalSurface
import cz.cuni.cusbg.surface.PackedNumericalSurface
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.CdkUtils
import groovy.transform.CompileStatic
import org.openscience.cdk.interfaces.IAtomContainer

/**
 * Selectable solvent-accessible-surface generation strategies. Each strategy knows how to build the
 * surface AND how to extract its points in the cheapest way for that backend, and declares whether the
 * produced points still need p2rank's external near-duplicate sparsification (a future strategy that
 * sparsifies internally would set this {@code false}).
 *
 * <p>Selected via the {@code surface_strategy} param (see {@link #resolve}); the legacy boolean
 * {@code use_optimized_surface} is honored as a deprecated fallback when {@code surface_strategy} is
 * empty.
 */
@CompileStatic
enum SurfaceStrategy {

    /** CDK's NumericalSurface via {@link PatchedCdkNumericalSurface} (adds a metal van der Waals fallback). */
    CDK('cdk', true),
    /** The optimized {@link FasterNumericalSurface} (current production default). */
    FASTER('faster', true),
    /** {@link PackedNumericalSurface}: flat store + zero-copy point delivery (bit-exact to FASTER). */
    PACKED('packed', true)

    final String id
    final boolean requiresSparsification

    SurfaceStrategy(String id, boolean requiresSparsification) {
        this.id = id
        this.requiresSparsification = requiresSparsification
    }

    /** Build the surface; returns the total area and the surface points BEFORE any sparsification. */
    RawSurface compute(IAtomContainer container, double solventRadius, int tesselationLevel) {
        switch (this) {
            case CDK:
                PatchedCdkNumericalSurface s = new PatchedCdkNumericalSurface(container, solventRadius, tesselationLevel)
                return new RawSurface(s.totalSurfaceArea, CdkUtils.toAtomPoints(s.allSurfacePoints))
            case FASTER:
                FasterNumericalSurface s = new FasterNumericalSurface(container, solventRadius, tesselationLevel)
                return new RawSurface(s.totalSurfaceArea, CdkUtils.toAtomPoints(s.allSurfacePoints))
            case PACKED:
                PackedNumericalSurface s = new PackedNumericalSurface(container, solventRadius, tesselationLevel)
                return new RawSurface(s.totalSurfaceArea, CdkUtils.toAtomPoints(s.surfacePointsXYZ(), s.surfacePointCount()))
            default:
                throw new IllegalStateException("unhandled surface strategy: $this")
        }
    }

    /**
     * Resolve the configured strategy. Precedence: explicit {@code surface_strategy} wins; if empty, the
     * deprecated {@code use_optimized_surface} maps true -> faster, false -> cdk (preserving old behavior).
     */
    static SurfaceStrategy resolve(Params params) {
        String name = params.surface_strategy?.trim()?.toLowerCase()
        if (!name) {
            name = params.use_optimized_surface ? FASTER.id : CDK.id
        }
        for (SurfaceStrategy s : values()) {
            if (s.id == name) return s
        }
        throw new PrankException("Unknown surface_strategy '${name}'. Valid options: ${values().collect { it.id }}")
    }

    /** Total surface area + surface points, as produced by a strategy before sparsification. */
    @CompileStatic
    static class RawSurface {
        final double totalSurfaceArea
        final Atoms points
        RawSurface(double totalSurfaceArea, Atoms points) {
            this.totalSurfaceArea = totalSurfaceArea
            this.points = points
        }
    }
}
