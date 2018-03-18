package cz.siret.prank.program.rendering

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.BinaryResidueLabeling
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
    BinaryResidueLabeling binaryLabeling
    List<LabeledPoint> labeledPoints

    Style style = new Style() 


    static class Style {
        //Color defaultProteinColor

        Color positiveResiduesColor = Color.RED
        Color negativeResiduesColor = Color.CYAN
    }

}
