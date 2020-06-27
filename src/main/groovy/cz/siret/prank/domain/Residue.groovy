package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.PrankException
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

    private Atoms atoms

    private Atoms headAtoms
    private Atoms sideChainAtoms

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

    Atoms getAtoms() {
        if (atoms==null) {
            atoms =  Atoms.allFromGroup(group) //.withoutHydrogens()
        }
        atoms
    }

    private splitAtoms() {
        getAtoms()

        headAtoms = new Atoms(4)
        sideChainAtoms = new Atoms(atoms.count)

        for (Atom a : atoms) {
            if (a.name in ['CA', 'C', 'O', 'N']) {
                headAtoms.add(a)
            } else {
                sideChainAtoms.add(a)
            }
        }
    }

    Atoms getHeadAtoms() {
        if (headAtoms==null) splitAtoms()
        return headAtoms
    }

    Atoms getSideChainAtoms() {
        if (headAtoms==null) splitAtoms()
        return sideChainAtoms
    }

    @Nullable
    String getCode() {
        PdbUtils.getResidueCode(group)
    }

    @Nullable
    String getCorrectedCode() {
        PdbUtils.getCorrectedResidueCode(group)
    }

    @Nullable
    AA getAa() {
        AA.forName(getCorrectedCode())
    }

    Character getCodeChar() {
        getAa()?.codeChar
    }

    @Override
    String toString() {
        return key.toString()
    }

    SecStrucInfo getSectStructInfo() {
        SecStrucInfo ss = (SecStrucInfo) group.getProperty(Group.SEC_STRUC)
        ss
    }

    SecStrucType getSecStruct() {
        sectStructInfo?.type
    }



//===========================================================================================================//

    static String safe1Code(Residue res) {
        if (res == null) {
            return "_"
        } else if (res.aa == null) {
            return "?"
        } else {
            return res.aa.codeChar.toString()
        }
    }

    static String safeOrderedCode2(Residue res1, Residue res2) {
        safe1Code(res1) + safe1Code(res2)
    }

    /**
     * triplet code made of single AA code characters
     * sorted orientation
     */
    static String safeSorted3Code(Residue res1, Residue res2, Residue res3) {
        String code = safe1Code(res1) + safe1Code(res2) + safe1Code(res3)
        if (code.charAt(0) > code.charAt(2)) {
            code = code.reverse()
        }
        return code
    }

    static String safeSorted3CodeFor(Residue res) {
        safeSorted3Code(res.previousInChain, res, res.nextInChain)
    }

//===========================================================================================================//

    /**
     * Unique identifier of the residue in particular Protein
     */
    static final class Key {

        private ResidueNumber residueNumber

        Key(ResidueNumber residueNumber) {
            Objects.requireNonNull(residueNumber)
            this.residueNumber = residueNumber
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Key key = (Key) o

            if (residueNumber != key.residueNumber) return false

            return true
        }

        int hashCode() {
            return residueNumber.hashCode()
        }

        static forAtom(Atom atom) {
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
        SsSection section
        int posInSection

        SsInfo(SsSection section, int posInSection) {
            this.section = section
            this.posInSection = posInSection
        }

        int getPosInSection() {
            posInSection
        }

        /**
         * relative postion from <0,1>
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
        SecStrucType type
        int startPos // inclusive
        int length

        SsSection(SecStrucType type, int startPos, int length) {
            this.type = type
            this.startPos = startPos
            this.length = length
        }
    }


}
