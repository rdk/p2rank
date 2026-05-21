package cz.siret.prank.program.routines.predict.output.grid.assign;

import com.carrotsearch.hppc.LongIntHashMap;
import cz.siret.prank.geom.Atoms;
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;
import org.biojava.nbio.structure.Atom;

import java.util.BitSet;
import java.util.List;

/**
 * Voxel-hash range query. For each input point (typically a pocket's SAS point),
 * iterate the small cubic box of lattice cells that could fall within
 * {@code assignCutoff}, prune by Euclidean cell distance, and verify the survivors
 * with a world-space distance check.
 *
 * <p>No KdTree build cost. Per call: O(S × (2r/spacing + 1)³) cell probes worst-case,
 * where S = input points and r = assignCutoff. The Chebyshev → Euclidean pre-prune
 * (skip cells whose nearest-corner squared distance already exceeds the cutoff) drops
 * ~40% of corner probes before the hashmap lookup. Faster than the KdTree strategy
 * when the grid is coarse and the cells-per-cutoff count is small.
 *
 * <p>Already-set indices are skipped before the distance check (a grid point inside
 * one input point's box may also be inside another's), saving redundant arithmetic.
 */
public final class VoxelHashAssigner implements PocketAssigner {

    @Override
    public String name() { return "voxel_hash"; }

    @Override
    public BitSet computeRawShell(Atoms inputPoints, PocketGrid grid, double assignCutoff) {
        LongIntHashMap latticeIndex = grid.getLatticeIndex();
        List<Atom> allPoints = grid.getAllPoints().list;
        double spacing = grid.getSpacing();
        BitSet raw = new BitSet();

        // Chebyshev radius bounds the cube to scan; Euclidean prune below tightens it
        // against the diagonal corners (which would otherwise be probed despite always
        // being beyond the spherical cutoff).
        int cellsRadius = (int) Math.ceil(assignCutoff / spacing);
        double cutoffSqr = assignCutoff * assignCutoff;

        for (Atom q : inputPoints) {
            double sx = q.getX();
            double sy = q.getY();
            double sz = q.getZ();
            int cx = grid.latticeI(sx);
            int cy = grid.latticeJ(sy);
            int cz = grid.latticeK(sz);

            for (int di = -cellsRadius; di <= cellsRadius; di++) {
                for (int dj = -cellsRadius; dj <= cellsRadius; dj++) {
                    for (int dk = -cellsRadius; dk <= cellsRadius; dk++) {
                        // Euclidean prune — lower bound on world-space distance from q
                        // to the lattice point at (cx+di, cy+dj, cz+dk). q sits at most
                        // spacing/2 away from its containing lattice point (cx,cy,cz),
                        // so the lattice-point separation |di|*spacing along each axis
                        // can be tightened down by spacing/2 in the worst case before
                        // becoming a valid lower bound.
                        double ldi = Math.max(0d, (Math.abs(di) - 0.5d)) * spacing;
                        double ldj = Math.max(0d, (Math.abs(dj) - 0.5d)) * spacing;
                        double ldk = Math.max(0d, (Math.abs(dk) - 0.5d)) * spacing;
                        if (ldi * ldi + ldj * ldj + ldk * ldk > cutoffSqr) continue;

                        int idx = latticeIndex.getOrDefault(
                                PocketGrid.pack(cx + di, cy + dj, cz + dk), PocketGrid.NOT_FOUND);
                        if (idx < 0 || raw.get(idx)) continue;

                        Atom p = allPoints.get(idx);
                        double dx = p.getX() - sx;
                        double dy = p.getY() - sy;
                        double dz = p.getZ() - sz;
                        if (dx * dx + dy * dy + dz * dz <= cutoffSqr) {
                            raw.set(idx);
                        }
                    }
                }
            }
        }
        return raw;
    }

}
