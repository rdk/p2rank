package cz.siret.prank.program.rendering

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.LabeledPoint
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor

import java.awt.Color

/**
 *
 */
@TupleConstructor
@CompileStatic
class RenderingModel {

    /**
     * generate new structure (pdb) file from protein instead of reusing proteinFile
     */
    boolean generateProteinFile = false
    String proteinFile

    String label

    Protein protein
    BinaryLabeling observedLabeling
    BinaryLabeling predictedLabeling
    List<LabeledPoint> labeledPoints

    Style style = new Style() 


    static class Style {
        //Color defaultProteinColor

        Color positiveResiduesColor = new Color(202,133,156)
        Color negativeResiduesColor = new Color(149,167,224)

        Color tpColor = new Color(100, 104, 142)  // blue
        Color fpColor = new Color(246, 147, 150)    // magenta
        Color fnColor = new Color(109, 186, 192)  // cyan

        //Color tpColor = Color.BLUE     
        //Color fpColor = Color.MAGENTA
        //Color fnColor = Color.CYAN

        //Color tnColor = new Color()
    }

}
