package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptor
import cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptorRegistry
import cz.siret.prank.program.routines.predict.output.descriptors.PocketGridContext
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Per-pocket descriptors table.
 *
 * <p>Schema:
 * <pre>
 *   name (STRING), rank (INT), score (DOUBLE),
 *   [probability (DOUBLE) — only when at least one pocket has a calibrated
 *    probaTP set by the score transformer],
 *   center_x (DOUBLE), center_y (DOUBLE), center_z (DOUBLE),
 *   &lt;descriptor1 col(s)&gt;, &lt;descriptor2 col(s)&gt;, ...
 * </pre>
 *
 * <p>Each descriptor contributes 1 or more columns. Scalar descriptors emit
 * one column headed by {@link PocketDescriptor#name()}; multi-column descriptors
 * emit N columns prefixed with {@code "{name()}."} (e.g. principal_moments emits
 * {@code principal_moments.lambda1}, {@code principal_moments.lambda2},
 * {@code principal_moments.lambda3}).
 *
 * <p>{@code includeProbability} is derived from the data — no caller has to
 * thread the flag through. {@code descriptorNames} ordering is preserved in
 * the output columns; columns within a multi-column descriptor follow the order
 * declared by {@link PocketDescriptor#columnNames()}.
 */
@CompileStatic
final class PocketDescriptorsRows implements TableData {

    private final List<? extends Pocket> pockets
    private final List<String> descriptorNames
    private final List<PocketDescriptor> descriptors
    private final boolean includeProbability
    private final List<String> header
    private final ColumnType[] columnTypes
    /** Pre-computed descriptor values per pocket: flat array of all descriptor columns. */
    private final double[][] descriptorValues
    /** Total number of descriptor columns across all descriptors (sum of arities). */
    private final int totalDescriptorCols

    PocketDescriptorsRows(List<? extends Pocket> pockets,
                                 List<String> descriptorNames,
                                 Protein protein,
                                 PocketGrid grid) {
        this.pockets = pockets
        // Tolerate a null descriptor list — treat it as "no descriptors", emitting
        // only the base columns. Rebind the local so the rest of the constructor
        // can iterate without a separate null-guard. The validator rejects
        // blank/unknown names inside a non-null list, so we don't re-filter here.
        if (descriptorNames == null) descriptorNames = Collections.<String> emptyList()
        this.descriptorNames = descriptorNames

        // Detect "transformer ran" by checking auxInfo.probaTP. Default is 0.0d
        // (Groovy double init); transformer outputs are typically > 0 even for
        // low-score pockets. A pocket genuinely transformed to exactly 0.0 is
        // possible but vanishingly rare with the existing transformers.
        boolean anyProba = false
        for (Pocket p : pockets) {
            if (p.auxInfo != null && p.auxInfo.probaTP > 0d) { anyProba = true; break }
        }
        this.includeProbability = anyProba

        // Resolve descriptor implementations (fail-fast at construction if name unknown).
        this.descriptors = new ArrayList<>(descriptorNames.size())
        for (String name : descriptorNames) {
            descriptors.add(PocketDescriptorRegistry.get(name))
        }

        // Honest contract: a null grid is acceptable only when every selected descriptor
        // declares needsGrid()=false. The upstream gate in PocketGridOutputs already
        // honors this, but this constructor is callable from elsewhere (tests, future
        // callers); fail loudly here so a descriptor whose needsGrid() lies surfaces
        // at construction time rather than via an NPE inside compute().
        if (grid == null) {
            for (PocketDescriptor d : descriptors) {
                if (d.needsGrid()) {
                    throw new cz.siret.prank.program.PrankException(
                            "Descriptor '${d.name()}' declares needsGrid()=true but a null " +
                            "PocketGrid was passed to PocketDescriptorsRows. Either build the " +
                            "grid upstream or drop this descriptor from -pocket_descriptors.")
                }
            }
        }

        // Build header + column types. Apply the "{name}.{col}" prefix rule for
        // multi-column descriptors; scalar descriptors get the bare name().
        List<String> h = new ArrayList<>()
        List<ColumnType> ct = new ArrayList<>()
        h.add('name');         ct.add(ColumnType.STRING)
        h.add('rank');         ct.add(ColumnType.INT)
        h.add('score');        ct.add(ColumnType.DOUBLE)
        if (includeProbability) {
            h.add('probability'); ct.add(ColumnType.DOUBLE)
        }
        h.add('center_x');     ct.add(ColumnType.DOUBLE)
        h.add('center_y');     ct.add(ColumnType.DOUBLE)
        h.add('center_z');     ct.add(ColumnType.DOUBLE)
        int total = 0
        for (PocketDescriptor d : descriptors) {
            total += DescriptorSchemaHelper.appendColumns(h, ct, d.name(), d.columnNames(), d.columnTypes())
        }
        this.header = h.asImmutable()
        this.columnTypes = ct.toArray(new ColumnType[0])
        this.totalDescriptorCols = total

        // Pre-compute descriptor values once. Flat layout: descriptorValues[pocket][col],
        // where col is the index into the flattened descriptor schema (in registration order).
        this.descriptorValues = new double[pockets.size()][totalDescriptorCols]
        for (int i = 0; i < pockets.size(); i++) {
            Pocket p = pockets.get(i)
            BitSet indices = grid != null ? grid.indicesForPocket(p.rank) : new BitSet()
            PocketGridContext ctx = new PocketGridContext(p, protein, grid, indices)
            int col = 0
            for (PocketDescriptor d : descriptors) {
                double[] vals = d.compute(ctx)
                for (int k = 0; k < vals.length; k++) {
                    descriptorValues[i][col++] = vals[k]
                }
            }
        }
    }

    @Override List<String> getHeader() { header }

    @Override int getRowCount() { pockets.size() }

    @Override
    double[] getRow(int index) {
        Pocket p = pockets.get(index)
        double[] row = new double[header.size()]
        int col = 0
        // Column 0 ('name') is STRING. The writer reads its value via getString(row, col),
        // so the numeric placeholder here doesn't reach the output; NaN flags it as unused.
        row[col++] = Double.NaN
        row[col++] = p.rank
        row[col++] = p.score
        if (includeProbability) {
            row[col++] = p.auxInfo != null ? p.auxInfo.probaTP : Double.NaN
        }
        Atom c = p.centroid
        row[col++] = c != null ? c.x : Double.NaN
        row[col++] = c != null ? c.y : Double.NaN
        row[col++] = c != null ? c.z : Double.NaN
        for (double v : descriptorValues[index]) {
            row[col++] = v
        }
        return row
    }

    @Override
    ColumnType getColumnType(int colIndex) {
        return columnTypes[colIndex]
    }

    @Override
    String getString(int rowIndex, int colIndex) {
        if (colIndex == 0) {
            return pockets.get(rowIndex).name
        }
        throw new UnsupportedOperationException("getString called on non-STRING column " + colIndex)
    }

}
