package cz.siret.prank.features.implementation.volsite;

import cz.siret.prank.domain.Protein;
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore.AtomProps;
import org.biojava.nbio.structure.Atom;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Per-protein cache of {@link VolSitePharmacophore#getAtomProperties(Atom)} keyed by atom identity.
 *
 * <p>Built once per protein on first access and memoized on {@code Protein.secondaryData}.
 * Replaces ~50k × ~30 per-(point, atom) calls in the volsite grid-point descriptors —
 * each of which allocates {@link AtomProps}, runs {@code toUpperCase}, and walks ~30
 * {@code String.contains} branches — with one table-build (~13k entries for a large
 * protein, ~1 MB) plus one identity-hashmap lookup per query.
 *
 * <p>Identity-keyed because the kd-tree returns the same {@link Atom} references that
 * were put in {@code Protein.proteinAtoms}; reference-equality is both faster and the
 * semantically correct relation here.
 *
 * <p>Assumes single-threaded access per protein — p2rank's worker pool partitions
 * datasets by protein, so {@code secondaryData} lookups serialize within one worker.
 *
 * <p>The canonical pharmacophore rules live in {@link VolSitePharmacophore}; this class
 * is a pure cache and stays in lock-step automatically when those rules change.
 */
public final class VolSiteAtomTable {

    private static final String SECONDARY_DATA_KEY = "VolSiteAtomTable";

    private final IdentityHashMap<Atom, AtomProps> byAtom;

    private VolSiteAtomTable(IdentityHashMap<Atom, AtomProps> byAtom) {
        this.byAtom = byAtom;
    }

    /**
     * Get-or-build the table for {@code protein}. Cached on the protein's
     * {@code secondaryData} — repeated calls return the same instance.
     */
    public static VolSiteAtomTable forProtein(Protein protein) {
        VolSiteAtomTable cached = (VolSiteAtomTable) protein.getSecondaryData().get(SECONDARY_DATA_KEY);
        if (cached != null) return cached;
        VolSiteAtomTable built = build(protein);
        protein.getSecondaryData().put(SECONDARY_DATA_KEY, built);
        return built;
    }

    private static VolSiteAtomTable build(Protein protein) {
        List<Atom> atoms = protein.getProteinAtoms().list;
        IdentityHashMap<Atom, AtomProps> m = new IdentityHashMap<>(atoms.size());
        for (Atom a : atoms) {
            m.put(a, VolSitePharmacophore.getAtomProperties(a));
        }
        return new VolSiteAtomTable(m);
    }

    /**
     * @return cached {@link AtomProps} for {@code atom}; never null for atoms in the protein
     *         the table was built from.
     * @throws IllegalStateException if {@code atom} is not in the table — typically means
     *         {@code Protein.proteinAtoms} was mutated after the table was built
     */
    public AtomProps get(Atom atom) {
        AtomProps p = byAtom.get(atom);
        if (p == null) {
            throw new IllegalStateException(
                    "Atom not in VolSiteAtomTable — Protein.proteinAtoms changed since the table was built");
        }
        return p;
    }
}
