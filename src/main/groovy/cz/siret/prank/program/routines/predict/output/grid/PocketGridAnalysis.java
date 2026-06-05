package cz.siret.prank.program.routines.predict.output.grid;

import cz.siret.prank.domain.Pocket;
import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.Struct;
import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs;
import cz.siret.prank.program.routines.predict.output.grid.fill.PocketShapeFiller;
import org.biojava.nbio.structure.Atom;

import java.util.BitSet;
import java.util.List;

/**
 * BitSet set-algebra and point-labelling helpers for the pocket-grid analyses
 * (overlap, cavity-fit). Kept in Java on purpose: in Groovy under
 * {@code @CompileStatic}, {@code bitset.and()/.or()/.andNot()} bind to Groovy's
 * {@code DefaultGroovyMethods} operator forms that RETURN a new BitSet instead
 * of mutating the receiver (a silent no-op that bit these analyses twice). Doing
 * the set-algebra here makes that trap impossible and keeps the per-point loops
 * (which the analyses run over whole datasets) off the Groovy path.
 *
 * <p>Stateless; all methods are static.
 */
public final class PocketGridAnalysis {

    private PocketGridAnalysis() {}

    /** @return |a ∩ b| without mutating either argument. */
    public static int intersectionCount(BitSet a, BitSet b) {
        BitSet tmp = (BitSet) a.clone();
        tmp.and(b);
        return tmp.cardinality();
    }

    /**
     * The HARD cross-pocket fill rule, in one place (used by both
     * {@link cz.siret.prank.program.routines.predict.output.grid.PocketGridBuilder}
     * and {@link #unionFilled}): drop from {@code filled} every point that lies in
     * another pocket's raw shell, i.e. in {@code unionRaw} but not in this pocket's
     * own {@code ownRaw}. Fill may expand into unclaimed space, but must not swallow
     * grid points within assignCutoff of a different pocket. Mutates {@code filled}.
     */
    public static void applyCrossPocketRule(BitSet filled, BitSet ownRaw, BitSet unionRaw) {
        BitSet foreign = (BitSet) unionRaw.clone();
        foreign.andNot(ownRaw);   // other pockets' raw shells, excluding our own
        filled.andNot(foreign);
    }

    /**
     * Union (over all pockets) of each pocket's raw shell after applying {@code filler}
     * AND the {@link #applyCrossPocketRule cross-pocket fill rule} — i.e. the exact
     * production per-pocket assignment, unioned. (The union itself is rule-invariant,
     * since a dropped point stays in its rightful pocket; applying the rule here keeps
     * the analysis tooling faithful to {@code PocketGridBuilder} per-pocket.)
     *
     * <p>Re-fills from {@code grid.rawShellForPocket(rank)} (a first-class build output),
     * so it works regardless of which fill the grid itself was built with.
     *
     * @return a fresh BitSet of indices into {@code grid.getAllPoints()}
     */
    public static BitSet unionFilled(PocketGrid grid, List<? extends Pocket> pockets,
                                     PocketShapeFiller filler, FillKnobs knobs) {
        BitSet unionRaw = new BitSet();
        for (Pocket pocket : pockets) unionRaw.or(grid.rawShellForPocket(pocket.getRank()));

        BitSet union = new BitSet();
        for (Pocket pocket : pockets) {
            BitSet raw = grid.rawShellForPocket(pocket.getRank());
            BitSet filled = filler.fill(raw, grid, knobs);
            applyCrossPocketRule(filled, raw, unionRaw);
            union.or(filled);
        }
        return union;
    }

    /**
     * Mask of grid points within {@code d} of at least one {@code target} atom.
     * Used to mark the grid points near the bound ligand (the ligand-envelope
     * ground truth) for the ligand cross-check.
     *
     * @return a BitSet over {@code gridPoints} indices; set bit = within {@code d} of a target
     */
    public static BitSet withinMask(Atoms gridPoints, Atoms targets, double d) {
        BitSet mask = new BitSet();
        if (targets == null || targets.getCount() == 0) return mask;
        int n = gridPoints.getCount();
        for (int i = 0; i < n; i++) {
            Atom p = gridPoints.list.get(i);
            Atom nearest = targets.findNearest(p);
            if (nearest != null && Struct.dist(nearest, p) <= d) mask.set(i);
        }
        return mask;
    }

    /**
     * Cavity mask for a larger probe radius: a grid point is "buried" (belongs to the
     * pocket cavity) when no point of the large-probe accessible surface lies within
     * {@code rLarge} of it -- i.e. the large probe cannot reach it because its surface
     * bridges over the pocket mouth. Points the large probe can reach are open/solvent.
     *
     * @param gridPoints the candidate grid points (e.g. {@code grid.getAllPoints()})
     * @param largeSas   accessible-surface points computed with the large probe radius
     * @param rLarge     the large probe radius (Å)
     * @return a BitSet over {@code gridPoints} indices; set bit = buried/cavity
     */
    public static BitSet buriedMask(Atoms gridPoints, Atoms largeSas, double rLarge) {
        BitSet buried = new BitSet();
        int n = gridPoints.getCount();
        boolean hasSurface = largeSas != null && largeSas.getCount() > 0;
        for (int i = 0; i < n; i++) {
            Atom p = gridPoints.list.get(i);
            if (!hasSurface) { buried.set(i); continue; }
            Atom nearest = largeSas.findNearest(p);
            if (nearest == null || Struct.dist(nearest, p) > rLarge) buried.set(i);
        }
        return buried;
    }

    /**
     * PROTOTYPE (nearest-pocket / Voronoi rule). For each relevant grid point, the rank
     * of the pocket whose SAS surface is nearest (min distance to that pocket's SAS
     * points), ties broken toward the lower rank (higher-confidence pocket). Only points
     * set in {@code relevant} are computed (others stay -1) -- pass the union of the
     * pockets' assignments so we only pay for points that matter.
     *
     * @return owner rank per grid-point index (or -1 if not in {@code relevant} / no SAS)
     */
    public static int[] nearestPocketOwners(PocketGrid grid, List<? extends Pocket> pockets, BitSet relevant) {
        int n = grid.getAllPoints().getCount();
        int[] owner = new int[n];
        double[] best = new double[n];
        java.util.Arrays.fill(owner, -1);
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        List<Atom> pts = grid.getAllPoints().list;
        for (Pocket p : pockets) {
            Atoms sas = p.getSasPoints();
            if (sas == null || sas.getCount() == 0) continue;
            int rank = p.getRank();
            for (int i = relevant.nextSetBit(0); i >= 0; i = relevant.nextSetBit(i + 1)) {
                Atom gp = pts.get(i);
                double d = Struct.dist(sas.findNearest(gp), gp);
                if (d < best[i] - 1e-9) { best[i] = d; owner[i] = rank; }
                else if (d <= best[i] + 1e-9 && owner[i] >= 0 && rank < owner[i]) { owner[i] = rank; }
            }
        }
        return owner;
    }

    /** @return the subset of {@code set} whose points are owned by {@code rank} (nearest-pocket rule). */
    public static BitSet restrictToOwner(BitSet set, int[] owner, int rank) {
        BitSet out = new BitSet();
        for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
            if (owner[i] == rank) out.set(i);
        }
        return out;
    }

    /** Union of all the given BitSets (fresh). */
    public static BitSet unionOf(List<BitSet> sets) {
        BitSet u = new BitSet();
        for (BitSet b : sets) u.or(b);
        return u;
    }

}
