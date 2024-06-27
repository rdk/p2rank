package cz.siret.prank.geom.transform;

import org.biojava.nbio.structure.Calc;
import org.biojava.nbio.structure.Structure;
import org.biojava.nbio.structure.StructureException;
import us.ihmc.euclid.matrix.RotationMatrix;
import us.ihmc.euclid.tools.EuclidCoreRandomTools;

import java.util.Random;

/**
 *
 */
public class Rotations {

    /**
     * see https://msl.cs.uiuc.edu/planning/node198.html
     */
    public static RotationMatrix generateRandomRotation(Random rand) {
        return EuclidCoreRandomTools.nextRotationMatrix(rand);
    }

    public static double[][] rotationMatrixToArrays(RotationMatrix mat) {
        double[] aux = new double[9];
        mat.get(aux); // fill in

        double[][] res = new double[3][];

        res[0] = new double[] { aux[0], aux[1], aux[2] };
        res[1] = new double[] { aux[3], aux[4], aux[5] };
        res[2] = new double[] { aux[6], aux[7], aux[8] };

        return res;
    }

    public static void rotateStructureInplace(Structure structure, double[][] rotationMatrix3D) {
        try {
            Calc.rotate(structure, rotationMatrix3D);
        } catch (StructureException e) {
            throw new RuntimeException("Failed to rotate the structure.", e);
        }
    }

    public static void rotateStructureInplace(Structure structure, RotationMatrix rotationMatrix3D) {
        rotateStructureInplace(structure, rotationMatrixToArrays(rotationMatrix3D));
    }

}
