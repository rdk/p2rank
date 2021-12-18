package cz.siret.prank.domain

import groovy.transform.CompileStatic

import javax.annotation.Nullable

/**
 * 20 main amino acid codes
 */
@CompileStatic
enum AA {

    ALA('A' as char),
    CYS('C' as char),
    ASP('D' as char),
    GLU('E' as char),
    PHE('F' as char),
    GLY('G' as char),
    HIS('H' as char),
    ILE('I' as char),
    LYS('K' as char),
    LEU('L' as char),
    MET('M' as char),
    ASN('N' as char),
    PRO('P' as char),
    GLN('Q' as char),
    ARG('R' as char),
    SER('S' as char),
    THR('T' as char),
    VAL('V' as char),
    TRP('W' as char),
    TYR('Y' as char);

    private static final Map<String, AA> index = new HashMap<String, AA>()
    private static final Map<Character, AA> indexByCodeChar = new HashMap<Character, AA>()

    static final String ALL_CODE_CHARS = values().collect { AA it -> it.codeChar }.join("")

    private char codeChar

    AA(char codeChar) {
        this.codeChar = codeChar
    }

    /**
     * 3 letter uppercase code identical with the enum element name
     * @return
     */
    String getCode() {
        return name()
    }

    char getCodeChar() {
        return codeChar
    }

    static {
        for (AA value : AA.values()) {
            index.put(value.name(), value)
            indexByCodeChar.put(value.codeChar, value)
        }
    }

    @Nullable
    static AA forName(String name) {
        return index.get(name)
    }

    @Nullable
    static AA forCode(String code) {
        return forName(code)
    }

    static AA forCodeChar(char codeChar) {
        return indexByCodeChar.get(codeChar)
    }

    static boolean isStandardOneLetterCode(char codeChar) {
        return forCodeChar(codeChar) != null
    }

}
