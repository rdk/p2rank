package cz.siret.prank.features.implementation.secstruct;

import org.apache.commons.lang3.ObjectUtils;
import org.biojava.nbio.structure.secstruc.SecStrucType;

import javax.annotation.Nullable;

/**
 *
 */
public enum SimpleSecStructType {

    HELIX("Helix", 'H'),
    BSHEET("BSheet", 'B'),
    COIL("Coil", ' ');

    private final String name;
    private final char code;

    SimpleSecStructType(String name, char code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public char getCode() {
        return code;
    }

    public static SimpleSecStructType from(SecStrucType type) {
        if (type == null) {
            return null;
        } else if (type.isHelixType()) {
            return HELIX;
        } else if (type.isBetaStrand()) {
            return BSHEET;
        } else {
            return COIL;
        }
    }

    public static int safeCompare(@Nullable SimpleSecStructType a, @Nullable SimpleSecStructType b) {
        return ObjectUtils.compare(a, b);
    }

}
