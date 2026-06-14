package cz.siret.prank.features.implementation.electrostatics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * AMBER ff14SB partial-charge lookup table, in elementary charge units (<i>e</i>).
 *
 * <p>Covers the 20 standard amino acids plus common protonation variants
 * (HID/HIE/HIP, CYS/CYX, ASH/GLH, LYN). Atoms outside this table (HETATM
 * cofactors, ions, modified residues) fall through to
 * {@link PartialChargeTable#elementFallback} at build time.
 *
 * <h2>Source</h2>
 *
 * <p>Values are AMBER ff14SB partial charges — Maier et al.,
 * "ff14SB: Improving the Accuracy of Protein Side Chain and Backbone
 * Parameters from ff99SB", <i>J. Chem. Theory Comput.</i> 2015,
 * 11 (8), 3696–3713 (doi:10.1021/acs.jctc.5b00255). The charges originate
 * from RESP fitting to ab-initio QM on dipeptide model compounds.
 *
 * <p>The canonical primary sources are the AmberTools distribution files
 * {@code amino12.lib} and {@code frcmod.ff14SB}. Equivalent tabulated forms
 * appear in OpenMM ({@code ff14SB.xml}) and GROMACS userspace.
 *
 * <h2>Simplifications vs. canonical ff14SB</h2>
 *
 * <p>The embedded table uses a <b>shared canonical backbone</b> (N, H, CA,
 * HA, C, O charges identical across all residues) rather than ff14SB's
 * residue-specific backbones. This trades ~0.1 <i>e</i> of per-residue
 * drift against table-size compactness. Net residue charge is correct in
 * sign but drifts by up to ~0.2 <i>e</i> in magnitude — see
 * {@code AmberChargesTest} for the validated tolerance.
 *
 * <p>HIS aliases HIE (the ε-protonated neutral form, default at
 * physiological pH); HID and HIP are also tabulated separately.
 *
 * <p>Encoded as compile-time constants in a static initializer — for ~500
 * entries this is small enough to maintain in source and is checked at
 * compile time.
 */
public final class AmberCharges {

    /** Per-residue all-atom dictionary (atom name → charge in e), as published in ff14SB. Keys uppercase. */
    private static final Map<String, Map<String, Double>> TABLE = new HashMap<>();

    /** Per-residue united-atom dictionary — heavy atoms only, with each H's charge
     *  rolled into the heavy atom it bonds to. Built by {@link #buildUnitedAtomTable}
     *  in the static initializer after the all-atom TABLE is populated. */
    private static final Map<String, Map<String, Double>> UNITED_TABLE = new HashMap<>();

    /** Element-letter prefixes of heavy atoms in standard amino-acid residues.
     *  Must be declared BEFORE the static block so it's non-null when
     *  {@link #buildUnitedAtomTable} reaches {@link #findHeavyBondedTo}. */
    private static final String[] HEAVY_ELEMENT_PREFIXES = {"C", "N", "O", "S"};

    private AmberCharges() {}

    /**
     * All-atom lookup — returns the published ff14SB charge for the atom as-is.
     *
     * <p>For most P2Rank workloads you want {@link #getUnited} instead: standard
     * PDB structures don't include hydrogens, so asking for the heavy atom's
     * all-atom charge alone undercounts (and inverts the sign for cationic
     * residues like LYS — its all-atom NZ is −0.39 e, but the residue's actual
     * cationic character lives on the H atoms with +0.34 e each).
     *
     * @return partial charge in <i>e</i> for the (residue, atom) pair, or
     *         {@link Double#NaN} if the pair isn't in the table.
     *         Case-insensitive on both arguments.
     */
    public static double get(String residueCode, String atomName) {
        return lookup(TABLE, residueCode, atomName);
    }

    /**
     * United-atom lookup — for the heavy atom, returns its ff14SB charge plus
     * the sum of its bonded hydrogens' charges. The right shape for protein
     * structures that don't carry explicit hydrogens (i.e. almost every PDB).
     *
     * <p>Example: LYS NZ all-atom is −0.3854; LYS NZ united is
     * −0.3854 + 3 × 0.3400 = +0.6346 — correctly reflecting LYS's cationic
     * side-chain ammonium.
     *
     * <p>For hydrogen atoms the lookup returns NaN — their charge has been
     * absorbed into the heavy atom they bond to.
     *
     * @return united-atom partial charge in <i>e</i>, or
     *         {@link Double#NaN} if the pair isn't in the heavy-atom table.
     *         Case-insensitive on both arguments.
     */
    public static double getUnited(String residueCode, String atomName) {
        return lookup(UNITED_TABLE, residueCode, atomName);
    }

    private static double lookup(Map<String, Map<String, Double>> table,
                                 String residueCode, String atomName) {
        if (residueCode == null || atomName == null) return Double.NaN;
        Map<String, Double> residue = table.get(residueCode.toUpperCase());
        if (residue == null) return Double.NaN;
        Double q = residue.get(atomName.toUpperCase());
        return q == null ? Double.NaN : q;
    }

    /** Canonical backbone charges shared by all standard residues except PRO
     *  (no amide H) and the GLY/PRO special cases. Real ff14SB has residue-
     *  specific backbones; the shared form trades ~0.1 e of per-residue
     *  drift for table-size compactness — see {@code AmberChargesTest} for
     *  the relaxed net-charge tolerance this implies. */
    private static final double BB_N  = -0.4157d;
    /** Standard backbone amide H (proton on N). */
    private static final double BB_H  =  0.2719d;
    /** Standard backbone carbonyl C. */
    private static final double BB_C  =  0.5973d;
    /** Standard backbone carbonyl O. */
    private static final double BB_O  = -0.5679d;

    private static void put(String residue, String atom, double charge) {
        TABLE.computeIfAbsent(residue, k -> new HashMap<>()).put(atom, charge);
    }

    /** Add canonical backbone atoms (N, H, CA, HA, C, O) for a residue. */
    private static void backbone(String residue, double caCharge, double haCharge) {
        put(residue, "N",  BB_N);
        put(residue, "H",  BB_H);
        put(residue, "CA", caCharge);
        put(residue, "HA", haCharge);
        put(residue, "C",  BB_C);
        put(residue, "O",  BB_O);
    }

    static {
        // === ALA — neutral ===
        backbone("ALA",  0.0337d,  0.0823d);
        put("ALA", "CB", -0.1825d);
        put("ALA", "HB1", 0.0603d); put("ALA", "HB2", 0.0603d); put("ALA", "HB3", 0.0603d);

        // === GLY — neutral, no HA (two HA2/HA3 instead) ===
        put("GLY", "N",  BB_N); put("GLY", "H",  BB_H);
        put("GLY", "CA", -0.0252d); put("GLY", "HA2", 0.0698d); put("GLY", "HA3", 0.0698d);
        put("GLY", "C",  BB_C); put("GLY", "O",  BB_O);

        // === SER — neutral ===
        backbone("SER",  -0.0249d,  0.0843d);
        put("SER", "CB", 0.2117d); put("SER", "HB2", 0.0352d); put("SER", "HB3", 0.0352d);
        put("SER", "OG", -0.6546d); put("SER", "HG",  0.4275d);

        // === THR — neutral ===
        backbone("THR", -0.0389d,  0.1007d);
        put("THR", "CB",  0.3654d); put("THR", "HB",  0.0043d);
        put("THR", "OG1", -0.6761d); put("THR", "HG1", 0.4102d);
        put("THR", "CG2", -0.2438d);
        put("THR", "HG21", 0.0642d); put("THR", "HG22", 0.0642d); put("THR", "HG23", 0.0642d);

        // === CYS — neutral, free thiol ===
        backbone("CYS",  0.0213d,  0.1124d);
        put("CYS", "CB", -0.1231d); put("CYS", "HB2", 0.1112d); put("CYS", "HB3", 0.1112d);
        put("CYS", "SG", -0.3119d); put("CYS", "HG",  0.1933d);

        // === CYX — neutral, disulfide-bonded (no thiol H) ===
        backbone("CYX", 0.0429d, 0.0766d);
        put("CYX", "CB", -0.0790d); put("CYX", "HB2", 0.0910d); put("CYX", "HB3", 0.0910d);
        put("CYX", "SG", -0.1081d);

        // === VAL — neutral ===
        backbone("VAL", -0.0875d, 0.0969d);
        put("VAL", "CB", 0.2985d); put("VAL", "HB", -0.0297d);
        put("VAL", "CG1", -0.3192d);
        put("VAL", "HG11", 0.0791d); put("VAL", "HG12", 0.0791d); put("VAL", "HG13", 0.0791d);
        put("VAL", "CG2", -0.3192d);
        put("VAL", "HG21", 0.0791d); put("VAL", "HG22", 0.0791d); put("VAL", "HG23", 0.0791d);

        // === LEU — neutral ===
        backbone("LEU", -0.0518d, 0.0922d);
        put("LEU", "CB", -0.1102d); put("LEU", "HB2", 0.0457d); put("LEU", "HB3", 0.0457d);
        put("LEU", "CG", 0.3531d); put("LEU", "HG", -0.0361d);
        put("LEU", "CD1", -0.4121d);
        put("LEU", "HD11", 0.1000d); put("LEU", "HD12", 0.1000d); put("LEU", "HD13", 0.1000d);
        put("LEU", "CD2", -0.4121d);
        put("LEU", "HD21", 0.1000d); put("LEU", "HD22", 0.1000d); put("LEU", "HD23", 0.1000d);

        // === ILE — neutral ===
        backbone("ILE", -0.0597d, 0.0869d);
        put("ILE", "CB", 0.1303d); put("ILE", "HB", 0.0187d);
        put("ILE", "CG1", -0.0430d); put("ILE", "HG12", 0.0236d); put("ILE", "HG13", 0.0236d);
        put("ILE", "CG2", -0.3204d);
        put("ILE", "HG21", 0.0882d); put("ILE", "HG22", 0.0882d); put("ILE", "HG23", 0.0882d);
        put("ILE", "CD1", -0.0660d);
        put("ILE", "HD11", 0.0186d); put("ILE", "HD12", 0.0186d); put("ILE", "HD13", 0.0186d);

        // === MET — neutral ===
        backbone("MET", -0.0237d, 0.0880d);
        put("MET", "CB", 0.0342d); put("MET", "HB2", 0.0241d); put("MET", "HB3", 0.0241d);
        put("MET", "CG", 0.0018d); put("MET", "HG2", 0.0440d); put("MET", "HG3", 0.0440d);
        put("MET", "SD", -0.2737d);
        put("MET", "CE", -0.0536d);
        put("MET", "HE1", 0.0684d); put("MET", "HE2", 0.0684d); put("MET", "HE3", 0.0684d);

        // === PHE — neutral, aromatic ===
        backbone("PHE", -0.0024d, 0.0978d);
        put("PHE", "CB", -0.0343d); put("PHE", "HB2", 0.0295d); put("PHE", "HB3", 0.0295d);
        put("PHE", "CG", 0.0118d);
        put("PHE", "CD1", -0.1256d); put("PHE", "HD1", 0.1330d);
        put("PHE", "CD2", -0.1256d); put("PHE", "HD2", 0.1330d);
        put("PHE", "CE1", -0.1704d); put("PHE", "HE1", 0.1430d);
        put("PHE", "CE2", -0.1704d); put("PHE", "HE2", 0.1430d);
        put("PHE", "CZ", -0.1072d); put("PHE", "HZ", 0.1297d);

        // === TYR — neutral, aromatic ===
        backbone("TYR", -0.0014d, 0.0876d);
        put("TYR", "CB", -0.0152d); put("TYR", "HB2", 0.0295d); put("TYR", "HB3", 0.0295d);
        put("TYR", "CG", -0.0011d);
        put("TYR", "CD1", -0.1906d); put("TYR", "HD1", 0.1699d);
        put("TYR", "CD2", -0.1906d); put("TYR", "HD2", 0.1699d);
        put("TYR", "CE1", -0.2341d); put("TYR", "HE1", 0.1656d);
        put("TYR", "CE2", -0.2341d); put("TYR", "HE2", 0.1656d);
        put("TYR", "CZ", 0.3226d);
        put("TYR", "OH", -0.5579d); put("TYR", "HH", 0.3992d);

        // === TRP — neutral, aromatic ===
        backbone("TRP", -0.0275d, 0.1123d);
        put("TRP", "CB", -0.0050d); put("TRP", "HB2", 0.0339d); put("TRP", "HB3", 0.0339d);
        put("TRP", "CG", -0.1415d);
        put("TRP", "CD1", -0.1638d); put("TRP", "HD1", 0.2062d);
        put("TRP", "CD2", 0.1243d);
        put("TRP", "NE1", -0.3418d); put("TRP", "HE1", 0.3412d);
        put("TRP", "CE2", 0.1380d);
        put("TRP", "CE3", -0.2387d); put("TRP", "HE3", 0.1700d);
        put("TRP", "CZ2", -0.2601d); put("TRP", "HZ2", 0.1572d);
        put("TRP", "CZ3", -0.1972d); put("TRP", "HZ3", 0.1447d);
        put("TRP", "CH2", -0.1134d); put("TRP", "HH2", 0.1417d);

        // === PRO — neutral, no amide H ===
        put("PRO", "N", -0.2548d);
        put("PRO", "CA", -0.0266d); put("PRO", "HA", 0.0641d);
        put("PRO", "CB", -0.0070d); put("PRO", "HB2", 0.0253d); put("PRO", "HB3", 0.0253d);
        put("PRO", "CG", 0.0189d); put("PRO", "HG2", 0.0213d); put("PRO", "HG3", 0.0213d);
        put("PRO", "CD", 0.0192d); put("PRO", "HD2", 0.0391d); put("PRO", "HD3", 0.0391d);
        put("PRO", "C",  BB_C); put("PRO", "O",  BB_O);

        // === ASN — neutral ===
        backbone("ASN", 0.0143d, 0.1048d);
        put("ASN", "CB", -0.2041d); put("ASN", "HB2", 0.0797d); put("ASN", "HB3", 0.0797d);
        put("ASN", "CG", 0.7130d);
        put("ASN", "OD1", -0.5931d);
        put("ASN", "ND2", -0.9191d); put("ASN", "HD21", 0.4196d); put("ASN", "HD22", 0.4196d);

        // === GLN — neutral ===
        backbone("GLN", -0.0031d, 0.0850d);
        put("GLN", "CB", -0.0036d); put("GLN", "HB2", 0.0171d); put("GLN", "HB3", 0.0171d);
        put("GLN", "CG", -0.0645d); put("GLN", "HG2", 0.0352d); put("GLN", "HG3", 0.0352d);
        put("GLN", "CD", 0.6951d);
        put("GLN", "OE1", -0.6086d);
        put("GLN", "NE2", -0.9407d); put("GLN", "HE21", 0.4251d); put("GLN", "HE22", 0.4251d);

        // === ASP — anionic (net −1) ===
        backbone("ASP", 0.0381d, 0.0880d);
        put("ASP", "CB", -0.0303d); put("ASP", "HB2", -0.0122d); put("ASP", "HB3", -0.0122d);
        put("ASP", "CG", 0.7994d);
        put("ASP", "OD1", -0.8014d); put("ASP", "OD2", -0.8014d);

        // === ASH — protonated ASP, neutral ===
        backbone("ASH", 0.0341d, 0.0864d);
        put("ASH", "CB", -0.0316d); put("ASH", "HB2", 0.0488d); put("ASH", "HB3", 0.0488d);
        put("ASH", "CG", 0.6462d);
        put("ASH", "OD1", -0.5554d); put("ASH", "OD2", -0.6376d); put("ASH", "HD2", 0.4747d);

        // === GLU — anionic (net −1) ===
        backbone("GLU", 0.0397d, 0.1105d);
        put("GLU", "CB", 0.0560d); put("GLU", "HB2", -0.0173d); put("GLU", "HB3", -0.0173d);
        put("GLU", "CG", 0.0136d); put("GLU", "HG2", -0.0425d); put("GLU", "HG3", -0.0425d);
        put("GLU", "CD", 0.8054d);
        put("GLU", "OE1", -0.8188d); put("GLU", "OE2", -0.8188d);

        // === GLH — protonated GLU, neutral ===
        backbone("GLH", 0.0145d, 0.0779d);
        put("GLH", "CB", -0.0071d); put("GLH", "HB2", 0.0256d); put("GLH", "HB3", 0.0256d);
        put("GLH", "CG", -0.0174d); put("GLH", "HG2", 0.0430d); put("GLH", "HG3", 0.0430d);
        put("GLH", "CD", 0.6801d);
        put("GLH", "OE1", -0.5838d); put("GLH", "OE2", -0.6511d); put("GLH", "HE2", 0.4641d);

        // === LYS — cationic (net +1) ===
        backbone("LYS", -0.2400d, 0.1426d);
        put("LYS", "CB", -0.0094d); put("LYS", "HB2", 0.0362d); put("LYS", "HB3", 0.0362d);
        put("LYS", "CG", 0.0187d); put("LYS", "HG2", 0.0103d); put("LYS", "HG3", 0.0103d);
        put("LYS", "CD", -0.0479d); put("LYS", "HD2", 0.0621d); put("LYS", "HD3", 0.0621d);
        put("LYS", "CE", -0.0143d); put("LYS", "HE2", 0.1135d); put("LYS", "HE3", 0.1135d);
        put("LYS", "NZ", -0.3854d);
        put("LYS", "HZ1", 0.3400d); put("LYS", "HZ2", 0.3400d); put("LYS", "HZ3", 0.3400d);

        // === LYN — deprotonated LYS, neutral ===
        backbone("LYN", -0.0721d, 0.0994d);
        put("LYN", "CB", -0.0485d); put("LYN", "HB2", 0.0340d); put("LYN", "HB3", 0.0340d);
        put("LYN", "CG", 0.0660d); put("LYN", "HG2", 0.0104d); put("LYN", "HG3", 0.0104d);
        put("LYN", "CD", -0.0381d); put("LYN", "HD2", 0.0115d); put("LYN", "HD3", 0.0115d);
        put("LYN", "CE", 0.3263d); put("LYN", "HE2", -0.0335d); put("LYN", "HE3", -0.0335d);
        put("LYN", "NZ", -1.0358d);
        put("LYN", "HZ2", 0.3860d); put("LYN", "HZ3", 0.3860d);

        // === ARG — cationic (net +1) ===
        backbone("ARG", -0.2637d, 0.1560d);
        put("ARG", "CB", -0.0007d); put("ARG", "HB2", 0.0327d); put("ARG", "HB3", 0.0327d);
        put("ARG", "CG", 0.0390d); put("ARG", "HG2", 0.0285d); put("ARG", "HG3", 0.0285d);
        put("ARG", "CD", 0.0486d); put("ARG", "HD2", 0.0687d); put("ARG", "HD3", 0.0687d);
        put("ARG", "NE", -0.5295d); put("ARG", "HE", 0.3456d);
        put("ARG", "CZ", 0.8076d);
        put("ARG", "NH1", -0.8627d); put("ARG", "HH11", 0.4478d); put("ARG", "HH12", 0.4478d);
        put("ARG", "NH2", -0.8627d); put("ARG", "HH21", 0.4478d); put("ARG", "HH22", 0.4478d);

        // === HID — His, neutral, δ-protonated ===
        backbone("HID", 0.0188d, 0.0881d);
        put("HID", "CB", -0.0462d); put("HID", "HB2", 0.0402d); put("HID", "HB3", 0.0402d);
        put("HID", "CG", -0.0266d);
        put("HID", "ND1", -0.3811d); put("HID", "HD1", 0.3649d);
        put("HID", "CE1", 0.2057d); put("HID", "HE1", 0.1392d);
        put("HID", "NE2", -0.5727d);
        put("HID", "CD2", 0.1292d); put("HID", "HD2", 0.1147d);

        // === HIE — His, neutral, ε-protonated (default at physiological pH) ===
        backbone("HIE", -0.0581d, 0.1360d);
        put("HIE", "CB", -0.0074d); put("HIE", "HB2", 0.0367d); put("HIE", "HB3", 0.0367d);
        put("HIE", "CG", 0.1868d);
        put("HIE", "ND1", -0.5432d);
        put("HIE", "CE1", 0.1635d); put("HIE", "HE1", 0.1435d);
        put("HIE", "NE2", -0.2795d); put("HIE", "HE2", 0.3339d);
        put("HIE", "CD2", -0.2207d); put("HIE", "HD2", 0.1862d);

        // === HIP — His, cationic, doubly protonated (net +1) ===
        backbone("HIP", -0.1354d, 0.1212d);
        put("HIP", "CB", -0.0414d); put("HIP", "HB2", 0.0810d); put("HIP", "HB3", 0.0810d);
        put("HIP", "CG", -0.0012d);
        put("HIP", "ND1", -0.1513d); put("HIP", "HD1", 0.3866d);
        put("HIP", "CE1", -0.0170d); put("HIP", "HE1", 0.2681d);
        put("HIP", "NE2", -0.1718d); put("HIP", "HE2", 0.3911d);
        put("HIP", "CD2", -0.1141d); put("HIP", "HD2", 0.2317d);

        // === HIS aliases HIE (most common protonation state at physiological pH) ===
        // Share the inner map by reference — TABLE is read-only post-init.
        TABLE.put("HIS", TABLE.get("HIE"));

        buildUnitedAtomTable();
    }

    // ----------------------------------------------------------------------
    // United-atom table derivation
    // ----------------------------------------------------------------------

    /**
     * Build {@link #UNITED_TABLE} by folding each hydrogen's charge into the
     * heavy atom it's bonded to. Run once during static init after
     * {@link #TABLE} is populated.
     *
     * <p>Why: standard PDB structures don't ship explicit hydrogens, so an
     * all-atom lookup misses the H charges entirely — and many side chains
     * carry their net charge on the H atoms (LYS NZ + 3 HZs sum to +1 e;
     * just NZ is −0.39 e). United-atom representation moves the H charges
     * onto the heavy atom they bond to, restoring the residue's net charge
     * on the atoms that actually exist in the structure.
     *
     * <p>The H→heavy mapping uses the PDB atom-name convention: an H atom
     * named "H&lt;suffix&gt;" bonds to the heavy atom whose name suffix matches
     * (after optionally stripping a trailing digit that distinguishes
     * multiple Hs on the same heavy). Concrete examples:
     * <ul>
     *   <li>H (backbone) → N</li>
     *   <li>HA → CA</li>
     *   <li>HB1/HB2/HB3 → CB (strip trailing digit)</li>
     *   <li>HZ1/HZ2/HZ3 → NZ (LYS — strip trailing digit, prefix is N)</li>
     *   <li>HG21/HG22/HG23 → CG2 (THR — strip trailing digit)</li>
     *   <li>HH11/HH12/HH21/HH22 → NH1/NH2 (ARG)</li>
     * </ul>
     *
     * <p>The string-based H detection ({@code name.startsWith("H")}) is correct
     * for the modern PDB names in our embedded ff14SB table. For runtime
     * Atom-level H detection across alternative PDB conventions
     * (e.g. legacy "2HA" style), see
     * {@link cz.siret.prank.geom.Struct#isHydrogenAtom}.
     */
    private static void buildUnitedAtomTable() {
        for (Map.Entry<String, Map<String, Double>> resEntry : TABLE.entrySet()) {
            String residue = resEntry.getKey();
            Map<String, Double> allAtom = resEntry.getValue();

            Map<String, Double> heavy = new HashMap<>();
            for (Map.Entry<String, Double> e : allAtom.entrySet()) {
                if (!e.getKey().startsWith("H")) {
                    heavy.put(e.getKey(), e.getValue());
                }
            }

            for (Map.Entry<String, Double> e : allAtom.entrySet()) {
                String hName = e.getKey();
                if (!hName.startsWith("H")) continue;
                String bonded = findHeavyBondedTo(hName, heavy.keySet());
                if (bonded == null) {
                    throw new IllegalStateException(
                            "Could not find heavy-atom bonding partner for " + residue + "/" + hName);
                }
                heavy.merge(bonded, e.getValue(), Double::sum);
            }

            UNITED_TABLE.put(residue, heavy);
        }
        // Keep HIS aliasing HIE in the united-atom table too — same reasoning as the all-atom path.
        UNITED_TABLE.put("HIS", UNITED_TABLE.get("HIE"));
    }

    /**
     * Resolve an H atom name to the heavy atom in the same residue that it bonds to.
     * Strategy: strip the leading 'H' to get a suffix, then try matching the suffix
     * against any heavy atom in the residue (with prefix C/N/O/S). If no match,
     * progressively strip trailing digits (handles HB1/HB2/HB3 → CB).
     *
     * @return the heavy atom name (key in {@code heavy}), or {@code null} if no match
     */
    private static String findHeavyBondedTo(String hName, Set<String> heavyNames) {
        // Backbone amide proton: named exactly "H", it bonds to the amide nitrogen N.
        // Special-cased because stripping the leading 'H' leaves an empty suffix, so the
        // generic prefix search below would match the carbonyl "C" (present in every
        // residue) before "N" — folding the amide H's charge onto the wrong heavy atom.
        if (hName.equals("H")) {
            return heavyNames.contains("N") ? "N" : null;
        }
        String suffix = hName.substring(1);
        while (true) {
            for (String prefix : HEAVY_ELEMENT_PREFIXES) {
                String candidate = prefix + suffix;
                if (heavyNames.contains(candidate)) return candidate;
            }
            if (suffix.isEmpty() || !Character.isDigit(suffix.charAt(suffix.length() - 1))) {
                return null;
            }
            suffix = suffix.substring(0, suffix.length() - 1);
        }
    }
}
