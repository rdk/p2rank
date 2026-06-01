package cz.siret.prank.geom.kdtree.v2;

import cz.siret.prank.geom.Atoms;
import org.biojava.nbio.structure.Atom;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable 3D KdTree optimized for protein atom spatial queries.
 *
 * Design choices and trade-offs:
 * - Immutable: build once, query many times. No add() after construction.
 *   Enables inherent thread-safety (no mutable state) and simpler/faster query code.
 * - Structure of Arrays (SoA): coordinates stored as separate xs[], ys[], zs[] arrays.
 *   Leaf scanning reads contiguous memory (3 cache lines per axis for 24-point bucket)
 *   vs AoS where each double[3] is a scattered heap object (~24 L2 cache misses).
 * - Linearized implicit-heap layout: node i has children at 2i+1, 2i+2.
 *   Array indexing replaces pointer chasing. All node data fits in L2 cache for typical proteins.
 * - Hardcoded 3D with squared Euclidean distance. No generic N-dimensional loops.
 *   Distance functions are private static → guaranteed JIT inline (no virtual dispatch).
 * - No NaN checks: protein coordinates from PDB/mmCIF are always valid doubles.
 * - Balanced build via quickselect: median splitting guarantees optimal tree depth.
 * - Stack-based traversal: local int[] stack per query (~120 bytes, fits in L1).
 *   No per-node mutable status field → safe for concurrent queries from multiple threads.
 *
 * Typical usage: 3000-6000 atoms, thousands of radius queries per protein.
 */
public final class KdTree3D {

    // --- Configuration ---

    /** Leaf bucket size. 24 balances leaf scan cost vs tree depth.
     *  Same as the original Rednaxela KdTree. */
    private static final int BUCKET_SIZE = 24;

    // --- Point data (SoA layout) ---
    // Contiguous per-axis arrays. Leaf scanning reads xs[start..end], ys[start..end], zs[start..end]
    // sequentially — prefetcher-friendly. Each 24-point bucket = 192 bytes per axis = 3 cache lines.

    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final Atom[] atoms;
    private final int size;

    // --- Tree structure (linearized implicit heap) ---
    // Node i: children at 2i+1 (left), 2i+2 (right).
    // Leaf nodes have leafStart[i] >= 0; internal nodes have leafStart[i] == -1.

    private final byte[] splitDims;      // split axis: 0=x, 1=y, 2=z (byte saves memory vs int)
    private final double[] splitVals;    // split plane coordinate

    // Per-node axis-aligned bounding boxes for subtree pruning.
    // Stored separately per axis for SoA-style access during pointRegionDist.
    private final double[] minXs, minYs, minZs;
    private final double[] maxXs, maxYs, maxZs;

    // Leaf data ranges into the SoA arrays. leafStart[i]=-1 means internal node.
    private final int[] leafStart;
    private final int[] leafEnd;

    private final int nodeCount;

    /** Singleton empty tree. findNearest returns null, findWithinRadius returns empty Atoms. */
    public static final KdTree3D EMPTY = new KdTree3D();

    /** Lightweight k-NN result. Record auto-generates equals/hashCode/toString. */
    public record NNEntry(double sqrDist, Atom atom) {}

    // --- Private constructors ---

    /** Empty tree constructor (for EMPTY singleton). */
    private KdTree3D() {
        this.xs = new double[0];
        this.ys = new double[0];
        this.zs = new double[0];
        this.atoms = new Atom[0];
        this.size = 0;
        this.splitDims = new byte[0];
        this.splitVals = new double[0];
        this.minXs = new double[0]; this.minYs = new double[0]; this.minZs = new double[0];
        this.maxXs = new double[0]; this.maxYs = new double[0]; this.maxZs = new double[0];
        this.leafStart = new int[0];
        this.leafEnd = new int[0];
        this.nodeCount = 0;
    }

