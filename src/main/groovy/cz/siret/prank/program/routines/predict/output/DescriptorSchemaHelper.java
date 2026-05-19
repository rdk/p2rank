package cz.siret.prank.program.routines.predict.output;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

import java.util.List;

/**
 * Shared schema-build helper for descriptor-based row classes
 * ({@link PocketDescriptorsRows}, {@link PocketGridRows}).
 *
 * <p>Single point where the {@code "{name}.{col}"} multi-column header
 * convention lives — both row builders go through this helper, so a
 * future change (different separator, scalar-handling edge case) touches
 * one site.
 *
 * <p>Interface-agnostic by design: takes the descriptor's columns +
 * types directly so the helper works for both {@code PocketDescriptor}
 * and {@code PocketGridPointDescriptor} without coupling to either.
 */
public final class DescriptorSchemaHelper {

    private DescriptorSchemaHelper() {}

    /**
     * Append one descriptor's output columns to the accumulating schema lists.
     * Multi-column descriptors get {@code "{name}.{col}"} headers; scalar
     * descriptors (size 1) get the bare {@code name} (sub-name ignored).
     *
     * @param headers  schema header list (appended in place)
     * @param types    schema column-type list, parallel to {@code headers}
     * @param name     descriptor name — CLI token and multi-col prefix
     * @param colNames sub-column names declared by the descriptor
     * @param colTypes column types, parallel to {@code colNames}
     * @return number of columns appended (= {@code colNames.size()})
     */
    public static int appendColumns(List<String> headers, List<ColumnType> types,
                                     String name, List<String> colNames, List<ColumnType> colTypes) {
        boolean multi = colNames.size() > 1;
        for (int i = 0; i < colNames.size(); i++) {
            headers.add(multi ? name + "." + colNames.get(i) : name);
            types.add(colTypes.get(i));
        }
        return colNames.size();
    }

}
