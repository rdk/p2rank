package cz.siret.prank.geom.spatial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Generic uniform-cell 3D spatial hash for fast fixed-radius queries.
 *
 * Points are bucketed into cubic cells of side {@code cellSize}. A query for
 * "is any inserted point within {@code radius} of (x,y,z)?" scans only the cells
 * overlapping the query sphere. When {@code radius <= cellSize} (the supported
 * range) that is the 3x3x3 neighbourhood of the query cell, because any point
 * within {@code cellSize} along an axis can differ by at most one cell index on
 * that axis. The check is therefore O(points in 27 cells), i.e. O(1) for
 * roughly-uniform point sets, and O(N) to process N points incrementally.
 *
 * Not thread-safe (single-threaded build and query). Reusable for any fixed-radius
 * spatial task: point de-duplication, neighbour-existence checks, clustering seeds,
 * a lightweight alternative to a k-d-tree when the query radius is bounded.
 *
 * @param <T> payload stored alongside each inserted point
 */
public final class UniformGrid3D<T> {

    private final double cellSize;
    private final Map<Long, List<Entry<T>>> cells = new HashMap<>();
    private int size = 0;

    private static final class Entry<T> {
        final double x, y, z;
        final T payload;
        Entry(double x, double y, double z, T payload) {
            this.x = x; this.y = y; this.z = z; this.payload = payload;
        }
    }

    public UniformGrid3D(double cellSize) {
        if (!(cellSize > 0)) {
            throw new IllegalArgumentException("cellSize must be > 0, got " + cellSize);
        }
        this.cellSize = cellSize;
    }

    public int size() {
        return size;
    }

    public void insert(double x, double y, double z, T payload) {
        long key = cellKey(cellIndex(x), cellIndex(y), cellIndex(z));
        cells.computeIfAbsent(key, k -> new ArrayList<>(2)).add(new Entry<>(x, y, z, payload));
        size++;
    }

    /**
     * @return true if any inserted point lies within (Euclidean) {@code radius} of (x,y,z).
     *         {@code radius} must be {@code <= cellSize}.
     */
    public boolean hasAnyWithin(double x, double y, double z, double radius) {
        return scan(x, y, z, radius, null);
    }

    /**
     * Invoke {@code action} with the payload of every inserted point within {@code radius}
     * (which must be {@code <= cellSize}) of (x,y,z).
     */
    public void forEachWithin(double x, double y, double z, double radius, Consumer<? super T> action) {
        scan(x, y, z, radius, action);
    }

    // Existence query when action == null (returns on first hit); otherwise visits all
    // matches and returns whether any matched.
    private boolean scan(double x, double y, double z, double radius, Consumer<? super T> action) {
        if (radius > cellSize) {
            throw new IllegalArgumentException("radius (" + radius + ") must be <= cellSize (" + cellSize + ")");
        }
        double r2 = radius * radius;
        int cx = cellIndex(x), cy = cellIndex(y), cz = cellIndex(z);
        boolean found = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Entry<T>> bucket = cells.get(cellKey(cx + dx, cy + dy, cz + dz));
                    if (bucket == null) continue;
                    for (int i = 0, n = bucket.size(); i < n; i++) {
                        Entry<T> e = bucket.get(i);
                        double ex = e.x - x, ey = e.y - y, ez = e.z - z;
                        if (ex * ex + ey * ey + ez * ez <= r2) {
                            if (action == null) return true;
                            action.accept(e.payload);
                            found = true;
                        }
                    }
                }
            }
        }
        return found;
    }

    private int cellIndex(double coord) {
        return (int) Math.floor(coord / cellSize);
    }

    // Pack 3 signed cell indices into one long: 21 bits each (range ~ +-1,048,575 cells).
    // Distinct cells collide only if an index differs by a multiple of 2^21 cells, i.e.
    // ~2^21 * cellSize apart in space, which is far beyond any realistic coordinate range.
    private static long cellKey(int cx, int cy, int cz) {
        return ((long) (cx & 0x1FFFFF) << 42)
             | ((long) (cy & 0x1FFFFF) << 21)
             |  (long) (cz & 0x1FFFFF);
    }
}
