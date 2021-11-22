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
     * http://www.imgt.org/IMGTeducation/Aide-memoire/_UK/aminoacids/charge/
     *
     * @param residueCode 3 letter residue code (e.g. "ALA")
     */
    private void setFromResidueAtom(Atom atom, String residueCode) {

        atomDensity = 1

        String an = atom.name // atom name

        if ("ARG".equals(residueCode)) {
            if ("NE".equals(an) || "NH1".equals(an) || "NH2".equals(an)) {
                hDonorAtoms++
            }
        } else if ("ASN".equals(residueCode)) {
            if ("ND2".equals(an)) {
                hDonorAtoms++
            } else if ("OD1".equals(an)) {
                hAcceptorAtoms++
            }
        } else if ("ASP".equals(residueCode)) {
            if ("OD1".equals(an) || "OD2".equals(an)) {
                hAcceptorAtoms++
            }
        } else if ("GLN".equals(residueCode)) {
            if ("NE2".equals(an)) {
                hDonorAtoms++
            } else if ("OE1".equals(an)) {
                hAcceptorAtoms++
            }
        } else if ("GLU".equals(residueCode)) {
            if ("OE1".equals(an) || "OE2".equals(an)) {
                hAcceptorAtoms++
            }
        } else if ("HIS".equals(residueCode)) {
            if ("ND1".equals(an) || "NE2".equals(an)) {
                hDonorAtoms++
                hAcceptorAtoms++
            }
        } else if ("LYS".equals(residueCode)) {
            if ("NZ".equals(an)) {
                hDonorAtoms++
            }
        } else if ("SER".equals(residueCode)) {
            if ("OG".equals(an)) {
                hDonorAtoms++
                hAcceptorAtoms++
            }
        } else if ("THR".equals(residueCode)) {
            if ("OG1".equals(an)) {
                hDonorAtoms++
                hAcceptorAtoms++
            }
        } else if ("TRP".equals(residueCode)) {
            if ("NE1".equals(an)) {
                hDonorAtoms++
            }
        } else if ("TYR".equals(residueCode)) {
            if ("OH".equals(an)) {
                hDonorAtoms++
                hAcceptorAtoms++
            }
        } else {
            if (!ChemDefaults.AACODES.contains(residueCode)) {
                log.debug "!! masking unknown residue code:  $residueCode"
                //if (params.mask_unknown_residues) {
                //    log.debug "!!! masking unknown residue code:  $residueCode"
                //} else {
                //    throw new PrankException("unknown residue code: $residueCode")
                //}
            }
        }

    }

    /**
     *
     * @param atom
     * @param residueCode 3-letter AA code (all upper case)
     * @return
     */
    public static ChemVector forAtom(Atom atom, String residueCode) {

        ChemVector p

        ChemVector proto = ChemDefaults.AA_DEFAULTS.get(residueCode)
        if (proto!=null) {
            p = (ChemVector) proto.clone()
        } else {
            p =  new ChemVector()
        }

        p.setFromResidueAtom(atom, residueCode)

        if (atom.element === Element.C) { p.atomC=1 }
        else if (atom.element === Element.O) { p.atomO=1 }
        else if (atom.element === Element.N) { p.atomN=1 }
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
