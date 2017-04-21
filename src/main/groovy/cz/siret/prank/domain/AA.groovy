package cz.siret.prank.domain

import groovy.transform.CompileStatic

/**
 * 20 main amino acid codes
 */
@CompileStatic
enum AA {

    ALA,
    CYS,
    ASP,
    GLU,
    PHE,
    GLY,
    HIS,
    ILE,
    LYS,
    LEU,
    MET,
    ASN,
    PRO,
    GLN,
    ARG,
    SER,
    THR,
    VAL,
    TRP,
    TYR;

    private static final Map<String, AA> index = new HashMap<String, AA>()


    static {
        for (AA value : EnumSet.allOf(AA.class)) {
            index.put(value.name(), value)
        }
    }

    public static AA forName(String name) {
        return index.get(name)
    }

}
