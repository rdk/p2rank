package cz.siret.prank.prediction.metrics;

/**
 * Point prediction of scoring binary classifier
 */
public class PPred {

    private boolean observed;
    private double score;

    public PPred(boolean observed, double score) {
        this.observed = observed;
        this.score = score;
    }

    public boolean isObserved() {
        return observed;
    }

    public void setObserved(boolean observed) {
        this.observed = observed;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

}
