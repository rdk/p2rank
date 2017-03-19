package cz.siret.prank.features.implementation.chem

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import groovy.transform.CompileStatic

/**
 * TODO: move to aa-table
 */
final class ChemDefaults {

    static class ToImmutableMixin {
        static def toImmutable(Map m) {
            ImmutableMap.copyOf(m)
        }
    }

    static {
        Map.metaClass.mixin(ToImmutableMixin)
    }

    public static final Map<String, Integer> HYDROPHOBIC = [
            Phe:1,
            Ile:1,
            Trp:1,
            Gly:1,
            Leu:1,
            Val:1,
            Met:1,

            Ala:1,
            Cys:1,
            Tyr:1,

            Arg:-1,
            Asn:-1,
            Asp:-1,
            Gln:-1,
            Glu:-1,
            Lys:-1,
            Pro:-1
    ]

    //http://www.thinkpeptides.com/aminoacidproperties.html //(Map<String, Double>)
    public static final Map<String, Double> HYDROPHATY_INDEX = [
            Ala:1.8,
            Arg:-4.5,
            Asn:-3.5,
            Asp:-3.5,
            Cys:2.5,
            Glu:-3.5,
            Gln:-3.5,
            Gly:-0.4,
            His:-3.2,
            Ile:4.5,
            Leu:3.8,
            Lys:-3.9,
            Met:1.9,
            Phe:2.8,
            Pro:-1.6,
            Ser:-0.8,
            Thr:-0.7,
            Trp:-0.9,
            Tyr:-1.3,
            Val:4.2
    ]
    //http://www.sigmaaldrich.com/life-science/metabolomics/learning-center/amino-acid-reference-chart.html#prop
//        static Map<String, Double> HYDROPHATY_INDEX = (Map<String, Double>)  [
//            Ala:41,
//            Arg:-14,
//            Asn:-28,
//            Asp:-55,
//            Cys:49,
//            Glu:-31,
//            Gln:-10,
//            Gly:0,
//            His:8,
//            Ile:99,
//            Leu:97,
//            Lys:-23,
//            Met:74,
//            Phe:100,
//            Pro:-46,
//            Ser:-5,
//            Thr:13,
//            Trp:97,
//            Tyr:63,
//            Val:76
//        ]

    public static final Map<String, Integer> ALIPHATIC = [Ala:1, Leu:1, Ile:1, Val:1, Gly:1, Pro:1].asImmutable()
    public static final Map<String, Integer> AROMATIC = [Phe:1, Trp:1, Tyr:1]
    public static final Map<String, Integer> SULFUR = [Cys:1, Met:1]
    public static final Map<String, Integer> HYDROXYL = [Ser:1, Thr:1]
    public static final Map<String, Integer> BASIC = [Arg:3, Lys:2, His:1]
    public static final Map<String, Integer> ACIDIC = [Asp:1, Glu:1]
    public static final Map<String, Integer> AMIDE = [Asn:1, Gln:1]
    public static final Map<String, Integer> CHARGE = [
            Asp:-1,
            Glu:-1,

            Arg:1,
            His:1,
            Lys:1
    ]
    public static final Map<String, Integer> POLAR = [
            Arg:1,
            Asn:1,
            Asp:1,
            Gln:1,
            Glu:1,
            His:1,
            Lys:1,
            Ser:1,
            Thr:1,
            Tyr:1,
            Cys:1
    ]
    public static final Map<String, Integer> IONIZABLE = [
            Asp:1,
            Glu:1,
            His:1,
            Lys:1,
            Arg:1,
            Cys:1,
            Tyr:1
    ]
    public static final Map<String, Integer> HB_DONOR = [Arg:1, Lys:1, Try:1]
    public static final Map<String, Integer> HB_ACCEPTOR = [Asp:1, Glu:1]
    public static final Map<String, Integer> HB_DONOR_ACCEPTOR = [Asn:1, Gln:1, His:1, Ser:1, Thr:1, Tyr:1]
    // http://www.imgt.org/IMGTeducation/Aide-memoire/_UK/aminoacids/charge/

    @CompileStatic
    private static final double mval(String aa, Map map) {
        Number val = (Number) map.get(aa)
        return val!=null ? val.doubleValue() : 0
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


    public static final Set<String> AACODES = ImmutableSet.copyOf(["Ala","Arg","Asn","Asp","Cys","Glu","Gln","Gly","His","Ile",
                                                                   "Leu","Lys","Met","Phe","Pro","Ser","Thr","Trp","Tyr","Val",   "Stp"])

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
        AA_DEFAULTS = ImmutableMap.copyOf(AA_DEFAULTS)
    }

}