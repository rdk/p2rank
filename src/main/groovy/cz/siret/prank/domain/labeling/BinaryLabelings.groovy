package cz.siret.prank.domain.labeling

import cz.siret.prank.prediction.metrics.ClassifierStats
import groovy.transform.CompileStatic

/**
 * Calculations related to binary residue labelings
 */
@CompileStatic
class BinaryLabelings {

    static class Stats {
        int total     = 0
        int positives = 0
        int negatives = 0
        int unlabeled = 0
    }

    static Stats getStats(BinaryLabeling labeling) {
        Stats s = new Stats()
        s.total = labeling.labeledResidues.size()

        for (LabeledResidue<Boolean> res : labeling.labeledResidues) {
            if (res.label == null) {
                s.unlabeled++
            } else {
                if (res.label) {
                    s.positives++
                } else {
                    s.negatives++
                }
            }
        }
        
        return s
    }

    static ClassifierStats eval(BinaryLabeling observed, BinaryLabeling predicted) {
        ClassifierStats stats = new ClassifierStats()

        for (int i = 0; i < observed.labeledResidues.size(); i++) {
            Boolean obs = observed.labeledResidues[i].label
            Boolean pred = predicted.labeledResidues[i].label

            stats.addPrediction(obs, pred)
        }

        return stats
    }

    static ClassifierStats eval(BinaryLabeling observed, BinaryLabeling predicted, ResidueLabeling<Double> predictedScores) {
        ClassifierStats stats = new ClassifierStats()

        for (int i = 0; i < observed.labeledResidues.size(); i++) {
            Boolean obs = observed.labeledResidues[i].label
            Boolean pred = predicted.labeledResidues[i].label
            double score = predictedScores.labeledResidues[i].label

            if (score < 0d) score = 0d
            score = 2*Math.atan(score) / Math.PI  // normalize to <0,1> (any monotonous function would do)

            stats.addPrediction(obs, pred, score)
        }

        return stats
    }

}
