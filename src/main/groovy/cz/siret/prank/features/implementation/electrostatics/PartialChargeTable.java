package cz.siret.prank.features.implementation.electrostatics;

import cz.siret.prank.domain.Protein;
import cz.siret.prank.utils.PdbUtils;
import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.Element;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Per-protein identity-keyed cache of atomic partial charges in elementary
 * charge units ({@code e}).
 *
 * <p>Built once per protein from {@link AmberCharges#getUnited(String, String)}
 * with element-bucket charges as the backstop (see {@link #elementFallback}).
 * Memoized on {@code Protein.secondaryData} — identical lifecycle to
 * {@link cz.siret.prank.features.implementation.volsite.VolSiteAtomTable}.
 *
 * <p>Single source of truth: every electrostatics feature/descriptor reads
 * from this table. Single-writer per protein (p2rank's worker pool partitions
 * datasets by protein, so {@code secondaryData} lookups serialize within one
 * worker).
 *
 * <p><b>Charge convention:</b> as of the united-atom fix, the cached values
 * are the <i>united-atom</i> AMBER ff14SB charges (heavy atom + bonded H
 * charges merged onto the heavy atom). This is the right shape for PDB
 * structures that don't carry explicit hydrogens. Earlier revisions called
 * {@link AmberCharges#get} (all-atom), which sign-flipped cationic side
 * chains for hydrogen-less inputs; numerical values for the same input
 * differ from those revisions.
 */
public final class PartialChargeTable {

    private static final String SECONDARY_DATA_KEY = "PartialChargeTable";

    private final IdentityHashMap<Atom, Double> byAtom;
    /** Count of {@link #get} calls that fell through to the element bucket
     *  because the atom wasn't in the build-time map. Exposed for diagnostics:
     *  drift from {@code Pocket.surfaceAtoms} (expected) bumps this slowly,
     *  but a sudden jump correlates with a cache-invalidation bug. */
    private long fallbackCount;

    private PartialChargeTable(IdentityHashMap<Atom, Double> byAtom) {
        this.byAtom = byAtom;
    }

    public static PartialChargeTable forProtein(Protein protein) {
        return (PartialChargeTable) protein.getSecondaryData()
                .computeIfAbsent(SECONDARY_DATA_KEY, k -> build(protein));
    }

    /** Test seam: build a table directly from a hand-supplied charge map. */
    static PartialChargeTable forTesting(IdentityHashMap<Atom, Double> charges) {
        return new PartialChargeTable(new IdentityHashMap<>(charges));
    }

    private static PartialChargeTable build(Protein protein) {
        List<Atom> atoms = protein.getProteinAtoms().list;
        IdentityHashMap<Atom, Double> m = new IdentityHashMap<>(atoms.size());
        for (Atom a : atoms) {
            String res = PdbUtils.getCorrectedAtomResidueCode(a);
            // Use the united-atom table (H charges merged into bonded heavy atoms),
            // because PDB-loaded protein structures typically don't carry explicit
            // hydrogens — the all-atom value alone would inverted-sign cationic
            // residues like LYS (NZ all-atom −0.39 e, NZ united +0.63 e).
            double q = AmberCharges.getUnited(res, a.getName());
            if (Double.isNaN(q)) q = elementFallback(a.getElement());
            m.put(a, q);
        }
        return new PartialChargeTable(m);
    }

    /**
     * @return cached partial charge in {@code e}, or the element-bucket fallback
     *         for atoms not in the build-time map (e.g. reference drift from
     *         predictor clustering into {@code Pocket.surfaceAtoms}). Never NaN.
     */
    public double get(Atom atom) {
        Double q = byAtom.get(atom);
        if (q != null) return q;
        fallbackCount++;
        return elementFallback(atom == null ? null : atom.getElement());
    }

    /**
     * @return count of {@link #get} calls that missed the build-time map and
     *         fell through to element fallback. Zero for a healthy cache;
     *         non-zero for atoms drawn from {@code Pocket.surfaceAtoms}
     *         that aren't literal {@link IdentityHashMap}-keys of
     *         {@code Protein.proteinAtoms}. A sudden spike per protein is a
     *         signal that the cache may be stale.
     */
    public long getFallbackCount() {
        return fallbackCount;
    }

    /**
     * Element-bucket fallback for atoms not covered by the AMBER table.
     *
     * <p>Values are hand-tuned defaults reflecting typical element behaviour
     * in biological molecules: light non-metals shaded along their Pauling
     * electronegativity (C ≈ 2.55 → −0.10 e; N ≈ 3.04 → −0.40; O ≈ 3.44 →
     * −0.50; S ≈ 2.58 → −0.20); H gets a small positive value as the canonical
     * H-bond donor; common biological cations (Fe, Zn, Cu, Mn, Mg, Ca) are
     * assigned their common +2 oxidation state and Na/K their +1; halides
     * (Cl, Br) get −1.
     *
     * <p>These are NOT force-field charges — they will not match AMBER values
     * for any specific atom. They are a deterministic, never-NaN backstop so
     * that descriptors don't crash on HETATM atoms (cofactors, ligands, ions,
     * water, modified residues). For ML feature derivation the signal is the
     * sign and magnitude class, not the third decimal.
     */
    static double elementFallback(Element element) {
        if (element == null) return 0d;
        switch (element) {
            case C: return -0.10d;
            case N: return -0.40d;
            case O: return -0.50d;
            case S: return -0.20d;
            case H: return  0.10d;
            case P: return  0.50d;
            case Fe: case Zn: case Cu: case Mn:
            case Mg: case Ca:
                return  2.0d;
            case Na: case K:
                return  1.0d;
            case Cl: case Br:
                return -1.0d;
            default:
                return  0d;
        }
    }
}
