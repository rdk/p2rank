package cz.siret.prank.domain.labeling

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

    static Stats getStats(BinaryResidueLabeling labeling) {
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


}
