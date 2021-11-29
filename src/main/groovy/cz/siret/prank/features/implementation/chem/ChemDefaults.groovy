package cz.siret.prank.features.implementation.chem


import groovy.transform.CompileStatic

import static java.util.Collections.unmodifiableMap
import static java.util.Collections.unmodifiableSet

/**
 * TODO: move to aa-table
 */
@CompileStatic
final class ChemDefaults {

    static class ToImmutableMixin {
        static Map toImmutable(Map m) {
            return unmodifiableMap(m)
        }
    }

    static {
        Map.metaClass.mixin(ToImmutableMixin)
    }

    public static final Map<String, Integer> HYDROPHOBIC = [
            PHE:1,
            ILE:1,
            TRP:1,
            GLY:1,
            LEU:1,
            VAL:1,
            MET:1,

            ALA:1,
            CYS:1,
            TYR:1,

            ARG:-1,
            ASN:-1,
            ASP:-1,
            GLN:-1,
            GLU:-1,
            LYS:-1,
            PRO:-1
    ]

    //http://www.thinkpeptides.com/aminoacidproperties.html //(Map<String, Double>)
    public static final Map<String, Double> HYDROPHATY_INDEX = [
            ALA:1.8,
            ARG:-4.5,
            ASN:-3.5,
            ASP:-3.5,
            CYS:2.5,
            GLU:-3.5,
            GLN:-3.5,
            GLY:-0.4,
            HIS:-3.2,
            ILE:4.5,
            LEU:3.8,
            LYS:-3.9,
            MET:1.9,
            PHE:2.8,
            PRO:-1.6,
            SER:-0.8,
            THR:-0.7,
            TRP:-0.9,
            TYR:-1.3,
            VAL:4.2
    ] as Map<String, Double>

    public static final Map<String, Integer> ALIPHATIC = [ALA:1, LEU:1, ILE:1, VAL:1, GLY:1, PRO:1].asImmutable()
    public static final Map<String, Integer> AROMATIC = [PHE:1, TRP:1, TYR:1]
    public static final Map<String, Integer> SULFUR = [CYS:1, MET:1]
    public static final Map<String, Integer> HYDROXYL = [SER:1, THR:1]
    public static final Map<String, Integer> BASIC = [ARG:3, LYS:2, HIS:1]
    public static final Map<String, Integer> ACIDIC = [ASP:1, GLU:1]
    public static final Map<String, Integer> AMIDE = [ASN:1, GLN:1]
    public static final Map<String, Integer> CHARGE = [
            ASP:-1,
            GLU:-1,

            ARG:1,
            HIS:1,
            LYS:1
    ]
    public static final Map<String, Integer> POLAR = [
            ARG:1,
            ASN:1,
            ASP:1,
            GLN:1,
            GLU:1,
            HIS:1,
            LYS:1,
            SER:1,
            THR:1,
            TYR:1,
            CYS:1
    ]
    public static final Map<String, Integer> IONIZABLE = [
            ASP:1,
            GLU:1,
            HIS:1,
            LYS:1,
            ARG:1,
            CYS:1,
            TYR:1
    ]
    public static final Map<String, Integer> HB_DONOR = [ARG:1, LYS:1, TRY:1]
    public static final Map<String, Integer> HB_ACCEPTOR = [ASP:1, GLU:1]
    public static final Map<String, Integer> HB_DONOR_ACCEPTOR = [ASN:1, GLN:1, HIS:1, SER:1, THR:1, TYR:1]
    

    @CompileStatic
    private static final double mval(String aa, Map map) {
        Number val = (Number) map.get(aa)
        return val!=null ? val.doubleValue() : 0d
    }

    @CompileStatic
    static final setAAProperties(ChemVector p, String aa) {
        p.hydrophobic = mval(aa, HYDROPHOBIC)
        if (p.hydrophobic<0) p.hydrophobic=0

        p.hydrophilic = -mval(aa, HYDROPHOBIC)
        if (p.hydrophilic<0) p.hydrophilic=0

        p.hydrophatyIndex =  mval(aa, HYDROPHATY_INDEX)

        p.aliphatic = mval(aa, ALIPHATIC)
        p.aromatic  = mval(aa, AROMATIC)
        p.sulfur    = mval(aa, SULFUR)
        p.hydroxyl  = mval(aa, HYDROXYL)
        p.basic = mval(aa, BASIC)
        p.acidic = mval(aa, ACIDIC)
        p.amide = mval(aa, AMIDE)

        double charge = mval(aa, CHARGE)
        if (charge>=0) {
            p.posCharge = charge
        } else {
            p.negCharge = -charge
        }

        p.hBondDonor = mval(aa, HB_DONOR)
        p.hBondAcceptor = mval(aa, HB_ACCEPTOR)
        p.hBondDonorAcceptor = mval(aa, HB_DONOR_ACCEPTOR)
        p.polar = mval(aa, POLAR)
        p.ionizable = mval(aa, IONIZABLE)
    }


    public static final Set<String> AACODES = unmodifiableSet([
            "ALA","ARG","ASN","ASP","CYS","GLU","GLN","GLY","HIS","ILE",
            "LEU","LYS","MET","PHE","PRO","SER","THR","TRP","TYR","VAL",
            "STP"].toSet())

    /*
     * mapping AACODE to vector with default values
     */
    public static Map<String, ChemVector> AA_DEFAULTS = new HashMap<>()
    static {
        AACODES.each { String code ->
            ChemVector p = new ChemVector()
            setAAProperties(p, code)
            AA_DEFAULTS.put(code, p)
        }
        AA_DEFAULTS = unmodifiableMap(AA_DEFAULTS)
    }

}