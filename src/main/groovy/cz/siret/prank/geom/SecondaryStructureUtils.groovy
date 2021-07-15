package cz.siret.prank.geom

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Structure
import org.biojava.nbio.structure.secstruc.SecStrucCalc

/**
 * @see https://github.com/biojava/biojava-tutorial/blob/master/structure/secstruc.md
 */
@CompileStatic
class SecondaryStructureUtils {

    /**
     * Determine and assign the SS of the Structure.
     * The rules for SS calculation are the ones defined by DSSP.
     * Resulting object also stores the result of the calculation.
     *
     * @see http://biojava.org/docs/api/org/biojava/nbio/structure/secstruc/SecStrucCalc.html
     */
    static SecStrucCalc assignSecondaryStructure(Structure structure) {
        SecStrucCalc ssp = new SecStrucCalc()
        ssp.calculate(structure, true) //true assigns the SS to the Structure
        return  ssp
    }
    
}
