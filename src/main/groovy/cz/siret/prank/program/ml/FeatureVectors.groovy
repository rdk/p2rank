package cz.siret.prank.program.ml

import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import weka.core.Instances

/**
 *
 */
@CompileStatic
class FeatureVectors {

    private Instances instances
    private int count
    private int positives
    private int negatives

    FeatureVectors(Instances instances, int positives, int negatives) {
        this.instances = instances
        this.positives = positives
        this.negatives = negatives
        this.count = positives + negatives
    }

    Instances getInstances() {
        return instances
    }

    int getCount() {
        return count
    }

    int getPositives() {
        return positives
    }

    int getNegatives() {
        return negatives
    }

//===========================================================================================================//

    static FeatureVectors fromInstances(Instances data) {
        int positives = WekaUtils.countPositives(data)
        int negatives = WekaUtils.countNegatives(data)
        return new FeatureVectors(data, positives, negatives)
    }
    
}
