package cz.siret.prank.geom;

import cz.siret.prank.geom.spatial.UniformGrid3D;
import org.biojava.nbio.structure.Atom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Spatial de-duplication ("sparsification") of atom point sets.
 *
 * Extracted out of {@link Atoms} to keep that class focused and to make the
 * underlying {@link UniformGrid3D} reusable.
 */
public final class AtomDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(AtomDeduplicator.class);

    private AtomDeduplicator() {}

    /**
     * Remove points that are within {@code dist} of an already-accepted point.
     *
     * Greedy and order-dependent: iterates {@code atoms} in input order and keeps a
     * point iff every already-kept point is strictly farther than {@code dist}. Backed
     * by a {@link UniformGrid3D} with cell size = {@code dist}, giving O(N) expected
     * time. The result is identical to a brute-force greedy pass in the same order
     * (replaces the previous incremental k-d-tree implementation, byte-for-byte).
     */
    public static Atoms sparsify(Atoms atoms, double dist) {
        long t0 = System.nanoTime();
        int inputSize = atoms.getCount();

        UniformGrid3D<Atom> grid = new UniformGrid3D<>(dist);
        List<Atom> result = new ArrayList<>(inputSize);

        for (Atom a : atoms) {
            double x = a.getX(), y = a.getY(), z = a.getZ();
            if (!grid.hasAnyWithin(x, y, z, dist)) {
                result.add(a);
                grid.insert(x, y, z, a);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("sparsify: {} -> {} points in {} ms",
                    inputSize, result.size(), (System.nanoTime() - t0) / 1_000_000);
        }
        return new Atoms(result);
    }
}
