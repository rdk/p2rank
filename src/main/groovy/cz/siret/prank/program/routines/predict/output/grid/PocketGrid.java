package cz.siret.prank.program.routines.predict.output.grid;

import com.carrotsearch.hppc.LongIntHashMap;
import cz.siret.prank.geom.Atoms;
import org.biojava.nbio.structure.Atom;

import java.util.BitSet;
import java.util.Map;

/**
 * In-memory representation of a 3D grid of points covering the empty space
 * around a protein, with per-pocket membership.
 *
 * <ul>
 *   <li>{@link #getAllPoints()} returns the lattice points that passed the
 *       min/max distance filter (see
 *       {@link cz.siret.prank.geom.samplers.GridGenerator#sampleGridPointsBetween}).</li>
 *   <li>{@link #getLatticeIndex()} maps each kept point's integer lattice
 *       coordinate (packed into a {@code long} via {@link #pack(int, int, int)})
 *       to its index in {@code allPoints} — enables O(1) 26-neighbor lookup
 *       without per-call key allocation. Uses HPPC's {@link LongIntHashMap}
 *       (primitive long→int) — no Long/Integer boxing on put/get, which matters
 *       because the morph closer's 26-neighbor probes run millions of times per
 *       build and previously dominated GC.</li>
 *   <li>{@link #getPocketToPointIndices()} is the per-pocket multi-valued
 *       assignment, stored as a {@link BitSet} of point indices for each
 *       pocket rank (zero autoboxing on add/contains/iterate, vectorized
 *       union/intersect, ~32× memory reduction vs {@code Set<Integer>}).</li>
 * </ul>
 *
 * <p>Constructed by {@link PocketGridBuilder}. Tests can build small instances
 * directly via the constructor. Bean-style getters (not record accessors) so
 * Groovy callers using property syntax — {@code grid.allPoints},
 * {@code grid.pointCount} — keep working.
 *
 * <p><b>WARNING for Groovy callers</b> of {@link #indicesForPocket} /
 * {@link #getPocketToPointIndices}: in Groovy under {@code @CompileStatic},
 * {@code bitset.and(other)} / {@code .or(other)} / {@code .andNot(other)} do NOT
 * mutate — they bind to Groovy's {@code DefaultGroovyMethods.and/or(BitSet,BitSet)}
 * which RETURN a new BitSet and leave the receiver unchanged (a silent no-op that
 * makes every "overlap"/"union" look like the receiver's own cardinality). Use the
 * operators ({@code a & b}, {@code a | b}, {@code a & ~b}) and assign, or do the
 * set-algebra in Java. This trap bit the pocket-grid analyses in AnalyzeRoutine twice.
 */
public final class PocketGrid {

    /**
     * Sentinel returned by {@link LongIntHashMap#getOrDefault} when a packed
     * lattice key is not present. Indices are always &gt;= 0 so any negative
     * value works; -1 is the conventional "not found".
     */
    public static final int NOT_FOUND = -1;

    /**
     * Offset added before packing to handle negative lattice coordinates within
     * a 20-bit unsigned field. With this offset, the safe lattice coord range
     * per axis is {@code [-524288, +524287]} (asymmetric by one on the positive
     * side — the 20-bit unsigned field tops out at 2^20-1 = 1048575). That's
     * way more than needed: typical proteins span a few hundred lattice cells
     * per dim.
     */
    public static final int LATTICE_OFFSET = 524288;  // 2^19

    /** Pack 3D integer lattice coord into a 64-bit key for the lattice index. */
    public static long pack(int i, int j, int k) {
        return (((long) (i + LATTICE_OFFSET) & 0xFFFFFL) << 40) |
               (((long) (j + LATTICE_OFFSET) & 0xFFFFFL) << 20) |
                ((long) (k + LATTICE_OFFSET) & 0xFFFFFL);
    }

    private final Atoms allPoints;
    private final double spacing;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final LongIntHashMap latticeIndex;
    private final Map<Integer, BitSet> pocketToPointIndices;
    /** Per-pocket RAW shell (points within assignCutoff of the pocket's SAS, pre-fill).
     *  A first-class build output so analyses don't re-derive it via a second fill=none build. */
    private final Map<Integer, BitSet> pocketToRawShell;