    /** Full constructor — called only from build(). */
    private KdTree3D(double[] xs, double[] ys, double[] zs, Atom[] atoms, int size,
                     byte[] splitDims, double[] splitVals,
                     double[] minXs, double[] minYs, double[] minZs,
                     double[] maxXs, double[] maxYs, double[] maxZs,
                     int[] leafStart, int[] leafEnd, int nodeCount) {
        this.xs = xs; this.ys = ys; this.zs = zs;
        this.atoms = atoms; this.size = size;
        this.splitDims = splitDims; this.splitVals = splitVals;
        this.minXs = minXs; this.minYs = minYs; this.minZs = minZs;
        this.maxXs = maxXs; this.maxYs = maxYs; this.maxZs = maxZs;
        this.leafStart = leafStart; this.leafEnd = leafEnd;
        this.nodeCount = nodeCount;
    }

    public int size() { return size; }

    // ==================== Build ====================

    /**
     * Build from Atoms. Extracts xyz via getX/getY/getZ — no double[] allocation per atom.
     */
    public static KdTree3D build(Atoms atoms) {
        int n = atoms.getCount();
        if (n == 0) return EMPTY;

        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        Atom[] atomArr = new Atom[n];

        for (int i = 0; i < n; i++) {
            Atom a = atoms.list.get(i);
            xs[i] = a.getX();
            ys[i] = a.getY();
            zs[i] = a.getZ();
            atomArr[i] = a;
        }

        return buildFromArrays(xs, ys, zs, atomArr, n);
    }

    /**
     * Build from List of Atoms. Convenience overload for sparsify().
     */
    public static KdTree3D build(List<? extends Atom> atoms) {
        int n = atoms.size();
        if (n == 0) return EMPTY;

        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        Atom[] atomArr = new Atom[n];

        for (int i = 0; i < n; i++) {
            Atom a = atoms.get(i);
            xs[i] = a.getX();
            ys[i] = a.getY();
            zs[i] = a.getZ();
            atomArr[i] = a;
        }

        return buildFromArrays(xs, ys, zs, atomArr, n);
    }

    /**
     * Core build from pre-extracted SoA arrays.
     * Uses balanced median-based partitioning via quickselect.
     * O(N log N) expected time (dominated by quickselect).
     * Bounding boxes: O(N) via bottom-up propagation from leaf scans.
     * Axis selection: O(1) per internal node using approximate parent bounds passed down
     * (single O(N) scan at root, then narrowed by split values at each level).
     */
    private static KdTree3D buildFromArrays(double[] xs, double[] ys, double[] zs, Atom[] atoms, int n) {
        // Calculate max tree depth for the implicit heap.
        // With median splitting and bucket size B, we get ceil(log2(n/B)) + 1 levels.
        // Complete binary tree of that depth has 2^(depth+1) - 1 nodes.
        int maxDepth = 0;
        int tmp = n;
        while (tmp > BUCKET_SIZE) {
            maxDepth++;
            tmp = (tmp + 1) / 2; // ceiling division simulates median split
        }
        int maxNodes = (1 << (maxDepth + 1)) - 1;
        if (maxNodes < 1) maxNodes = 1;

        // Allocate node arrays
        byte[] splitDims = new byte[maxNodes];
        double[] splitVals = new double[maxNodes];
        double[] minXs = new double[maxNodes];
        double[] minYs = new double[maxNodes];
        double[] minZs = new double[maxNodes];
        double[] maxXArr = new double[maxNodes];
        double[] maxYArr = new double[maxNodes];
        double[] maxZArr = new double[maxNodes];
        int[] leafStartArr = new int[maxNodes];
        int[] leafEndArr = new int[maxNodes];

        java.util.Arrays.fill(leafStartArr, -1);

        // Single O(N) scan for root bounding box — passed down for axis selection.
        double xMin = xs[0], yMin = ys[0], zMin = zs[0];
        double xMax = xs[0], yMax = ys[0], zMax = zs[0];
        for (int i = 1; i < n; i++) {
            if (xs[i] < xMin) xMin = xs[i]; else if (xs[i] > xMax) xMax = xs[i];
            if (ys[i] < yMin) yMin = ys[i]; else if (ys[i] > yMax) yMax = ys[i];
            if (zs[i] < zMin) zMin = zs[i]; else if (zs[i] > zMax) zMax = zs[i];
        }

        // Build recursively. Returns highest node index used (= nodeCount).
        int nodeCount = buildNode(0, 0, n,
                xs, ys, zs, atoms,
                splitDims, splitVals,
                minXs, minYs, minZs, maxXArr, maxYArr, maxZArr,
                leafStartArr, leafEndArr,
                xMin, yMin, zMin, xMax, yMax, zMax);

        return new KdTree3D(xs, ys, zs, atoms, n,
                splitDims, splitVals,
                minXs, minYs, minZs, maxXArr, maxYArr, maxZArr,
                leafStartArr, leafEndArr, nodeCount);
    }

