package cz.siret.prank.utils

import groovy.transform.CompileStatic

import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 *
 */
@CompileStatic
class SysUtils {

    static boolean isInstanceOfAny(@Nullable Object obj, @Nonnull List<Class> classes) {
        return classes.contains(obj?.class)
    }

}
