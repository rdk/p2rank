package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Long-format export of {@link PocketGrid}: one row per (point, pocket) pair.
 *
 * <p>A grid point assigned to K pockets contributes K rows. Unassigned points
 * appear once with {@code pocket = 0} only if {@code includeUnassigned} is on.
 *
 * <p>Sort order (documented spec contract): {@code pocket} ascending, then
 * {@code x}, {@code y}, {@code z} ascending. Unassigned rows (if included) go
 * last so readers that only care about assigned points can stop early.
 */
@CompileStatic
final class PocketGridRows implements TableData {

    private static final List<String> HEADER = ['x', 'y', 'z', 'pocket'].asImmutable()

    private final PocketGrid grid
    /** Parallel arrays: one entry per output row. */
    private final int[] rowPointIdx
    private final int[] rowPocket

    PocketGridRows(PocketGrid grid, boolean includeUnassigned) {
        this.grid = grid

        // Compute the union of assigned point indices (across pockets) — sizes the
        // output and identifies unassigned points when enabled.
        BitSet assignedUnion = new BitSet(grid.allPoints.count)
        int totalMemberships = 0  // (point, pocket) pairs
        for (BitSet bs : grid.pocketToPointIndices.values()) {
            // Manually OR — Groovy's a.or(b) under @CompileStatic doesn't reliably
            // call BitSet#or; it can route through Number.or-style operator overloading.
            for (int b = bs.nextSetBit(0); b >= 0; b = bs.nextSetBit(b + 1)) {
                assignedUnion.set(b)
            }
            totalMemberships += bs.cardinality()
        }
        int unassignedCount = includeUnassigned ? grid.allPoints.count - assignedUnion.cardinality() : 0

        rowPointIdx = new int[totalMemberships + unassignedCount]
        rowPocket = new int[rowPointIdx.length]

        // Write pocket rows in rank order, each sorted by (x, y, z); unassigned (pocket=0) last.
        List<Atom> allPoints = grid.allPoints.list
        int w = 0
        List<Integer> ranks = new ArrayList<>(grid.pocketToPointIndices.keySet())
        Collections.sort(ranks)
        for (Integer rank : ranks) {
            BitSet bs = grid.pocketToPointIndices.get(rank)
            List<Integer> sorted = new ArrayList<>(bs.cardinality())
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) sorted.add(i)
            sortByCoord(sorted, allPoints)
            for (Integer idx : sorted) {
                rowPointIdx[w] = idx
                rowPocket[w] = rank
                w++
            }
        }
        if (includeUnassigned) {
            List<Integer> unassigned = new ArrayList<>(unassignedCount)
            for (int i = 0; i < allPoints.size(); i++) {
                if (!assignedUnion.get(i)) unassigned.add(i)
            }
            sortByCoord(unassigned, allPoints)
            for (Integer idx : unassigned) {
                rowPointIdx[w] = idx
                rowPocket[w] = 0
                w++
            }
        }
    }

    private static void sortByCoord(List<Integer> indices, List<Atom> allPoints) {
        Collections.sort(indices, { Integer a, Integer b ->
            Atom pa = allPoints.get(a.intValue())
            Atom pb = allPoints.get(b.intValue())
            int c = Double.compare(pa.x, pb.x)
            if (c != 0) return c
            c = Double.compare(pa.y, pb.y)
            if (c != 0) return c
            return Double.compare(pa.z, pb.z)
        } as Comparator<Integer>)
    }

    @Override List<String> getHeader() { HEADER }

    @Override int getRowCount() { rowPointIdx.length }

    @Override
    double[] getRow(int index) {
        int pointIdx = rowPointIdx[index]
        Atom p = grid.allPoints.list[pointIdx]
        return [p.x, p.y, p.z, (double) rowPocket[index]] as double[]
    }

    @Override
    double[] getColumn(int colIndex) {
        int n = rowPointIdx.length
        double[] out = new double[n]
        if (colIndex == 3) {
            for (int i = 0; i < n; i++) out[i] = rowPocket[i]
            return out
        }
        List<Atom> allPoints = grid.allPoints.list
        for (int i = 0; i < n; i++) {
            Atom p = allPoints.get(rowPointIdx[i])
            out[i] = colIndex == 0 ? p.x : colIndex == 1 ? p.y : p.z
        }
        return out
    }

    @Override
    ColumnType getColumnType(int colIndex) {
        return colIndex == 3 ? ColumnType.INT : ColumnType.DOUBLE
    }

}