    /**
     * Recursively build a node. Returns the highest node index used in this subtree (1-based count).
     *
     * Axis selection uses approximate parent bounds (pMin/pMax) passed down from the root.
     * These are narrowed by split values at each level — O(1) per node vs O(range) scanning.
     * The approximation only affects axis choice, not correctness: child ranges are ≤ parent
     * ranges, so the widest-axis heuristic occasionally picks a suboptimal axis. In practice
     * this rarely matters for 3D protein data (roughly spherical distribution, similar axis widths).
     *
     * Bounding boxes stored for query pruning are exact — computed bottom-up from leaf scans.
     * Leaves scan their bucket points (each point visited exactly once → O(N) total).
     * Internal nodes take the union of their children's boxes (O(1) per node).
     *
     * @param nodeIdx  index in the implicit heap
     * @param from     start of data range (inclusive)
     * @param to       end of data range (exclusive)
     * @param pMinX..pMaxZ  approximate parent bounds for axis selection
     * @return highest node index + 1 (i.e. nodeCount for this subtree)
     */
    private static int buildNode(int nodeIdx, int from, int to,
                                 double[] xs, double[] ys, double[] zs, Atom[] atoms,
                                 byte[] splitDims, double[] splitVals,
                                 double[] minXs, double[] minYs, double[] minZs,
                                 double[] maxXs, double[] maxYs, double[] maxZs,
                                 int[] leafStart, int[] leafEnd,
                                 double pMinX, double pMinY, double pMinZ,
                                 double pMaxX, double pMaxY, double pMaxZ) {
        int count = to - from;

        // Leaf: data range fits in bucket — scan bucket for exact bounds
        if (count <= BUCKET_SIZE) {
            double xMin = xs[from], yMin = ys[from], zMin = zs[from];
            double xMax = xs[from], yMax = ys[from], zMax = zs[from];
            for (int i = from + 1; i < to; i++) {
                if (xs[i] < xMin) xMin = xs[i]; else if (xs[i] > xMax) xMax = xs[i];
                if (ys[i] < yMin) yMin = ys[i]; else if (ys[i] > yMax) yMax = ys[i];
                if (zs[i] < zMin) zMin = zs[i]; else if (zs[i] > zMax) zMax = zs[i];
            }
            minXs[nodeIdx] = xMin; minYs[nodeIdx] = yMin; minZs[nodeIdx] = zMin;
            maxXs[nodeIdx] = xMax; maxYs[nodeIdx] = yMax; maxZs[nodeIdx] = zMax;
            leafStart[nodeIdx] = from;
            leafEnd[nodeIdx] = to;
            return nodeIdx + 1;
        }

        // Internal node: pick split axis from approximate parent bounds (O(1), no scanning)
        double xWidth = pMaxX - pMinX;
        double yWidth = pMaxY - pMinY;
        double zWidth = pMaxZ - pMinZ;

        byte dim;
        if (xWidth >= yWidth && xWidth >= zWidth) dim = 0;
        else if (yWidth >= zWidth) dim = 1;
        else dim = 2;

        // Partition by median using quickselect. After this:
        // - data[from..mid) has coordinate[dim] <= median
        // - data[mid..to) has coordinate[dim] >= median
        int mid = from + count / 2;
        quickselect(xs, ys, zs, atoms, from, to - 1, mid, dim);

        double splitVal = getCoord(xs, ys, zs, mid, dim);
        splitDims[nodeIdx] = dim;
        splitVals[nodeIdx] = splitVal;
        // leafStart stays -1 (internal node marker)

        // Narrow parent bounds along the split axis for each child
        // Left child: split-axis max clamped to splitVal
        // Right child: split-axis min clamped to splitVal
        double lMaxX = pMaxX, lMaxY = pMaxY, lMaxZ = pMaxZ;
        double rMinX = pMinX, rMinY = pMinY, rMinZ = pMinZ;
        if (dim == 0)      { lMaxX = splitVal; rMinX = splitVal; }
        else if (dim == 1) { lMaxY = splitVal; rMinY = splitVal; }
        else               { lMaxZ = splitVal; rMinZ = splitVal; }

        // Recurse into children
        int leftCount = buildNode(2 * nodeIdx + 1, from, mid, xs, ys, zs, atoms,
                splitDims, splitVals, minXs, minYs, minZs, maxXs, maxYs, maxZs,
                leafStart, leafEnd,
                pMinX, pMinY, pMinZ, lMaxX, lMaxY, lMaxZ);
        int rightCount = buildNode(2 * nodeIdx + 2, mid, to, xs, ys, zs, atoms,
                splitDims, splitVals, minXs, minYs, minZs, maxXs, maxYs, maxZs,
                leafStart, leafEnd,
                rMinX, rMinY, rMinZ, pMaxX, pMaxY, pMaxZ);

        // Bottom-up bounding box: union of children's exact boxes (O(1) per internal node)
        int l = 2 * nodeIdx + 1;
        int r = 2 * nodeIdx + 2;
        minXs[nodeIdx] = Math.min(minXs[l], minXs[r]);
        minYs[nodeIdx] = Math.min(minYs[l], minYs[r]);
        minZs[nodeIdx] = Math.min(minZs[l], minZs[r]);
        maxXs[nodeIdx] = Math.max(maxXs[l], maxXs[r]);
        maxYs[nodeIdx] = Math.max(maxYs[l], maxYs[r]);
        maxZs[nodeIdx] = Math.max(maxZs[l], maxZs[r]);

        return Math.max(nodeIdx + 1, Math.max(leftCount, rightCount));
    }

