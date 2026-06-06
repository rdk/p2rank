package cz.siret.prank.program.routines.predict.output;

import cz.siret.prank.domain.Pocket;
import cz.siret.prank.domain.Protein;
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointContext;
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptor;
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptorRegistry;
import org.biojava.nbio.structure.Atom;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-format export of {@link PocketGrid}: one row per (point, pocket) pair.
 *
 * <p>A grid point assigned to K pockets contributes K rows. Unassigned points
 * appear once with {@code pocket = 0} only when {@code includeUnassigned} is on
 * (driven by {@code -pocket_grid_include_unassigned}); otherwise they are
 * skipped. This is the tabular export only — the PDB visualization sidecar is a
 * separate writer that always emits assigned points exclusively.
 *
 * <p>Sort order (documented spec contract): {@code pocket} ascending, then
 * {@code x}, {@code y}, {@code z} ascending. Unassigned rows (if included) go
 * last so readers that only care about assigned points can stop early.
 *
 * <p>Base schema: {@code x, y, z, pocket}. Each entry in {@code descriptors}
 * appends one or more columns; multi-column descriptors get the
 * {@code "{name}."} header prefix. Values are pre-computed once at
 * construction.
 *
 * <p>Java (not Groovy) on purpose: this is the per-(point, pocket) export hot
 * loop, run over whole datasets when {@code export_pocket_grid} is on, and it
 * does BitSet iteration + descriptor dispatch per row.
 */
public final class PocketGridRows implements TableData {

    private static final List<String> BASE_HEADER = List.of("x", "y", "z", "pocket");
    private static final int BASE_COLS = 4;

    private final PocketGrid grid;
    /** Parallel arrays: one entry per output row. */
    private final int[] rowPointIdx;
    private final int[] rowPocket;

    private final List<String> header;
    private final ColumnType[] columnTypes;
    /** [rowIndex][descriptorColumn] — flat across all descriptors; null when no descriptors. */
    private final double[][] descriptorValues;

    public PocketGridRows(PocketGrid grid, boolean includeUnassigned, Protein protein,
                          List<? extends Pocket> pockets,
                          List<String> descriptorNames) {
        List<PocketGridPointDescriptor> descriptors = resolveDescriptors(descriptorNames);
        this.grid = grid;

        // Contract: unassigned (pocket=0) rows carry a null Pocket in the context, so they
        // can only be emitted alongside descriptors that never read ctx.pocket(). Enforce it
        // at construction rather than NPE deep in the per-row loop only when both happen to
        // be set. The interface default isPocketAgnostic()==false, so a future per-pocket
        // descriptor trips this immediately instead of silently.
        if (includeUnassigned) {
            for (PocketGridPointDescriptor d : descriptors) {
                if (!d.isPocketAgnostic()) {
                    throw new IllegalArgumentException(
                            "pocket_grid_include_unassigned is incompatible with non-pocket-agnostic descriptor '"
                            + d.name() + "': unassigned rows (pocket=0) have no pocket to read");
                }
            }
        }

        // Union of assigned point indices (across pockets) — sizes the output and, when
        // includeUnassigned is on, identifies the leftover points. Plain BitSet.or here:
        // this is Java, so it mutates in place (the Groovy DefaultGroovyMethods no-op trap
        // does not apply to .java sources).
        BitSet assignedUnion = new BitSet(grid.getAllPoints().getCount());
        int totalMemberships = 0;  // (point, pocket) pairs
        for (BitSet bs : grid.getPocketToPointIndices().values()) {
            assignedUnion.or(bs);
            totalMemberships += bs.cardinality();
        }
        int unassignedCount = includeUnassigned
                ? grid.getAllPoints().getCount() - assignedUnion.cardinality()
                : 0;

        rowPointIdx = new int[totalMemberships + unassignedCount];
        rowPocket = new int[rowPointIdx.length];

        // Write pocket rows in rank order, each sorted by (x, y, z); unassigned (pocket=0) last.
        List<Atom> allPoints = grid.getAllPoints().list;
        int w = 0;
        List<Integer> ranks = new ArrayList<>(grid.getPocketToPointIndices().keySet());
        Collections.sort(ranks);
        for (Integer rank : ranks) {
            BitSet bs = grid.getPocketToPointIndices().get(rank);
            List<Integer> sorted = new ArrayList<>(bs.cardinality());
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) sorted.add(i);
            sortByCoord(sorted, allPoints);
            for (Integer idx : sorted) {
                rowPointIdx[w] = idx;
                rowPocket[w] = rank;
                w++;
            }
        }
        if (includeUnassigned) {
            List<Integer> unassigned = new ArrayList<>(unassignedCount);
            for (int i = 0; i < allPoints.size(); i++) {
                if (!assignedUnion.get(i)) unassigned.add(i);
            }
            sortByCoord(unassigned, allPoints);
            for (Integer idx : unassigned) {
                rowPointIdx[w] = idx;
                rowPocket[w] = 0;   // sentinel: unassigned (pocket ranks are 1-based)
                w++;
            }
        }

