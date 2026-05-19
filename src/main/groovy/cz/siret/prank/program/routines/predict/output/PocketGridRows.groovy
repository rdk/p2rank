package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointContext
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptor
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptorRegistry
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
 *
 * <p>Base schema: {@code x, y, z, pocket}. Each entry in {@code descriptors}
 * appends one or more columns; multi-column descriptors get the
 * {@code "{name}."} header prefix. Values are pre-computed once at
 * construction.
 */
@CompileStatic
final class PocketGridRows implements TableData {

    private static final List<String> BASE_HEADER = ['x', 'y', 'z', 'pocket'].asImmutable()
    private static final int BASE_COLS = 4

    private final PocketGrid grid
    /** Parallel arrays: one entry per output row. */
    private final int[] rowPointIdx
    private final int[] rowPocket

    private final List<String> header
    private final ColumnType[] columnTypes
    /** [rowIndex][descriptorColumn] — flat across all descriptors; null when no descriptors. */
    private final double[][] descriptorValues

    PocketGridRows(PocketGrid grid, boolean includeUnassigned,
                   Protein protein, List<? extends Pocket> pockets,
                   List<String> descriptorNames) {
        List<PocketGridPointDescriptor> descriptors = resolveDescriptors(descriptorNames)
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

        List<String> h = new ArrayList<>(BASE_HEADER)
        List<ColumnType> ct = new ArrayList<>()
        ct.add(ColumnType.DOUBLE); ct.add(ColumnType.DOUBLE); ct.add(ColumnType.DOUBLE)
        ct.add(ColumnType.INT)  // pocket
        int totalDescriptorCols = 0
        for (PocketGridPointDescriptor d : descriptors) {
            totalDescriptorCols += DescriptorSchemaHelper.appendColumns(
                    h, ct, d.name(), d.columnNames(), d.columnTypes())
        }
        this.header = h.asImmutable()
        this.columnTypes = ct.toArray(new ColumnType[0])

        if (descriptors.isEmpty()) {
            this.descriptorValues = null
        } else {
            // Build rank → Pocket lookup; pocket=0 (unassigned) maps to null.
            Map<Integer, Pocket> rankToPocket = new HashMap<>()
            if (pockets != null) {
                for (Pocket p : pockets) {
                    rankToPocket.put(p.rank, p)
                }
            }
            this.descriptorValues = new double[rowPointIdx.length][totalDescriptorCols]
            for (int i = 0; i < rowPointIdx.length; i++) {
                int pointIdx = rowPointIdx[i]
                int pocketRank = rowPocket[i]
                Atom point = allPoints.get(pointIdx)
                Pocket pocket = pocketRank == 0 ? null : rankToPocket.get(pocketRank)
                PocketGridPointContext ctx = new PocketGridPointContext(
                        pointIdx, point, pocketRank, pocket, protein, grid)
                int col = 0
                for (PocketGridPointDescriptor d : descriptors) {
                    double[] vals = d.compute(ctx)
                    for (int k = 0; k < vals.length; k++) {
                        descriptorValues[i][col++] = vals[k]
                    }
                }
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

    @Override List<String> getHeader() { header }

    @Override int getRowCount() { rowPointIdx.length }

    @Override
    double[] getRow(int index) {
        int pointIdx = rowPointIdx[index]
        Atom p = grid.allPoints.list[pointIdx]
        double[] row = new double[header.size()]
        row[0] = p.x; row[1] = p.y; row[2] = p.z
        row[3] = (double) rowPocket[index]
        if (descriptorValues != null) {
            double[] descVals = descriptorValues[index]
            for (int k = 0; k < descVals.length; k++) row[BASE_COLS + k] = descVals[k]
        }
        return row
    }

    @Override
    double[] getColumn(int colIndex) {
        int n = rowPointIdx.length
        double[] out = new double[n]
        if (colIndex == BASE_COLS - 1) {  // pocket column — INT
            for (int i = 0; i < n; i++) out[i] = rowPocket[i]
            return out
        }
        if (colIndex < BASE_COLS) {
            List<Atom> allPoints = grid.allPoints.list
            for (int i = 0; i < n; i++) {
                Atom p = allPoints.get(rowPointIdx[i])
                out[i] = colIndex == 0 ? p.x : colIndex == 1 ? p.y : p.z
            }
            return out
        }
        // Descriptor column: descriptorValues is non-null whenever any descriptor column exists
        // (the schema-build branches together with the pre-compute branch).
        int descCol = colIndex - BASE_COLS
        for (int i = 0; i < n; i++) out[i] = descriptorValues[i][descCol]
        return out
    }

    @Override
    ColumnType getColumnType(int colIndex) {
        return columnTypes[colIndex]
    }

    private static List<PocketGridPointDescriptor> resolveDescriptors(List<String> names) {
        if (names == null || names.isEmpty()) return Collections.<PocketGridPointDescriptor> emptyList()
        List<PocketGridPointDescriptor> out = new ArrayList<>(names.size())
        for (String n : names) out.add(PocketGridPointDescriptorRegistry.get(n))
        return out
    }

}