    // ==================== Quickselect ====================

    /**
     * In-place quickselect: partitions so that element at index k is the k-th smallest
     * along the given dimension. All elements [lo..k) <= pivot, all [k..hi] >= pivot.
     *
     * Swaps all 4 parallel arrays (xs, ys, zs, atoms) in sync.
     * Expected O(N) per call. Uses median-of-3 pivot selection to avoid worst-case.
     *
     * Resolves the split-axis array once (keys = xs/ys/zs based on dim) to avoid
     * per-comparison branching in the inner partition loop.
     *
     * Implementation: Sedgewick-style partition with sentinels (fast for random data),
     * plus post-partition equal-range scan to skip duplicate regions. This prevents O(N²)
     * degeneration when many elements share the same coordinate value (common for surface
     * points on flat protein regions where many atoms align along one axis). With many
     * duplicates, the equal range around the pivot is large → the scan catches most of them
     * and skips the entire region in one step.
     */
    private static void quickselect(double[] xs, double[] ys, double[] zs, Atom[] atoms,
                                    int lo, int hi, int k, int dim) {
        // Resolve split-axis array once — inner loop uses keys[] directly, no branching on dim.
        double[] keys = dim == 0 ? xs : dim == 1 ? ys : zs;

        while (lo < hi) {
            // Base case: 2 elements — just sort them
            if (hi - lo == 1) {
                if (keys[lo] > keys[hi]) {
                    swap(xs, ys, zs, atoms, lo, hi);
                }
                return;
            }

            // Median-of-three: sort keys[lo], keys[mid], keys[hi] to select pivot.
            int mid = lo + (hi - lo) / 2;
            if (keys[lo] > keys[mid]) swap(xs, ys, zs, atoms, lo, mid);
            if (keys[lo] > keys[hi])  swap(xs, ys, zs, atoms, lo, hi);
            if (keys[mid] > keys[hi]) swap(xs, ys, zs, atoms, mid, hi);
            // Now: keys[lo] <= keys[mid] <= keys[hi].
            // keys[lo] is left sentinel (<=pivot), keys[hi] is right sentinel (>=pivot).

            // Park pivot (keys[mid]) at hi-1
            swap(xs, ys, zs, atoms, mid, hi - 1);
            double pivot = keys[hi - 1];

            // Partition: scan inward from lo+1 and hi-2.
            // Sentinels guarantee: left scan stops at or before hi, right scan stops at or after lo.
            int i = lo;
            int j = hi - 1;
            while (true) {
                while (keys[++i] < pivot) {} // stops at keys[hi] sentinel (>=pivot)
                while (keys[--j] > pivot) {} // stops at keys[lo] sentinel (<=pivot)
                if (i >= j) break;
                swap(xs, ys, zs, atoms, i, j);
            }
            swap(xs, ys, zs, atoms, i, hi - 1); // restore pivot to final position

            // After partition: keys[lo..i-1] <= pivot, keys[i] == pivot, keys[i+1..hi] >= pivot.
            // Expand the equal region around position i to skip duplicates.
            // For random data: region is [i, i] (2 extra comparisons, negligible).
            // For all-equal data: region is [lo, hi] → return immediately, O(N) total.
            int eqLo = i;
            while (eqLo > lo && keys[eqLo - 1] == pivot) eqLo--;
            int eqHi = i;
            while (eqHi < hi && keys[eqHi + 1] == pivot) eqHi++;

            // Narrow search to the region containing k
            if (k >= eqLo && k <= eqHi) return; // k in equal region — done
            if (k < eqLo) hi = eqLo - 1;
            else           lo = eqHi + 1;
        }
    }

