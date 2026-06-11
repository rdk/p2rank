package cz.siret.prank.domain


import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.AminoAcid
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.ResidueNumber
import org.biojava.nbio.structure.secstruc.SecStrucInfo
import org.biojava.nbio.structure.secstruc.SecStrucType

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static cz.siret.prank.geom.Struct.getAuthorId
import static cz.siret.prank.geom.Struct.getMmcifId

/**
 * Represents protein amino acid residue
 */
@Slf4j
@CompileStatic
class Residue {

    @Nonnull
    private Key key

    @Nonnull
    private Group group

    private Atoms allAtoms
    private Atoms atoms

    private Atoms backboneAtoms
    private Atoms sidechainAtoms

    /** marks solvent exposed residues (may not be filled!) */
    boolean exposed

    ResidueChain chain 

    /** position in chain starting at 0 */
    int posInChain

    @Nullable Residue previousInChain
    @Nullable Residue nextInChain

    @Nullable SsInfo ss

    Residue(Group group) {
        this.group = group

        if (group.residueNumber == null) {
            throw new IllegalArgumentException("group without residueNumber: " + group)
        }

        this.key = new Key(group.residueNumber)
    }

    /**
     * note: in biojava UNK Groups sometimes implement AminoAcid and sometimes HetatomImpl!
     */
    static Residue fromGroup(Group group) {
        // some modified residues are included as HETATM, so we cannot rely on checks like that:
        // also UNK Groups sometimes implement AminoAcid and sometimes HetatomImpl!
        
        //if (!(group.isAminoAcid() || group.getPDBName().startsWith("UNK")))
        //    throw new PrankException("Trying to create residue from non amino acid group: " + group)

        return new Residue(group)
    }

//===========================================================================================================//

    /**
     * note: in biojava UNK Groups sometimes implement AminoAcid and sometimes HetatomImpl!
     */
    AminoAcid getAminoAcid() {
        if (group instanceof  AminoAcid) {
            return (AminoAcid)group
        } else {
            return null
        }
    }

    Key getKey() {
        return key
    }
    
    @Nullable
    String getChainMmcifId() {
        getMmcifId(group?.chain)
    }

    @Nullable
    String getChainAuthorId() {
        getAuthorId(group?.chain)
    }

    ResidueNumber getResidueNumber() {
        group.residueNumber
    }

//===========================================================================================================//

    @Nonnull
    Group getGroup() {
        return group
    }

    /**
     * Heavy atoms only (no hydrogens)
     */
    Atoms getAtoms() {
        if (atoms == null) {
            atoms = getAllAtoms().withoutHydrogens()
        }
        atoms
    }

    /**
     * All atoms including hydrogens
     */
    Atoms getAllAtoms() {
        if (allAtoms == null) {
            allAtoms = Atoms.allFromGroup(group)
        }
        allAtoms
    }

    private splitAtoms() {
        getAtoms()

        backboneAtoms = new Atoms(5)
        sidechainAtoms = new Atoms(Math.max(atoms.count - 4, 0))

        for (Atom a : atoms) {
            if (PdbUtils.isBackboneHeavyAtom(a)) {
                backboneAtoms.add(a)
            } else {
                sidechainAtoms.add(a)
            }
        }
    }

    Atoms getBackboneAtoms() {
        if (backboneAtoms == null) splitAtoms()
        return backboneAtoms
    }

    Atoms getSidechainAtoms() {
        if (backboneAtoms == null) splitAtoms()
        return sidechainAtoms
    }

    /**
     * @return three letter residue code (e.g. "ASP")
     */
    @Nullable
    String getCode() {
        PdbUtils.getResidueCode(group)
    }

    /**
     * @return three letter residue code (e.g. "ASP"), some corrections are applied
     */
    @Nullable
    String getCorrectedCode() {
        PdbUtils.getCorrectedResidueCode(group)
    }

    @Nullable
    AA getAa() {
        AA.forName(getCorrectedCode())
    }

    /**
     * One letter code via ChemComp. Result may depend on the online access.
     * null/empty is masked as '?'
     */
    @Nonnull
    char getCodeCharBiojava() {
        return PdbUtils.getBiojavaOneLetterCode(group)
    }

