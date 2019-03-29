package cz.siret.prank.prediction.metrics

/**
 * Point prediction of scoring binary classifier
 */
class PPred {

    boolean observed   // true if observed class is 1
    double score       // predicted score for class 1

    PPred(boolean observed, double score) {
        this.observed = observed
        this.score = score
    }
    
}