    public PocketGrid(Atoms allPoints,
                      double spacing,
                      double originX,
                      double originY,
                      double originZ,
                      LongIntHashMap latticeIndex,
                      Map<Integer, BitSet> pocketToPointIndices,
                      Map<Integer, BitSet> pocketToRawShell) {
        this.allPoints = allPoints;
        this.spacing = spacing;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.latticeIndex = latticeIndex;
        this.pocketToPointIndices = pocketToPointIndices;
        this.pocketToRawShell = pocketToRawShell;
    }

    /** Convenience constructor for hand-built test grids: raw shell == assigned (no fill applied). */
    public PocketGrid(Atoms allPoints, double spacing, double originX, double originY, double originZ,
                      LongIntHashMap latticeIndex, Map<Integer, BitSet> pocketToPointIndices) {
        this(allPoints, spacing, originX, originY, originZ, latticeIndex, pocketToPointIndices, pocketToPointIndices);
    }

    public Atoms getAllPoints() { return allPoints; }
    public double getSpacing() { return spacing; }
    public double getOriginX() { return originX; }
    public double getOriginY() { return originY; }
    public double getOriginZ() { return originZ; }
    /** Packed-long lattice key → index in {@code allPoints}. Lookups return {@link #NOT_FOUND} when absent. */
    public LongIntHashMap getLatticeIndex() { return latticeIndex; }
    /** Pocket rank → BitSet of indices in {@code allPoints}. */
    public Map<Integer, BitSet> getPocketToPointIndices() { return pocketToPointIndices; }

    public int getPointCount() {
        return allPoints.getCount();
    }

    public int getPocketCount() {
        return pocketToPointIndices.size();
    }

    /** @return this pocket's RAW shell (pre-fill); empty BitSet if pocket unknown. */
    public BitSet rawShellForPocket(int rank) {
        BitSet bs = pocketToRawShell.get(rank);
        return bs != null ? bs : new BitSet();
    }

    /** @return BitSet of indices assigned to a pocket; empty BitSet if pocket unknown. */
    public BitSet indicesForPocket(int rank) {
        BitSet bs = pocketToPointIndices.get(rank);
        return bs != null ? bs : new BitSet();
    }

    /** World-space x → lattice i (signed; centered on the grid's origin). */
    public int latticeI(double x) { return (int) Math.round((x - originX) / spacing); }
    public int latticeJ(double y) { return (int) Math.round((y - originY) / spacing); }
    public int latticeK(double z) { return (int) Math.round((z - originZ) / spacing); }

    /** Pack the lattice key for the cell containing {@code p}. Convenience over the three latticeI/J/K calls. */
    public long packLatticeKey(Atom p) {
        return pack(latticeI(p.getX()), latticeJ(p.getY()), latticeK(p.getZ()));
    }

    /** Looks up the {@code allPoints} index for the cell containing {@code p}, or {@link #NOT_FOUND}. */
    public int indexOf(Atom p) {
        return latticeIndex.getOrDefault(packLatticeKey(p), NOT_FOUND);
    }

    /**
     * Write the 0..26 surviving 26-neighborhood neighbor indices into {@code buf}.
     * Caller pre-allocates a buf of length ≥ 26 and reuses it across calls
     * (zero allocation per call — important for the morph closer's hot loop).
     *
     * @return the number of neighbors written into {@code buf}
     */
    public int neighborsInto(int pointIndex, int[] buf) {
        Atom p = allPoints.list.get(pointIndex);
        int li = latticeI(p.getX());
        int lj = latticeJ(p.getY());
        int lk = latticeK(p.getZ());
        int n = 0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                for (int dk = -1; dk <= 1; dk++) {
                    if (di == 0 && dj == 0 && dk == 0) continue;
                    int idx = latticeIndex.getOrDefault(pack(li + di, lj + dj, lk + dk), NOT_FOUND);
                    if (idx >= 0) buf[n++] = idx;
                }
            }
        }
        return n;
    }

}