    /**
     * Anything that is not one of 20 standard one letter codes is masked as '?'
     * Does not go via ChemComp as getBioJavaOneLetterCode but instead via residue three-letter code.
     * Should be more stable when online access sin not allowed.
     *
     * The only three letter code masking done is MSE->MET=M
     */
    @Nonnull
    char getCodeCharStandard() {
        return PdbUtils.getStandardOneLetterCode(group)
    }

    @Override
    String toString() {
        return key.toString()
    }

    @Nullable
    SecStrucInfo getSectStructInfo() {
        SecStrucInfo ss = (SecStrucInfo) group.getProperty(Group.SEC_STRUC)
        ss
    }

    @Nullable
    SecStrucType getSecStruct() {
        sectStructInfo?.type
    }

//===========================================================================================================//

    @Override
    boolean equals(Object o) {
        if (this.is(o)) return true
        if (!(o instanceof Residue)) return false   // Residue has no subclasses: == exact-class match

        Residue residue = (Residue) o

        // untyped equals(o) + Groovy '!='/'getClass()!=' compiled to ScriptBytecodeAdapter
        // compareNotEqual (dynamic) at every HashMap lookup; Objects.equals is null-safe + static
        if (!Objects.equals(key, residue.key)) return false

        return true
    }

    int hashCode() {
        return key.hashCode()
    }

//===========================================================================================================//

    @Nonnull
    static char safe1Code(Residue res) {
        if (res == null) {
            return '_' as char
        } else {
            return res.codeCharStandard
        }
    }

    /**
     * order significant
     */
    static String safeOrderedCode2(Residue res1, Residue res2) {
        StringBuilder sb = new StringBuilder(2)
        sb.append(safe1Code(res1))
        sb.append(safe1Code(res2))
        return sb.toString()
    }

    /**
     * triplet code made of single AA code characters
     * sorted orientation
     */
    static String safeSorted3Code(Residue res1, Residue res2, Residue res3) {
        StringBuilder code = new StringBuilder(3)
        code.append(safe1Code(res1))
        code.append(safe1Code(res2))
        code.append(safe1Code(res3))

        if (code.charAt(0) > code.charAt(2)) {
            code = code.reverse()
        }
        
        return code.toString()
    }

    static String safeSorted3CodeFor(Residue res) {
        safeSorted3Code(res.previousInChain, res, res.nextInChain)
    }

//===========================================================================================================//

    /**
     * Unique identifier of the residue in particular Protein
     */
    @CompileStatic
    static final class Key {

        private final ResidueNumber residueNumber

        Key(ResidueNumber residueNumber) {
            Objects.requireNonNull(residueNumber)
            this.residueNumber = residueNumber
        }

        @Override
        boolean equals(Object o) {
            if (this.is(o)) return true
            if (!(o instanceof Key)) return false   // Key is final: instanceof == exact-class match

            Key key = (Key) o

            if (!residueNumber.equals(key.residueNumber)) return false   // both non-null (ctor requireNonNull)

            return true
        }

        int hashCode() {
            return residueNumber.hashCode()
        }

        static Key of(ResidueNumber residueNumber) {
            return new Key(residueNumber)
        }

        static Key forAtom(Atom atom) {
            ResidueNumber rn = atom?.group?.residueNumber
            if (rn != null) {
                new Key(rn)
            } else {
                null
            }
        }

        @Override
        String toString() {
            return residueNumber.printFull()
        }
    }

//===========================================================================================================//

    /**
     * Secondary structure section with position
     */
    static class SsInfo {
        final SsSection section
        final int posInSection

        SsInfo(SsSection section, int posInSection) {
            this.section = section
            this.posInSection = posInSection
        }

        int getPosInSection() {
            posInSection
        }

        /**
         * relative position from <0,1>
         */
        double getRelativePosInSection() {
            int n = section.length - 1
            if (n == 0) {
                return 0
            } else {
                return (double)posInSection / n
            }
        }

        SecStrucType getType() {
            section.type
        }

    }

    /**
     * Secondary structure section (the longest extent of the same SS type)
     */
    static class SsSection {
        final SecStrucType type
        final int startPos // inclusive
        final int length

        @Nullable SsSection previous
        @Nullable SsSection next

        SsSection(SecStrucType type, int startPos, int length) {
            this.type = type
            this.startPos = startPos
            this.length = length
        }
    }

}
