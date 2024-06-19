package cz.siret.prank.geom.transform


import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Calc
import us.ihmc.euclid.matrix.RotationMatrix

/**
 *
 */
@CompileStatic
class Rotation extends GeometricTransformation {

    private double[][] matrix

    Rotation(String name, RotationMatrix rotMatrix) {
        super(name)

        this.matrix = Rotations.rotationMatrixToArrays(rotMatrix)
    }

    @Override
    void transformAtom(Atom atom) {
        Calc.rotate(atom, matrix)
    }

}
