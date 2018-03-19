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

        Color tpColor = new Color(138,131,206)  // violet
        Color fpColor = new Color(255,147,145)  // pink-red
        Color fnColor = new Color(160,166,225)  // violet-blue
        //Color tnColor = new Color()
    }

}
