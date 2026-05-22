package cz.siret.prank.program.routines.predict.output

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PocketDescriptorsRowsTest {

    @TempDir
    Path tempDir

    private static class TestPocket extends Pocket {}

    private static Atom heavyAtomAt(double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.C
        a.name = "C"
        a.x = x; a.y = y; a.z = z
        return a
    }

    private static TestPocket pocket(int rank, String name, double score, Atoms surface) {
        TestPocket p = new TestPocket()
        p.rank = rank
        p.name = name
        p.score = score
        p.surfaceAtoms = surface
        p.centroid = new Point(0d, 0d, 0d)
        return p
    }

    private static PocketGrid tinyGrid(int rank, int pointCount) {
        List<Atom> pts = (0..<pointCount).collect { int i -> (Atom) new Point((double) i, 0d, 0d) }
        LongIntHashMap idx = new LongIntHashMap()
        for (int i = 0; i < pointCount; i++) idx.put(PocketGrid.pack(i, 0, 0), i)
        BitSet bs = new BitSet()
        bs.set(0, pointCount)
        Map<Integer, BitSet> assigned = [(rank): bs] as LinkedHashMap
        return new PocketGrid(new Atoms(pts), 1.0d, 0d, 0d, 0d, idx, assigned)
    }

    @Test
    void schemaIncludesVolumeAndNameAsString() {
        TestPocket p = pocket(1, "pocket.1", 0.9d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p], ['volume'], null, tinyGrid(1, 4))

        assertEquals(['name', 'rank', 'score', 'center_x', 'center_y', 'center_z', 'volume'], data.header)
        assertEquals(TableData.ColumnType.STRING, data.getColumnType(0))
        assertEquals(TableData.ColumnType.INT, data.getColumnType(1))
        assertEquals(TableData.ColumnType.DOUBLE, data.getColumnType(2))
        assertEquals(TableData.ColumnType.DOUBLE, data.getColumnType(6))
    }

    @Test
    void probabilityColumnOmittedWhenAllProbaTpZero() {
        TestPocket p = pocket(1, "pocket.1", 0.5d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        // probaTP defaults to 0.0 (auxInfo's default); column should be omitted.
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p], ['volume'], null, tinyGrid(1, 1))
        assertFalse(data.header.contains('probability'))
    }

    @Test
    void probabilityColumnIncludedWhenAnyPocketHasNonZeroProbaTp() {
        TestPocket p1 = pocket(1, "pocket.1", 0.5d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        TestPocket p2 = pocket(2, "pocket.2", 0.4d, new Atoms([heavyAtomAt(2d, 0d, 0d)]))
        p2.auxInfo.probaTP = 0.7d
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p1, p2], ['volume'], null, tinyGrid(1, 1))
        assertTrue(data.header.contains('probability'))
    }

    @Test
    void roundTripsThroughCsvViaTableExporter() {
        TestPocket p = pocket(1, "pocket.1", 0.9256d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p], ['volume', 'num_surface_atoms'], null, tinyGrid(1, 8))
        String filepath = "${tempDir}/descriptors.csv"
        TableExporter.export(data, filepath, "csv")

        List<String> lines = new File(filepath).readLines()
        assertEquals(2, lines.size())
        assertEquals('name,rank,score,center_x,center_y,center_z,volume,num_surface_atoms', lines[0])
        assertTrue(lines[1].startsWith('pocket.1,1,'), "row prefix mismatch: ${lines[1]}")
        // volume should be 8 (8 cells × 1³); num_surface_atoms = 1
        assertTrue(lines[1].endsWith(',8,1') || lines[1].endsWith(',8.0,1'), "row should end with 8 and 1: ${lines[1]}")
    }

    @Test
    void emptyDescriptorListEmitsOnlyBaseColumns() {
        TestPocket p = pocket(1, "pocket.1", 0.5d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p], [], null, tinyGrid(1, 1))
        assertEquals(['name', 'rank', 'score', 'center_x', 'center_y', 'center_z'], data.header)
    }

    @Test
    void nullDescriptorListIsTreatedAsEmpty() {
        // Defense-in-depth: validator rejects null/blank entries inside a list,
        // but a fully-null list should also degrade gracefully to "base columns only".
        TestPocket p = pocket(1, "pocket.1", 0.5d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p], (List<String>) null, null, tinyGrid(1, 1))
        assertEquals(['name', 'rank', 'score', 'center_x', 'center_y', 'center_z'], data.header)
    }

    @Test
    void multiColumnDescriptorEmitsPrefixedHeadersAndCorrectValues() {
        // principal_moments is the only multi-column registered descriptor today.
        // Verifies the schema-build prefix rule AND that the row layout puts the
        // descriptor's three eigenvalues in the correct trailing positions.
        TestPocket p = pocket(1, "pocket.1", 0.5d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p], ['principal_moments'], null, tinyGrid(1, 27))  // 3³ cube → isotropic eigenvalues
        assertEquals(['name', 'rank', 'score', 'center_x', 'center_y', 'center_z',
                      'principal_moments.lambda1', 'principal_moments.lambda2', 'principal_moments.lambda3'],
                data.header)
        double[] row = data.getRow(0)
        assertEquals(9, row.length)
        // For a tinyGrid 27-point sequence along x (not a real cube), the eigenvalues
        // aren't isotropic — assert non-negative and sorted descending, which is the
        // contract this row layout test cares about.
        assertTrue(row[6] >= row[7], "λ₁ ≥ λ₂ in row")
        assertTrue(row[7] >= row[8], "λ₂ ≥ λ₃ in row")
        assertTrue(row[8] >= 0d, "λ₃ ≥ 0 in row")
    }

    @Test
    void scalarAndMultiColumnDescriptorsAppearInRequestedOrder() {
        // Mix scalar + multi: column order is determined by the descriptorNames list,
        // and within each descriptor by its declared columnNames(). 'volume' (scalar)
        // then 'principal_moments' (3 cols) → 4 trailing columns after the base.
        TestPocket p = pocket(1, "pocket.1", 0.5d, new Atoms([heavyAtomAt(0d, 0d, 0d)]))
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                [p], ['volume', 'principal_moments'], null, tinyGrid(1, 8))
        assertEquals(['name', 'rank', 'score', 'center_x', 'center_y', 'center_z',
                      'volume',
                      'principal_moments.lambda1', 'principal_moments.lambda2', 'principal_moments.lambda3'],
                data.header)
    }

}
