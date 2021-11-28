package cz.siret.prank.features.implementation.secstruct

import cz.siret.prank.domain.Residue
import groovy.transform.CompileStatic

import javax.annotation.Nonnull

import static cz.siret.prank.utils.Sutils.prefixEach

/**
 *
 */
@CompileStatic
class SsSimpleTriplet {

    private static final List<String> HIST_HEADER = SsSimpleHistogram.header
    private static final List<String> HEADER = prefixEach("1_", HIST_HEADER) + prefixEach("2_", HIST_HEADER) + prefixEach("3_", HIST_HEADER)

    static List<String> getHeader() {
        HEADER
    }

    /**
     * values can be null
     */
    @Nonnull
    private final List<SimpleSecStructType> types

    /**
     * @param types must have size=3
     */
    SsSimpleTriplet(List<SimpleSecStructType> types) {
        this.types = types
    }

    static SsSimpleTriplet from(Residue.SsSection prev, Residue.SsSection middle, Residue.SsSection next) {
        return new SsSimpleTriplet([SimpleSecStructType.from(prev?.type), SimpleSecStructType.from(middle?.type), SimpleSecStructType.from(next?.type)])
    }

    /**
     * rotate triplet so iut starts with lowest ordinal
     */
    void order() {
        if (SimpleSecStructType.safeCompare(types[0], types[2]) > 0) {
            Collections.swap(types, 0, 2)
        }
    }

    void encodeOneHotInplace(double[] array, int startIdx) {
        SsSimpleHistogram.encodeOneHotInplace(types[0], array, startIdx)
        SsSimpleHistogram.encodeOneHotInplace(types[1], array, startIdx + 3)
        SsSimpleHistogram.encodeOneHotInplace(types[2], array, startIdx + 6)
    }

}
