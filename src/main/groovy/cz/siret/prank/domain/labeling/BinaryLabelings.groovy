package cz.siret.prank.domain.labeling

import cz.siret.prank.prediction.metrics.ClassifierStats

/**
 * Calculations arond binary residua labelings
 */
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

        labeling.labeledResidues.each {
            if (it.label == null) {
                s.@unlabeled++
            } else {
                if (it.label == true) {
                    s.@positives++
                } else {
                    s.@negatives++
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
