package cz.siret.prank.features.implementation.volsite;

/**
 * Adopted from VolSite druggability prediction method.
 */
public class VolSitePharmacophore {

    public static class AtomProps {
        public boolean aromatic;
        public boolean cation;
        public boolean anion;
        public boolean hydrophobic;
        public boolean acceptor;
        public boolean donor;
    }
    
    private static boolean appartient(String a, String list) {
        return list.contains(a);
    }

    /**
     * Adopted from VolSite
     *
     * @param atomCode CD1, ND2, ...
     * @param residueCode PHE, Trp, TYR...
     */
    public static AtomProps getAtomProperties(String atomCode, String residueCode) {
        if (residueCode==null) residueCode="";
        if (atomCode==null) atomCode="";

        String name = atomCode.toUpperCase();
        String resname = residueCode.toUpperCase();
        
        AtomProps atm = new AtomProps();

        if (name.equals("C")) {
            atm.hydrophobic = true; return atm;
        }
        if (appartient(name, "CA, CB") && !resname.equals("CA")) {
            atm.hydrophobic = true; return atm;
        }
        if (name.equals("CD") && appartient(resname, "ARG, GLN, GLU, LYS, PRO")) {
            atm.hydrophobic = true; return atm;
        }
        if (name.equals("CD1"))
            if (appartient(resname, "ILE, LEU")) {
                atm.hydrophobic = true; return atm;
            } else if (appartient(resname, "PHE, TRP, TYR")) {
                atm.aromatic = true; return atm;
            }
        if (name.equals("CD2"))
            if (resname.equals("LEU")) {
                atm.hydrophobic = true; return atm;
            } else if (appartient(resname, "PHE, TRP, TYR")) {
                atm.aromatic = true; return atm;
            } else if (appartient(resname, "HIS,HID,HIE")) {
                atm.aromatic = true;
                atm.hydrophobic=false; return atm;
            }
        if (name.equals("CE") && appartient(resname, "LYS, MET")) {
            atm.hydrophobic = true; return atm;
        }
        if (name.equals("CE1"))
            if (appartient(resname, "HIS,HID,HIE")) {
                atm.aromatic = true;
                atm.hydrophobic=false; return atm;
            } else if (appartient(resname, "PHE, TYR")) {
                atm.aromatic = true; return atm;
            }
        if (name.equals("CE2") && appartient(resname, "PHE, TRP, TYR")) {
            atm.aromatic = true; return atm;
        }
        if (name.equals("CE3") && resname.equals("TRP")) {
            atm.aromatic = true; return atm;
        }
        if (name.equals("CG"))
            if (appartient(resname, "ARG, ASN, ASP, CYS, CYX, GLN, GLU, LEU, LYS, MET, PRO")) {
                atm.hydrophobic = true; return atm;
            } else if (appartient(resname, "HIS, HID, HIE, PHE, TRP, TYR")) {
                atm.aromatic = true; return atm;
            }
        if ((name.equals("CG1") && appartient(resname, "ILE, VAL"))
                || (name.equals("CG2") && appartient(resname, "ILE, THR, VAL"))) {
            atm.hydrophobic = true; return atm;
        }
        if (name.equals("CH2") && resname.equals("TRP")) {
            atm.aromatic = true; return atm;
        }
        if (name.equals("CZ"))
            if (resname.equals("ARG")) {
                atm.hydrophobic = true; return atm;
            } else if (appartient(resname, "PHE, TYR")) {
                atm.aromatic = true; return atm;
            }
        if (appartient(name, "CZ2, CZ3") && resname.equals("TRP")) {
            atm.aromatic = true; return atm;
        }
        if (name.equals("N")) {
            atm.donor = true; return atm;
        }
        if (name.equals("ND1") && appartient(resname, "HIS, HID, HIE")) {
            atm.donor = true; atm.acceptor = true; return atm;
        }
        if (name.equals("ND2") && resname.equals("ASN")) {
            atm.donor = true; return atm;
        }
        if (name.equals("NE") && appartient(resname, "ARG, LYS")) {
            atm.cation = true; return atm;
        }
        if (name.equals("NE1") && resname.equals("TRP")) {
            atm.donor = true; return atm;
        }
        if (name.equals("NE2"))
            if (resname.equals("GLN")) {
                atm.donor = true; return atm;
            } else if (appartient(resname, "HIS, HID, HIE")) {
                atm.donor = true;
                atm.acceptor = true; return atm;
            }
        if ((appartient(name, "NH1, NH2") && resname.equals("ARG"))
                || (name.equals("NZ") && resname.equals("LYS"))) {
            atm.cation = true; return atm;
        }
        if (name.equals("O")) {
            atm.acceptor = true; return atm;
        }
        if (appartient(name, "OD1, OD2") && resname.equals("ASP")) {
            atm.anion = true; return atm;
        }
        if (name.equals("OD1") && resname.equals("ASN")) {
            atm.acceptor = true; return atm;
        }
        if (name.equals("OE1") && resname.equals("GLN")) {
            atm.acceptor = true; return atm;
        }
        if (appartient(name, "OE1, OE2") && resname.equals("GLU")) {
            atm.anion = true; return atm;
        }
        if ((name.equals("OG") && resname.equals("SER")) ||
                (name.equals("OG1") && resname.equals("THR")) ||
                (name.equals("OH") && resname.equals("TYR"))) {
            atm.donor = true;
            atm.acceptor = true; return atm;
        }
        if (name.equals("OXT")) {
            atm.anion = true; return atm;
        }
        if (appartient(name, "SD, SG"))
            if (appartient(resname, "CYS, CYX")) {
                atm.acceptor = true; return atm;
            } else if (resname.equals("MET")) {
                atm.hydrophobic = true; return atm;
            }
        if (appartient(name, "FE, MG, MG, MN, ZN, CO")) {
            atm.cation = true; return atm;
        }

        return atm;
    }

}
