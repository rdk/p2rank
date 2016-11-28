package cz.siret.prank.program.rendering

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Annotated point used for generating visualizations and pocket predictions.
 */
@CompileStatic
class LabeledPoint implements Atom {

    @Delegate Atom point
    int PDBserial

    /**
     * ligandability score histogram - direct output of classifier (hist[0]=unligandable,hist[1]=ligandable)
     * always length=2
     */
    double[] hist  // length=2

    boolean predicted
    boolean observed

    /**
     * pocket number <br>
     * 0 = no pocket
     */
    int pocket = 0


    LabeledPoint(Atom point, double[] hist, boolean observed, boolean predicted) {
        this.point = point
        this.hist = hist
        this.predicted = predicted
        this.observed = observed
    }

    LabeledPoint(Atom point) {
        this.point = point
        this.hist = new double[2]
        this.predicted = false
        this.observed = false
    }

//===========================================================================================================//

    double[] getCoords() {
        return point.coords
    }

    int getPDBserial() {
        return PDBserial
    }

    void setPDBserial(int PDBserial) {
        this.PDBserial = PDBserial
    }

//    /**
//     * @return predicted lgandability score from interval <0,1> (aggregated from histogram)
//     */
//    double getLigandabilityScore() {
//        hist[1] / (hist[0]+hist[1])
//    }

//===========================================================================================================//




}
