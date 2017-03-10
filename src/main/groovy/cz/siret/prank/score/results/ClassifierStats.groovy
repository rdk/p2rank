package cz.siret.prank.score.results

import groovy.transform.CompileStatic

import java.text.DecimalFormat

@CompileStatic
class ClassifierStats {

    int[][] op    // [observed][predicted]
    int count = 0
    int nclasses

    double sumE = 0
    double sumEpos = 0
    double sumEneg = 0
    double sumSE = 0
    double sumSEpos = 0
    double sumSEneg = 0

    String name

    ClassifierStats(int nclases) {
        op = new int[nclases][nclases]
        this.nclasses = nclases
    }

    void addAll(ClassifierStats add) {
        for (int i=0; i!=nclasses; ++i)
            for (int j=0; j!=nclasses; ++j)
                op[i][j] += add.op[i][j]

        count += add.count

        sumE += add.sumE
        sumEpos += add.sumEpos
        sumEneg += add.sumEneg
        sumSE += add.sumSE
        sumSEpos += add.sumSEpos
        sumSEneg += add.sumSEneg
    }

    void addCase(int observed, int predicted) {
        op[observed][predicted]++
        count++
    }

    void addCase(boolean obs, boolean pred, double p1) {

        double obsv = obs ? 1 : 0
        double e = Math.abs(obsv-p1)
        double se = e*e

        sumE += e
        sumSE += se

        if (obs) {
            sumEpos += e
            sumSEpos += se
        } else {
            sumEneg += e
            sumSEneg += se
        }


        addCase(obs?1:0, pred?1:0)
    }

    double fmt(double x) {
        return (double)Math.round(1000*x)/10
    }

    double calcMCC(double TP, double FP, double TN, double FN) {
        double n = TP*TN - FP*FN
        double d = (TP+FP)*(TP+FN)*(TN+FP)*(TN+FN)
        d = Math.sqrt(d);
        if (d == 0) {
            d = 1;
        }

        return n / d;
    }

    static String format(double x) {
        return new DecimalFormat("#.####").format(x)
    }

    private double rel(double x) {
        return pc((double)x/count)
    }

    private double pc(double x) {
        x *= 100
        return ((double)Math.round(x*10)) / 10
    }

    double div(double a, double b) {
        if (b==0)
            return Double.NaN
        return a / b
    }

//===========================================================================================================//

    double getTp() { op[1][1] }
    double getFp() { op[0][1] }
    double getTn() { op[0][0] }
    double getFn() { op[1][0] }

    double getP() {
        div tp , (tp + fp)
    }
    double getR() {
        div tp , (tp + fn)
    }



    double getF1() {
        div( (2*(p*r)) , (p+r) )
    }

    double getF2() {
        getFWeighted(2)
    }
    double getF05() {
        getFWeighted(0.5)
    }


    double getMCC() {
        calcMCC(tp, fp, tn, fn)
    }

    /** negative predictive value */
    double getNPV() {
        div tn , (tn + fn)
    }

    /** specificity = true negative rate */
    double getSPC() {
        div tn , (tn + fp)
    }

    /** accuraccy */
    double getACC() {
        div( (tp + tn) , count )
    }

    double getTPX() {
        div tp, tp + fn + fp
    }

    /** false positive rate */
    double getFPR() {
        div fp , (fp + tn)
    }

    /** false negative rate */
    double getFNR() {
        div fn , (tp + fn)
    }

    /** positive likelihood ratio */
    double getPLR() {
        div r, FPR
    }

    /** negative likelihood ratio */
    double getNLR() {
        div FNR, SPC
    }

    /** diagnostic odds ratio */
    double getDOR() {
        div PLR, NLR
    }

    /** false discovery rate */
    double getFDR() {
        div fp , (tp + fp)
    }

    /** false ommision rate */
    double getFOR() {
        div fn , (fn + tn)
    }


    double getFWeighted(double beta) {
        double betaSqr = beta*beta
        div ( (1+betaSqr)*p*r , r + betaSqr*p  )
    }

//===========================================================================================================//

    double getME()         { div sumE, count        }
    double getMEpos()      { div sumEpos, count     }
    double getMEneg()      { div sumEneg, count     }
    double getMEbalanced() { (MEneg + MEpos) / 2    }

    double getMSE()       { sumSE / count      }
    double getMSEpos()    { sumSEpos / count      }
    double getMSEneg()    { sumSEneg / count      }
    double getMSEbalanced() { (MSEneg + MSEpos) / 2    }

    //===========================================================================================================//

    //@CompileStatic
    String toCSV(String classifierDesc) {

        double P = p      // precision / positive predictive value
        double R = r       // recall / sensitivity / true positive rate

        StringBuilder s = new StringBuilder()
        s << "classifier: ${classifierDesc}\n"
        s << "\n"
        s << "n:,$count\n"
        s << "\n"
        s << ",TN   , FP, (spc)\n"
        s << ",FN   , TP, (r)\n"
        s << ",(npv),(p)\n"
        s << "\n"
        s << "pred:  , [0], [1]\n"
        s << "obs[0] , ${tn}, ${fp}, ${pc(SPC)}\n"
        s << "obs[1] , ${fn}, ${tp}, ${pc(R)}\n"
        s << "       , ${pc(NPV)}, ${pc(P)}\n"
        s << "\n"
        s << "%:\n"
        s << ", ${rel(tn)}, ${rel(fp)}\n"
        s << ", ${rel(fn)}, ${rel(tp)}\n"
        s << "\n"
        s << "ACC:, ${format(ACC)}, accuracy\n"
        s << "\n"
        s << "P:, ${format(P)}, precision / positive predictive value    ,,TP / (TP + FP)\n"
        s << "R:, ${format(R)}, recall / sensitivity / true positive rate,,TP / (TP + FN)\n"
        s << "\n"
        s << "NPV:, ${format(NPV)}, negative predictive value       ,,TN / (TN + FN)\n"
        s << "SPC:, ${format(SPC)}, specificity / true negative rate,,TN / (TN + FP)\n"
        s << "\n"
        s << "FM:, ${format(f1)}, F-measure\n"
        s << "MCC:, ${format(MCC)}, Matthews correlation coefficient\n"

        s << "\n"
        s << "ME:, ${format(ME)}, Mean error\n"
        s << "MEpos:, ${format(MEpos)}, ME on positive observations\n"
        s << "MEneg:, ${format(MEneg)}, Mean error on negative observations\n"
        s << "MEbal:, ${format(MEbalanced)}, Mean error balanced\n"
        s << "\n"
        s << "MSE:, ${format(MSE)}, Mean squared error\n"
        s << "MSEpos:, ${format(MSEpos)}, MSE on positive observations\n"
        s << "MSEneg:, ${format(MSEneg)}, MSE on negative observations\n"
        s << "MSEbal:, ${format(MSEbalanced)}, Mean error balanced\n"

        return s.toString()
    }

}
