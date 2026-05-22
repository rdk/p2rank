package cz.siret.prank.features.implementation.volsite;

import cz.siret.prank.domain.Protein;
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore.AtomProps;
import org.biojava.nbio.structure.Atom;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Per-protein identity-keyed cache of {@link VolSitePharmacophore#getAtomProperties(Atom)}.
 *
 * <p>Identity-keyed because the kd-tree returns the same {@link Atom} references that
 * were put in {@code Protein.proteinAtoms}; reference-equality is both correct and faster.
 * Single-writer per protein: p2rank's worker pool partitions datasets by protein, so
 * {@code secondaryData} lookups serialize within one worker.
 */
public final class VolSiteAtomTable {

    private static final String SECONDARY_DATA_KEY = "VolSiteAtomTable";

    private final IdentityHashMap<Atom, AtomProps> byAtom;

    private VolSiteAtomTable(IdentityHashMap<Atom, AtomProps> byAtom) {
        this.byAtom = byAtom;
    }

    public static VolSiteAtomTable forProtein(Protein protein) {
        return (VolSiteAtomTable) protein.getSecondaryData()
                .computeIfAbsent(SECONDARY_DATA_KEY, k -> build(protein));
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
