package cz.siret.prank.features.implementation.energy2.calc

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

/**
 * Heavy-atom role classification for H-bond partner identification.
 * Uses residue+atomname heuristics to determine donor/acceptor status.
 */
@CompileStatic
class AtomRole {

    final boolean isDonor
    final boolean isAcceptor
    final int roleClassID

    AtomRole(boolean isDonor, boolean isAcceptor, int roleClassID) {
        this.isDonor = isDonor
        this.isAcceptor = isAcceptor
        this.roleClassID = roleClassID
    }

    /**
     * Factory method to classify an atom based on residue and atom name
     */
    static AtomRole classify(Atom atom) {
        if (!atom || !atom.getGroup()) {
            return new AtomRole(false, false, 0)
        }

        Group group = atom.getGroup()
        String resName = group.getPDBName()?.trim()?.toUpperCase()
        String atomName = atom.getName()?.trim()?.toUpperCase()

        if (!resName || !atomName) {
            return new AtomRole(false, false, 0)
        }

        boolean isDonor = false
        boolean isAcceptor = false
        int roleClass = 0

        // Backbone atoms (common to all residues)
        if (atomName == "N") {
            isDonor = true  // Backbone amide nitrogen
            roleClass = 1
        } else if (atomName == "O") {
            isAcceptor = true  // Backbone carbonyl oxygen
            roleClass = 2
        }
        // Side chain classification by residue
        else {
            switch (resName) {
                case "ARG":
                    if (atomName in ["NE", "NH1", "NH2"]) {
                        isDonor = true
                        roleClass = 3  // Guanidinium
                    }
                    break

                case "ASN":
                    if (atomName == "ND2") {
                        isDonor = true  // Weak donor
                        roleClass = 4
                    } else if (atomName == "OD1") {
                        isAcceptor = true  // Amide carbonyl
                        roleClass = 5
                    }
                    break

                case "ASP":
                    if (atomName in ["OD1", "OD2"]) {
                        isAcceptor = true  // Carboxylate
                        roleClass = 6
                    }
                    break

                case "CYS":
                    if (atomName == "SG") {
                        isDonor = true  // Weak donor
                        isAcceptor = true  // Weak acceptor
                        roleClass = 7
                    }
                    break

                case "GLN":
                    if (atomName == "NE2") {
                        isDonor = true  // Weak donor
                        roleClass = 8
                    } else if (atomName == "OE1") {
                        isAcceptor = true  // Amide carbonyl
                        roleClass = 9
                    }
                    break

                case "GLU":
                    if (atomName in ["OE1", "OE2"]) {
                        isAcceptor = true  // Carboxylate
                        roleClass = 10
                    }
                    break

                case "HIS":
                    if (atomName in ["ND1", "NE2"]) {
                        isDonor = true  // Can be protonated
                        isAcceptor = true  // Can accept H-bond
                        roleClass = 11
                    }
                    break

                case "LYS":
                    if (atomName == "NZ") {
                        isDonor = true  // Ammonium
                        roleClass = 12
                    }
                    break

                case "SER":
                    if (atomName == "OG") {
                        isDonor = true  // Hydroxyl donor
                        isAcceptor = true  // Hydroxyl acceptor
                        roleClass = 13
                    }
                    break

                case "THR":
                    if (atomName == "OG1") {
                        isDonor = true  // Hydroxyl donor
                        isAcceptor = true  // Hydroxyl acceptor
                        roleClass = 14
                    }
                    break

                case "TRP":
                    if (atomName == "NE1") {
                        isDonor = true  // Indole NH
                        roleClass = 15
                    }
                    break

                case "TYR":
                    if (atomName == "OH") {
                        isDonor = true  // Phenolic donor
                        isAcceptor = true  // Phenolic acceptor
                        roleClass = 16
                    }
                    break

                default:
                    // No special role for other residues/atoms
                    roleClass = 0
                    break
            }
        }

        return new AtomRole(isDonor, isAcceptor, roleClass)
    }

    @Override
    String toString() {
        return "AtomRole{donor=${isDonor}, acceptor=${isAcceptor}, class=${roleClassID}}"
    }
}