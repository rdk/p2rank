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

import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * Represents protein amino acid residue
 */
@Slf4j
@CompileStatic
class Residue {

    @Nonnull
    private Key key

    @Nonnull
    private AminoAcid group

    /** marks solvent exposed residues (may not be filled!) */
    boolean exposed


    @Nullable Residue previousInChain
    @Nullable Residue nextInChain


    Residue(AminoAcid group) {
        this.group = group
        this.key = new Key(group.residueNumber)
    }

    static Residue fromGroup(Group group) {
        if (!Struct.isAminoAcidGroup(group))
            throw new PrankException("Trying to create residue from non amino acid group: " + group)

        if (! group instanceof AminoAcid)
            throw new PrankException("Trying to create residue from group that is not of type AminoAcid: " + group)

        return new Residue((AminoAcid) group)
    }

//===========================================================================================================//

    AminoAcid getAminoAcid() {
        return group
    }

    Key getKey() {
        return key
    }
    
    @Nullable
    String getChainId() {
        group.chainId
    }

    ResidueNumber getResidueNumber() {
        group.residueNumber
    }

//===========================================================================================================//

    Atoms getAtoms() {
        Atoms.allFromGroup(group)
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
            return residueNumber.toString()
        }
    }


}
