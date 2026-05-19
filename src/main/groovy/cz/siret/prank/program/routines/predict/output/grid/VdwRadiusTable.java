package cz.siret.prank.program.routines.predict.output.grid;

import org.biojava.nbio.structure.Atom;
import org.openscience.cdk.config.Elements;
import org.openscience.cdk.tools.periodictable.PeriodicTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-atom van der Waals radius via CDK's {@code PeriodicTable.getVdwRadius(symbol)}.
 *
 * <p>Some biologically common metals (Co, Ni, Cu, Rh, Os, Ir) and radioactive
 * elements have {@code null} VdW radii in CDK's {@code Elements} enum
 * (see {@code local/cdk-vdw-radius-gap.md}). For those, we fall back to
 * Krypton's 2.02 Å — within 0.05 Å of the actual radii of all the affected
 * biological metals per CDK's own {@code radii-vdw.txt}. This matches the
 * pattern in {@code cz.siret.prank.geom.PatchedCdkNumericalSurface}.
 *
 * <p>Results are cached in a {@link ConcurrentHashMap} keyed on element
 * symbol, since {@code get} is called once per lattice cell during grid
 * generation and dataset processing runs multi-threaded.
 */
public final class VdwRadiusTable {

    private static final Logger log = LoggerFactory.getLogger(VdwRadiusTable.class);

    /** Fallback radius for elements whose CDK enum entry returns null. */
    public static final double FALLBACK_VDW = 2.02d;  // Krypton, matches PatchedCdkNumericalSurface

    private static final ConcurrentHashMap<String, Double> CACHE = new ConcurrentHashMap<>();

    private VdwRadiusTable() {}

    /**
     * @return van der Waals radius in Å for the atom's element; never null,
     *         never NaN. Unknown symbols and null-radius elements both
     *         resolve to {@link #FALLBACK_VDW}.
     */
    public static double get(Atom atom) {
        String symbol = resolveSymbol(atom);
        return CACHE.computeIfAbsent(symbol, s -> {
            Double r = PeriodicTable.getVdwRadius(s);
            if (r == null) {
                log.debug("VdwRadiusTable: null radius for [{}], using Krypton fallback {}", s, FALLBACK_VDW);
                return FALLBACK_VDW;
            }
            return r;
        });
    }

    private static String resolveSymbol(Atom atom) {
        if (atom.getElement() != null) {
            String s = mapIsotope(atom.getElement().name());
            if (!Elements.ofString(s).equals(Elements.Unknown)) {
                return s;
            }
        }
        // Fall back to atom name prefix (matches CdkUtils.bioJavaToCDKAtom pattern).
        // mapIsotope is applied here only as a safety net — a PDB atom *name*
        // starting with "D" or "T" is more likely a heavier element (e.g. "DA" =
        // DNA ribose carbon) than an isotope. Mis-mapping is benign for the grid
        // sampler (vdw(H) vs vdw(C) keep-out band differs by < 0.5 Å), so we
        // prioritise the isotope case rather than build a name-prefix exclusion list.
        String name = atom.getName();
        if (name != null && !name.isEmpty()) {
            String s = mapIsotope(name.substring(0, 1));
            if (!Elements.ofString(s).equals(Elements.Unknown)) {
                return s;
            }
        }
        return "C";  // last-resort, same fallback as CdkUtils
    }

    /** Map hydrogen isotopes (D, T) to H — CDK's Elements enum doesn't distinguish them. */
    private static String mapIsotope(String symbol) {
        if ("D".equals(symbol) || "T".equals(symbol)) return "H";
        return symbol;
    }

}
