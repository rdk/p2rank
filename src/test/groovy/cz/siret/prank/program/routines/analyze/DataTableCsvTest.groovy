package cz.siret.prank.program.routines.analyze

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * CSV-quoting regression tests for DataTable.toCsv.
 *
 * Covers audit finding #6 - cell values containing commas, double-quotes, or newlines
 * must be RFC-4180 quoted so they don't break the CSV row structure.
 */
@CompileStatic
class DataTableCsvTest {

    @Test
    void plainValuesAreNotQuoted() {
        DataTable dt = new DataTable("protein", "a", "b")
        dt.newRow("p1").put("a", "x").put("b", "y")
        String csv = dt.toCsv()
        assertTrue(csv.contains("p1, x, y"), "Plain values should not be quoted: $csv")
    }

    @Test
    void valuesWithCommasAreQuoted() {
        // E.g. a cofactor specifier "FAD[contact_res_ids:A_D246,A_T259]" - the inner
        // comma must not break the CSV row.
        DataTable dt = new DataTable("protein", "specifier")
        dt.newRow("p1").put("specifier", "FAD[contact_res_ids:A_D246,A_T259]")
        String csv = dt.toCsv()
        assertTrue(csv.contains('"FAD[contact_res_ids:A_D246,A_T259]"'),
                "Comma-containing value should be wrapped in double quotes: $csv")
    }

    @Test
    void valuesWithDoubleQuotesAreEscaped() {
        DataTable dt = new DataTable("protein", "note")
        dt.newRow("p1").put("note", 'has a " quote')
        String csv = dt.toCsv()
        assertTrue(csv.contains('"has a "" quote"'),
                "Inner double quote should be doubled: $csv")
    }

    @Test
    void columnHeadersWithCommasAreQuoted() {
        DataTable dt = new DataTable("protein", "weird,header")
        dt.newRow("p1").put("weird,header", "v")
        String csv = dt.toCsv()
        assertTrue(csv.contains('"weird,header"'),
                "Header containing comma should be quoted: $csv")
    }

    @Test
    void labelWithCommaIsQuoted() {
        DataTable dt = new DataTable("protein", "a")
        dt.newRow("weird,label").put("a", "v")
        String csv = dt.toCsv()
        assertTrue(csv.contains('"weird,label"'),
                "Row label containing comma should be quoted: $csv")
    }

    @Test
    void valueWithNewlineIsQuoted() {
        // RFC-4180: cells containing LF or CR must be quoted, else parsers split mid-row.
        DataTable dt = new DataTable("protein", "note")
        dt.newRow("p1").put("note", "line1\nline2")
        String csv = dt.toCsv()
        assertTrue(csv.contains('"line1\nline2"'),
                "Newline-containing value must be wrapped in double quotes: $csv")
    }

    @Test
    void valueWithCarriageReturnIsQuoted() {
        // RFC-4180: \r alone (rare but possible from Windows-origin labels) must trigger quoting,
        // otherwise the unquoted CR mid-cell mis-splits in strict parsers. Audit fix #5.
        DataTable dt = new DataTable("protein", "note")
        dt.newRow("p1").put("note", "line1\rline2")
        String csv = dt.toCsv()
        assertTrue(csv.contains('"line1\rline2"'),
                "CR-containing value must be wrapped in double quotes: $csv")
    }

    @Test
    void nullValueRendersAsEmpty() {
        DataTable dt = new DataTable("protein", "a")
        dt.newRow("p1")  // no put() -> value remains null
        String csv = dt.toCsv()
        // Row line should be `p1, ` (label, then empty cell). No quotes, no "null".
        assertTrue(csv.contains("p1, \n") || csv.endsWith("p1, "),
                "Null cell must render as empty (not 'null'): $csv")
    }

    @Test
    void preQuotedValueGetsItsQuotesEscaped() {
        // If the user's value already starts/ends with double-quotes, those inner quotes
        // must be doubled (RFC-4180 escape) - otherwise CSV parsers see a quoted field
        // terminating prematurely.
        DataTable dt = new DataTable("protein", "note")
        dt.newRow("p1").put("note", '"already quoted"')
        String csv = dt.toCsv()
        assertTrue(csv.contains('"""already quoted"""'),
                "Pre-quoted value must have inner quotes doubled: $csv")
    }
}