    /** Get coordinate value by dimension index. Inlined by JIT. */
    private static double getCoord(double[] xs, double[] ys, double[] zs, int i, int dim) {
        return dim == 0 ? xs[i] : dim == 1 ? ys[i] : zs[i];
    }

    /** Swap elements at indices i and j across all 4 parallel arrays. */
    private static void swap(double[] xs, double[] ys, double[] zs, Atom[] atoms, int i, int j) {
        double t;
        t = xs[i]; xs[i] = xs[j]; xs[j] = t;
        t = ys[i]; ys[i] = ys[j]; ys[j] = t;
        t = zs[i]; zs[i] = zs[j]; zs[j] = t;
        Atom a = atoms[i]; atoms[i] = atoms[j]; atoms[j] = a;
    }

    // ==================== Distance Functions ====================

    /**
     * Squared Euclidean distance between two 3D points.
     * Private static → JIT-guaranteed inline. No NaN checks (protein coords are always valid).
     */
    private static double sqrDist(double x1, double y1, double z1,
                                  double x2, double y2, double z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Squared distance from point (qx,qy,qz) to axis-aligned bounding box.
     * Returns 0 if point is inside the box. Used for subtree pruning.
     * Per-axis computation avoids dimension-index branching.
     */
    private static double sqrDistToBox(double qx, double qy, double qz,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double d = 0;
        if (qx < minX) { double dx = qx - minX; d += dx * dx; }
        else if (qx > maxX) { double dx = qx - maxX; d += dx * dx; }
        if (qy < minY) { double dy = qy - minY; d += dy * dy; }
        else if (qy > maxY) { double dy = qy - maxY; d += dy * dy; }
        if (qz < minZ) { double dz = qz - minZ; d += dz * dz; }
        else if (qz > maxZ) { double dz = qz - maxZ; d += dz * dz; }
        return d;
    }

    // ==================== Queries ====================

    /**
     * Find the nearest atom to (qx, qy, qz).
     * Returns null if tree is empty.
     * Zero heap allocation — tracks best candidate in local variables.
     * Stack-based traversal — no mutable tree state, thread-safe.
     */
    public Atom findNearest(double qx, double qy, double qz) {
        if (size == 0) return null;

        // Traversal stack. Max depth = 2 * tree_depth (push both children).
        // For 100k points: depth ~14, stack ~30 entries = ~120 bytes. Fits in L1.
        int[] stack = new int[64];
        int sp = 0;
        stack[sp++] = 0; // start at root

        double bestSqrDist = Double.POSITIVE_INFINITY;
        Atom bestAtom = null;

        while (sp > 0) {
            int node = stack[--sp];
            if (node >= nodeCount) continue;

            // Prune: if this node's bounding box is farther than current best, skip
            if (sqrDistToBox(qx, qy, qz,
                    minXs[node], minYs[node], minZs[node],
                    maxXs[node], maxYs[node], maxZs[node]) >= bestSqrDist) {
                continue;
            }

            // Leaf: scan all points in bucket
            if (leafStart[node] >= 0) {
                int start = leafStart[node];
                int end = leafEnd[node];
                for (int i = start; i < end; i++) {
                    double d = sqrDist(qx, qy, qz, xs[i], ys[i], zs[i]);
                    if (d < bestSqrDist) {
                        bestSqrDist = d;
                        bestAtom = atoms[i];
                    }
                }
                continue;
            }

            // Internal node: push children. Visit nearer child last (processed first from stack).
            int left = 2 * node + 1;
            int right = 2 * node + 2;
            double qVal = splitDims[node] == 0 ? qx : splitDims[node] == 1 ? qy : qz;

            if (qVal <= splitVals[node]) {
                // Query is on the left side — visit left first (push right, then left)
                if (right < nodeCount) stack[sp++] = right;
                if (left < nodeCount) stack[sp++] = left;
            } else {
                // Query is on the right side — visit right first
                if (left < nodeCount) stack[sp++] = left;
                if (right < nodeCount) stack[sp++] = right;
            }
        }

        return bestAtom;
    }

    /**
     * Squared distance to the nearest point.
     * Returns Double.POSITIVE_INFINITY if tree is empty.
     */
    public double nearestSqrDist(double qx, double qy, double qz) {
        if (size == 0) return Double.POSITIVE_INFINITY;

        int[] stack = new int[64];
        int sp = 0;
        stack[sp++] = 0;

        double bestSqrDist = Double.POSITIVE_INFINITY;

        while (sp > 0) {
            int node = stack[--sp];
            if (node >= nodeCount) continue;

            if (sqrDistToBox(qx, qy, qz,
                    minXs[node], minYs[node], minZs[node],
                    maxXs[node], maxYs[node], maxZs[node]) >= bestSqrDist) {
                continue;
            }

            if (leafStart[node] >= 0) {
                int start = leafStart[node];
                int end = leafEnd[node];
                for (int i = start; i < end; i++) {
                    double d = sqrDist(qx, qy, qz, xs[i], ys[i], zs[i]);
                    if (d < bestSqrDist) {
                        bestSqrDist = d;
                    }
                }
                continue;
            }

            int left = 2 * node + 1;
            int right = 2 * node + 2;
            double qVal = splitDims[node] == 0 ? qx : splitDims[node] == 1 ? qy : qz;

            if (qVal <= splitVals[node]) {
                if (right < nodeCount) stack[sp++] = right;
                if (left < nodeCount) stack[sp++] = left;
            } else {
                if (left < nodeCount) stack[sp++] = left;
                if (right < nodeCount) stack[sp++] = right;
            }
        }

        return bestSqrDist;
    }

    /**
     * Find all atoms within squared radius of (qx, qy, qz).
     *
     * Returns Atoms directly — no Entry wrapper objects (unlike v1 which allocates
     * new Entry<>() per result, ~100 per query for typical 6Å radius).
     * This is the hot path: called thousands of times per protein via Atoms.cutoutSphereKD().
     *
     * @param sqrRadius squared radius (caller squares it once, avoids sqrt in distance checks)
     */
    public Atoms findWithinRadius(double qx, double qy, double qz, double sqrRadius) {
        if (size == 0) return new Atoms(0);

        // Pre-size result list. Typical radius query returns ~50-150 atoms.
        List<Atom> result = new ArrayList<>(64);

        int[] stack = new int[64];
        int sp = 0;
        stack[sp++] = 0;

        while (sp > 0) {
            int node = stack[--sp];
            if (node >= nodeCount) continue;

            // Prune: entire subtree's bounding box is outside the search sphere
            if (sqrDistToBox(qx, qy, qz,
                    minXs[node], minYs[node], minZs[node],
                    maxXs[node], maxYs[node], maxZs[node]) > sqrRadius) {
                continue;
            }

            // Leaf: check each point against radius
            if (leafStart[node] >= 0) {
                int start = leafStart[node];
                int end = leafEnd[node];
                for (int i = start; i < end; i++) {
                    double d = sqrDist(qx, qy, qz, xs[i], ys[i], zs[i]);
                    if (d <= sqrRadius) {
                        result.add(atoms[i]);
                    }
                }
                continue;
            }

            // Internal: push both children (no ordering needed for radius search)
            int left = 2 * node + 1;
            int right = 2 * node + 2;
            if (right < nodeCount) stack[sp++] = right;
            if (left < nodeCount) stack[sp++] = left;
        }

        return new Atoms(result);
    }

    /**
     * Count points within sqrRadius of the query, WITHOUT materializing a result list.
     *
     * Equivalent to {@code findWithinRadius(...).getCount()} but allocation-free: it is
     * called once per SAS point by the protrusion feature with a large radius (~10 A),
     * where the result routinely exceeds the result list's initial capacity and the only
     * thing the caller needs is the count.
     *
     * @param sqrRadius squared radius
     */
    public int countWithinRadius(double qx, double qy, double qz, double sqrRadius) {
        if (size == 0) return 0;

        int count = 0;

        int[] stack = new int[64];
        int sp = 0;
        stack[sp++] = 0;

        while (sp > 0) {
            int node = stack[--sp];
            if (node >= nodeCount) continue;

            // Prune: entire subtree's bounding box is outside the search sphere
            if (sqrDistToBox(qx, qy, qz,
                    minXs[node], minYs[node], minZs[node],
                    maxXs[node], maxYs[node], maxZs[node]) > sqrRadius) {
                continue;
            }

            // Leaf: check each point against radius
            if (leafStart[node] >= 0) {
                int start = leafStart[node];
                int end = leafEnd[node];
                for (int i = start; i < end; i++) {
                    double d = sqrDist(qx, qy, qz, xs[i], ys[i], zs[i]);
                    if (d <= sqrRadius) {
                        count++;
                    }
                }
                continue;
            }

            // Internal: push both children (no ordering needed for radius search)
            int left = 2 * node + 1;
            int right = 2 * node + 2;
            if (right < nodeCount) stack[sp++] = right;
            if (left < nodeCount) stack[sp++] = left;
        }

        return count;
    }

    /**
     * Find k nearest neighbors.
     *
     * Uses a max-heap (ResultHeap) to track the k closest points seen so far.
     * The heap's max distance serves as the pruning radius during traversal.
     *
     * @param sorted if true, results are sorted by ascending distance
     * @return list of NNEntry records with squared distances
     */
    public List<NNEntry> findNearestN(double qx, double qy, double qz, int count, boolean sorted) {
        if (size == 0 || count <= 0) return List.of();
        count = Math.min(count, size);

        ResultHeap heap = new ResultHeap(count);

        int[] stack = new int[64];
        int sp = 0;
        stack[sp++] = 0;

        while (sp > 0) {
            int node = stack[--sp];
            if (node >= nodeCount) continue;

            double boxDist = sqrDistToBox(qx, qy, qz,
                    minXs[node], minYs[node], minZs[node],
                    maxXs[node], maxYs[node], maxZs[node]);
            if (boxDist >= heap.maxDist()) continue;

            if (leafStart[node] >= 0) {
                int start = leafStart[node];
                int end = leafEnd[node];
                for (int i = start; i < end; i++) {
                    double d = sqrDist(qx, qy, qz, xs[i], ys[i], zs[i]);
                    heap.offer(d, atoms[i]);
                }
                continue;
            }

            int left = 2 * node + 1;
            int right = 2 * node + 2;
            double qVal = splitDims[node] == 0 ? qx : splitDims[node] == 1 ? qy : qz;

            if (qVal <= splitVals[node]) {
                if (right < nodeCount) stack[sp++] = right;
                if (left < nodeCount) stack[sp++] = left;
            } else {
                if (left < nodeCount) stack[sp++] = left;
                if (right < nodeCount) stack[sp++] = right;
            }
        }

        return heap.toList(sorted);
    }

    // ==================== ResultHeap (for k-NN) ====================

    /**
     * Fixed-capacity max-heap tracking the k nearest neighbors.
     * maxDist() returns the distance to the farthest point in the heap
     * (or POSITIVE_INFINITY if the heap is not yet full).
     * Used as the pruning radius during k-NN traversal.
     */
    private static final class ResultHeap {
        private final double[] distances;
        private final Atom[] atoms;
        private final int capacity;
        private int size;

        ResultHeap(int capacity) {
            this.distances = new double[capacity];
            this.atoms = new Atom[capacity];
            this.capacity = capacity;
            this.size = 0;
        }

        double maxDist() {
            return size < capacity ? Double.POSITIVE_INFINITY : distances[0];
        }

        void offer(double dist, Atom atom) {
            if (size < capacity) {
                // Heap not full: insert and bubble up
                distances[size] = dist;
                atoms[size] = atom;
                siftUp(size);
                size++;
            } else if (dist < distances[0]) {
                // Heap full and this is closer than the farthest: replace root
                distances[0] = dist;
                atoms[0] = atom;
                siftDown(0);
            }
        }

        /** Extract results. If sorted=true, returns ascending by distance. */
        List<NNEntry> toList(boolean sorted) {
            if (!sorted) {
                List<NNEntry> result = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    result.add(new NNEntry(distances[i], atoms[i]));
                }
                return result;
            }

            // Sort by extracting max repeatedly (heap sort, descending) then reverse
            NNEntry[] arr = new NNEntry[size];
            int origSize = size;
            for (int i = origSize - 1; i >= 0; i--) {
                arr[i] = new NNEntry(distances[0], atoms[0]);
                size--;
                if (size > 0) {
                    distances[0] = distances[size];
                    atoms[0] = atoms[size];
                    siftDown(0);
                }
            }
            size = origSize;
            return List.of(arr);
        }

        private void siftUp(int c) {
            while (c > 0) {
                int p = (c - 1) / 2;
                if (distances[c] <= distances[p]) break;
                // Swap child and parent
                double td = distances[c]; distances[c] = distances[p]; distances[p] = td;
                Atom ta = atoms[c]; atoms[c] = atoms[p]; atoms[p] = ta;
                c = p;
            }
        }

        private void siftDown(int p) {
            while (true) {
                int c = 2 * p + 1;
                if (c >= size) break;
                // Pick the larger child
                if (c + 1 < size && distances[c] < distances[c + 1]) c++;
                if (distances[p] >= distances[c]) break;
                // Swap
                double td = distances[p]; distances[p] = distances[c]; distances[c] = td;
                Atom ta = atoms[p]; atoms[p] = atoms[c]; atoms[c] = ta;
                p = c;
            }
        }
    }
}
