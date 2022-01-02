package cz.siret.prank.program.params

import javax.annotation.Nullable

/**
 *
 */
interface IterativeParam<T> {

    String getName()

    /**
     * either all values if known or list of generated values so far
     */
    List<T> getValues()

    @Nullable
    T getNextValue()

}