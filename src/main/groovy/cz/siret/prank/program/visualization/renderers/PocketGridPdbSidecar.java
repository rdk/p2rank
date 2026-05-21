package cz.siret.prank.program.visualization.renderers;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;
import cz.siret.prank.utils.Futils;
import org.biojava.nbio.structure.Atom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared writer for the {@code {label}_pocket_grid.pdb.gz} sidecar consumed by
 * both {@link PocketGridPymolRenderer} and {@link PocketGridChimeraXRenderer}.
 *
 * <p>{@link #write} produces a single combined file with one HETATM per
 * {@code (point, pocket)} pair (pocket rank in the residue-sequence column) —
 * what the PyMOL overlay loads as {@code pocket_grid_src} and then partitions
 * by {@code resi N}.
 *
 * <p>{@link #writePerPocket} additionally writes one file per pocket — the
 * ChimeraX overlay loads each as a submodel ({@code #99.N}) so the Models
 * panel shows a per-pocket entry in the tree. ChimeraX has no per-residue
 * split command that works with our HETATM-only PDB, so we partition on disk
 * instead.
 *
 * <p>Element column is {@code C} (not {@code H}) so PyMOL's surface algorithm
 * picks the atoms up without needing {@code flag ignore} cleared. Coordinate
 * fields use {@link Locale#ROOT} so locales with comma decimals can't break
 * downstream parsers.
 *
 * <p>Pocket ranks are capped at 9999 by the PDB residue-sequence column width
 * (4 chars). Real protein pockets stay well under 100.
 */
public final class PocketGridPdbSidecar {

    private static final Logger log = LoggerFactory.getLogger(PocketGridPdbSidecar.class);

    /** PDB serial column width (5 chars) — exceeded serials wrap via modulo. */
    private static final int SERIAL_LIMIT = 100_000;

    private static final String PDB_LINE_FORMAT =
            "HETATM%5d  C   STP A%4d    %8.3f%8.3f%8.3f  1.00  0.00          C\n";

    private PocketGridPdbSidecar() {}

    /**
     * Idempotent write: only writes if the file doesn't already exist.
     * Used by the renderers so that the second renderer-to-run is a no-op
     * (the combined PDB is shared between PyMOL and ChimeraX overlays).
     *
     * @return {@code true} if the file was written, {@code false} if it already existed.
     */
    public static boolean ensureWritten(PocketGrid grid, String pdbPath) {
        if (Futils.exists(pdbPath)) return false;
        write(grid, pdbPath);
        return true;
    }

    /** Write all pockets' grid points to one combined gzipped PDB. */
    public static void write(PocketGrid grid, String pdbPath) {
        try (PrintWriter pdb = Futils.getGzipWriter(pdbPath)) {
            int serial = 1;
            List<Integer> ranks = sortedRanks(grid);
            for (Integer rank : ranks) {
                BitSet indices = grid.getPocketToPointIndices().get(rank);
                if (indices == null || indices.isEmpty()) continue;
                serial = writePocket(pdb, grid, rank, indices, serial);
            }
            if (serial > SERIAL_LIMIT) {
                log.warn("Pocket grid sidecar [{}] has {} atoms — exceeds the PDB " +
                        "serial column width ({}); serials wrap and become non-unique. " +
                        "PyMOL/ChimeraX rendering still works (we don't rely on serials), " +
                        "but bond-inference tools may misbehave.",
                        pdbPath, serial - 1, SERIAL_LIMIT);
            }
        }
    }

    /**
     * Write one gzipped PDB per pocket; returns the basenames in rank order so
     * the caller can reference them by file path.
     *
     * <p>Filename convention: {@code {labelPrefix}_pocket_grid_{rank}.pdb.gz}.
     *
     * @param grid         pocket grid
     * @param dirPath      directory the files are written into (e.g. {@code …/visualizations/data})
     * @param labelPrefix  per-protein prefix (typically {@code item.label})
     * @return rank → relative basename (e.g. {@code 1 → "1fbl.pdb_pocket_grid_1.pdb.gz"})
     */
    public static java.util.LinkedHashMap<Integer, String> writePerPocket(PocketGrid grid, String dirPath, String labelPrefix) {
        java.util.LinkedHashMap<Integer, String> rankToBasename = new java.util.LinkedHashMap<>();
        for (Integer rank : sortedRanks(grid)) {
            BitSet indices = grid.getPocketToPointIndices().get(rank);
            if (indices == null || indices.isEmpty()) continue;
            String basename = labelPrefix + "_pocket_grid_" + rank + ".pdb.gz";
            String fullPath = dirPath + "/" + basename;
            int endSerial;
            try (PrintWriter pdb = Futils.getGzipWriter(fullPath)) {
                endSerial = writePocket(pdb, grid, rank, indices, 1);
            }
            // Same wrap-warning policy as write() — fires only when a single pocket has
            // enough grid points to overflow the PDB serial column. Practically unreachable
            // for real pockets but kept for parity with the combined writer.
            if (endSerial > SERIAL_LIMIT) {
                log.warn("Per-pocket grid sidecar [{}] has {} atoms — exceeds the PDB " +
                        "serial column width ({}); serials wrap and become non-unique.",
                        fullPath, endSerial - 1, SERIAL_LIMIT);
            }
            rankToBasename.put(rank, basename);
        }
        return rankToBasename;
    }

    private static List<Integer> sortedRanks(PocketGrid grid) {
        List<Integer> ranks = new ArrayList<>(grid.getPocketToPointIndices().keySet());
        Collections.sort(ranks);
        return ranks;
    }

    /** Emit HETATM lines for one pocket. Returns the next serial number to use. */
    private static int writePocket(PrintWriter pdb, PocketGrid grid, int rank, BitSet indices, int startSerial) {
        int serial = startSerial;
        int resi = Math.min(rank, 9999);
        for (int i = indices.nextSetBit(0); i >= 0; i = indices.nextSetBit(i + 1)) {
            Atom p = grid.getAllPoints().list.get(i);
            pdb.printf(Locale.ROOT, PDB_LINE_FORMAT,
                    serial % SERIAL_LIMIT, resi, p.getX(), p.getY(), p.getZ());
            serial++;
        }
        return serial;
    }

}