        List<String> h = new ArrayList<>(BASE_HEADER);
        List<ColumnType> ct = new ArrayList<>();
        ct.add(ColumnType.DOUBLE); ct.add(ColumnType.DOUBLE); ct.add(ColumnType.DOUBLE);
        ct.add(ColumnType.INT);  // pocket
        int totalDescriptorCols = 0;
        for (PocketGridPointDescriptor d : descriptors) {
            totalDescriptorCols += DescriptorSchemaHelper.appendColumns(
                    h, ct, d.name(), d.columnNames(), d.columnTypes());
        }
        this.header = Collections.unmodifiableList(h);
        this.columnTypes = ct.toArray(new ColumnType[0]);

        if (descriptors.isEmpty()) {
            this.descriptorValues = null;
        } else {
            // Build rank → Pocket lookup; pocket=0 (unassigned, when includeUnassigned
            // is on) maps to null. The constructor guard above guarantees that whenever
            // includeUnassigned is on every descriptor is pocket-agnostic (never reads
            // ctx.pocket()), so the null is never dereferenced — see the cost note in
            // export-pocket-grid.md.
            Map<Integer, Pocket> rankToPocket = new HashMap<>();
            if (pockets != null) {
                for (Pocket p : pockets) {
                    rankToPocket.put(p.getRank(), p);
                }
            }

            // Per-(descriptor, point) memo for pocket-agnostic descriptors so a point
            // shared across multiple pockets computes once. Inner arrays are null for
            // non-agnostic descriptors — the inner-loop null-check selects the path.
            int descCount = descriptors.size();
            PocketGridPointDescriptor[] descArr = descriptors.toArray(new PocketGridPointDescriptor[0]);
            // Column counts hoisted once; the runner trusts each descriptor's
            // compute() to write exactly descCols[d] doubles starting at
            // out[descOffsets[d]]. A descriptor that miscounts overruns into
            // the next descriptor's columns silently — descCols sourced from
            // the same columnNames() the header builder used pins this contract.
            int[] descCols = new int[descCount];
            int[] descOffsets = new int[descCount];
            int runningOffset = 0;
            for (int d = 0; d < descCount; d++) {
                descCols[d] = descArr[d].columnNames().size();
                descOffsets[d] = runningOffset;
                runningOffset += descCols[d];
            }
            if (runningOffset != totalDescriptorCols) {
                throw new IllegalStateException("descriptor column-count mismatch");
            }
            double[][][] agnosticCache = new double[descCount][][];
            int pointCount = grid.getAllPoints().getCount();
            for (int d = 0; d < descCount; d++) {
                if (descArr[d].isPocketAgnostic()) agnosticCache[d] = new double[pointCount][];
            }

            // One pooled context reused for every row. Descriptors must not retain
            // references past their compute() call — documented contract on
            // PocketGridPointContext.
            PocketGridPointContext ctx = new PocketGridPointContext();

            this.descriptorValues = new double[rowPointIdx.length][totalDescriptorCols];
            // Rows are emitted in pocket-rank order (see the sort above), so the
            // rank-to-pocket lookup repeats across runs of equal-rank rows. Hoist
            // it across the run so each rank is resolved once, not per row.
            int currentRank = -1;
            Pocket pocket = null;
            for (int i = 0; i < rowPointIdx.length; i++) {
                int pointIdx = rowPointIdx[i];
                int pocketRank = rowPocket[i];
                if (pocketRank != currentRank) {
                    currentRank = pocketRank;
                    pocket = rankToPocket.get(pocketRank);
                }
                Atom point = allPoints.get(pointIdx);
                ctx.reset(pointIdx, point, pocketRank, pocket, protein, grid);
                double[] rowOut = descriptorValues[i];
                for (int d = 0; d < descCount; d++) {
                    int off = descOffsets[d];
                    int len = descCols[d];
                    double[][] dCache = agnosticCache[d];
                    if (dCache != null) {
                        double[] cached = dCache[pointIdx];
                        if (cached == null) {
                            cached = new double[len];
                            descArr[d].compute(ctx, cached, 0);
                            dCache[pointIdx] = cached;
                        }
                        System.arraycopy(cached, 0, rowOut, off, len);
                    } else {
                        // Non-agnostic: write directly into the output row.
                        descArr[d].compute(ctx, rowOut, off);
                    }
                }
            }
        }
    }

    private static void sortByCoord(List<Integer> indices, List<Atom> allPoints) {
        indices.sort((Comparator<Integer>) (a, b) -> {
            Atom pa = allPoints.get(a);
            Atom pb = allPoints.get(b);
            int c = Double.compare(pa.getX(), pb.getX());
            if (c != 0) return c;
            c = Double.compare(pa.getY(), pb.getY());
            if (c != 0) return c;
            return Double.compare(pa.getZ(), pb.getZ());
        });
    }

    @Override public List<String> getHeader() { return header; }

    @Override public int getRowCount() { return rowPointIdx.length; }

    @Override
    public double[] getRow(int index) {
        int pointIdx = rowPointIdx[index];
        Atom p = grid.getAllPoints().list.get(pointIdx);
        double[] row = new double[header.size()];
        row[0] = p.getX(); row[1] = p.getY(); row[2] = p.getZ();
        row[3] = (double) rowPocket[index];
        if (descriptorValues != null) {
            double[] descVals = descriptorValues[index];
            for (int k = 0; k < descVals.length; k++) row[BASE_COLS + k] = descVals[k];
        }
        return row;
    }

    @Override
    public double[] getColumn(int colIndex) {
        int n = rowPointIdx.length;
        double[] out = new double[n];
        if (colIndex == BASE_COLS - 1) {  // pocket column — INT
            for (int i = 0; i < n; i++) out[i] = rowPocket[i];
            return out;
        }
        if (colIndex < BASE_COLS) {
            List<Atom> allPoints = grid.getAllPoints().list;
            for (int i = 0; i < n; i++) {
                Atom p = allPoints.get(rowPointIdx[i]);
                out[i] = colIndex == 0 ? p.getX() : colIndex == 1 ? p.getY() : p.getZ();
            }
            return out;
        }
        // Descriptor column: descriptorValues is non-null whenever any descriptor column exists
        // (the schema-build branches together with the pre-compute branch).
        int descCol = colIndex - BASE_COLS;
        for (int i = 0; i < n; i++) out[i] = descriptorValues[i][descCol];
        return out;
    }

    @Override
    public ColumnType getColumnType(int colIndex) {
        return columnTypes[colIndex];
    }

    private static List<PocketGridPointDescriptor> resolveDescriptors(List<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        List<PocketGridPointDescriptor> out = new ArrayList<>(names.size());
        for (String n : names) out.add(PocketGridPointDescriptorRegistry.get(n));
        return out;
    }

}
