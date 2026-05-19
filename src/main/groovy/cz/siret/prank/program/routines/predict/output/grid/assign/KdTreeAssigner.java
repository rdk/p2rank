package cz.siret.prank.program.routines.predict.output.grid.assign;

import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.kdtree.AtomKdTree;
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;
import org.biojava.nbio.structure.Atom;

import java.util.BitSet;

/**
 * KdTree-based range query. Per call: query the grid's KdTree around each input point
 * (typically a pocket's SAS point), then map each hit back to its lattice index via
 * {@link PocketGrid#indexOf}.
 *
 * <p>O(N log N) up-front build + O(S × (log N + answer)) per call, where N = grid points,
 * S = input points. Faster than the voxel-hash strategy when the grid is dense (small
 * spacing) — the KdTree's logarithmic per-query factor wins over voxel-hash's
 * O((cutoff/spacing)³) cube scan.
 */
public final class KdTreeAssigner implements PocketAssigner {

    @Override
    public String name() { return "kdtree"; }

    @Override
    public void initialize(PocketGrid grid) {
        // Build the KdTree eagerly so per-call queries are pure query cost (no
        // first-call build overhead).
        grid.getAllPoints().withKdTree();
    }

    @Override
    public BitSet computeRawShell(Atoms inputPoints, PocketGrid grid, double assignCutoff) {
        AtomKdTree kdTree = grid.getAllPoints().getKdTree();
        BitSet raw = new BitSet();

        for (Atom q : inputPoints) {
            Atoms nearby = kdTree.findAtomsWithinRadius(q, assignCutoff, false);
            for (Atom p : nearby) {
                int idx = grid.indexOf(p);
                if (idx < 0 || raw.get(idx)) continue;  // missing or already in shell
                raw.set(idx);
            }
        }
        return raw;
    }

}
