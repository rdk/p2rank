package cz.siret.prank.features.implementation.chem

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element

/**
 *
 */
@Slf4j
@CompileStatic
class ChemVector implements Cloneable {

    double  hydrophobic; // 1=hydrophobic (A/Ala, C/Cys, I/Ile, L/Leu, M/Met, F/Phe, W/Trp, V/Val),   +Pro +Gly
    double  hydrophilic; // 1=hyrophilic (R/Arg, N/Asn, D/Asp, Q/Gln, E/Glu, K/Lys)
    double  hydrophatyIndex;
    double  aliphatic; //1 (A/Ala,  I/Ile, L/Leu,, V/Val)    G/Gly,    P/Pro
    double  aromatic; //1 (F/Phe, W/Trp, Y/Tyr)
    double  sulfur; //1 (C/Cys, M/Met)
    double  hydroxyl; //1 (S/Ser, T/Thr)
    double  basic; //1 (R/Arg, H/His, K/Lys)
    double  acidic; //1 (D/Asp, E/Glu)
    double  amide; //1 (N/Asn, Q/Gln)
    double  posCharge; // -1=negative charged (D/Asp, E/Glu), 0=uncharged,  1=positive charged (R/Arg, H/Hys, K/Lys)
    double  negCharge;
    double  hBondDonor; //1 (R/Arg, K/Lys, V/Val)
    double  hBondAcceptor; //1 (D/Asp, E/Glu)
    double  hBondDonorAcceptor; // 1 (N/Asn, Q/Gln, H/His, S/Ser, T/Thr, Y/Tyr)
    double  polar; //1=polar (R/Arg, N/Asn, D/Asp, Q/Gln, E/Glu, H/His, K/Lys, S/Ser, T/Thr, Y/Tyr)   +Cys
    double  ionizable
    double  atoms
    double  atomDensity
    double  atomC
    double  atomO
    double  atomN
    double  hDonorAtoms
    double  hAcceptorAtoms

    static final List<String> HEADER = [
            "hydrophobic",
            "hydrophilic",
            "hydrophatyIndex",
            "aliphatic",
            "aromatic",
            "sulfur",
            "hydroxyl",
            "basic",
            "acidic",
            "amide",
            "posCharge",
            "negCharge",
            "hBondDonor",
            "hBondAcceptor",
            "hBondDonorAcceptor",
            "polar",
            "ionizable",
            "atoms",
            "atomDensity",
            "atomC",
            "atomO",
            "atomN",
            "hDonorAtoms",
            "hAcceptorAtoms"
    ]


    static List<String> getHeader() {
        return HEADER
    }

    /**
     *
     * @param residueCode 3 letter residue code (e.g. "Ala")
     */
    private void setFromResidueAtom(Atom atom, String residueCode) {

        atomDensity = 1

        String an = atom.name // atom name

        if (residueCode == "Ala") {
        } else if (residueCode == "Arg") {
            if(["NE","NH1","NH2"].contains(an))
                hDonorAtoms++
        } else if (residueCode == "Asn") {
            if(an=="ND2") hDonorAtoms++
            if(an=="OD1") hAcceptorAtoms++
        } else if (residueCode == "Asp") {
            if(["OD1","OD2"].contains(an)) hAcceptorAtoms++
        } else if (residueCode == "Cys") {
        } else if (residueCode == "Gln") {
            if(an=="NE2") hDonorAtoms++
            if(an=="OE1") hAcceptorAtoms++
        } else if (residueCode == "Glu") {
            if(["OE1","OE2"].contains(an)) hAcceptorAtoms++
        } else if (residueCode == "Gly") {
        } else if (residueCode == "His") {
            if(["ND1","NE2"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Ile") {
        } else if (residueCode == "Leu") {
        } else if (residueCode == "Lys") {
            if(an=="NZ") hDonorAtoms++
        } else if (residueCode == "Met") {
        } else if (residueCode == "Phe") {
        } else if (residueCode == "Pro") {
        } else if (residueCode == "Ser") {
            if(["OG"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Thr") {
            if(["OG1"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Trp") {
            if(an=="NE1") hDonorAtoms++
        } else if (residueCode == "Tyr") {
            if(["OH"].contains(an)) { hDonorAtoms++; hAcceptorAtoms++ }
        } else if (residueCode == "Val") {
        } else {
            if (!ChemDefaults.AACODES.contains(residueCode) ) {
                log.debug "!! masking unknown residue code:  $residueCode"
                //if (params.mask_unknown_residues) {
                //    log.debug "!!! masking unknown residue code:  $residueCode"
                //} else {
                //    throw new PrankException("unknown residue code: $residueCode")
                //}
            }
        }


    }

    public static ChemVector forAtom(Atom atom, String residueCode) {

        ChemVector p

        ChemVector proto = ChemDefaults.AA_DEFAULTS.get(residueCode)
        if (proto!=null) {
            p = (ChemVector) proto.clone()
        } else {
            p =  new ChemVector()
        }

        p.setFromResidueAtom(atom, residueCode)

        if (atom.element == Element.C) { p.atomC=1 }
        else if (atom.element == Element.O) { p.atomO=1 }
        else if (atom.element == Element.N) { p.atomN=1 }
        else {
            if (atom.name.startsWith("C")) { p.atomC=1 }
            else if (atom.name.startsWith("O")) { p.atomO=1 }
            else if (atom.name.startsWith("N")) { p.atomN=1 }
            else {
                //log.debug "ignore ATOM: "+atom.element  + " | " +atom.getPDBline()
            }
        }

        return p
    }
    
    double[] toArray() {
        [
                hydrophobic       ,
                hydrophilic       ,
                hydrophatyIndex   ,
                aliphatic         ,
                aromatic          ,
                sulfur            ,
                hydroxyl          ,
                basic             ,
                acidic            ,
                amide             ,
                posCharge         ,
                negCharge         ,
                hBondDonor        ,
                hBondAcceptor     ,
                hBondDonorAcceptor,
                polar             ,
                ionizable         ,
                atoms             ,
                atomDensity       ,
                atomC             ,
                atomO             ,
                atomN             ,
                hDonorAtoms       ,
                hAcceptorAtoms    
        ] as double[]
    }
    
}
