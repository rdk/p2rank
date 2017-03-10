package cz.siret.prank.score.results

import groovy.transform.CompileStatic

import java.text.DecimalFormat

/**
 * Binary classifier statistics collector and calculator
 */
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

    Stats stats = new Stats()

    ClassifierStats() {
        nclasses = 2
        op = new int[nclasses][nclasses]
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

    /**
     *
     * @param obs
     * @param pred
     * @param score predicted score from iterval <0,1>
     */
    void addCase(boolean obs, boolean pred, double score) {

        double obsv = obs ? 1 : 0
        double e = Math.abs(obsv-score)
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

        op[obs?1:0][pred?1:0]++
        count++
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

    /**
     * flyweight class for 1D statistics 
     */
    class Stats {

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

        double getME()         { div sumE, count        }
        double getMEpos()      { div sumEpos, count     }
        double getMEneg()      { div sumEneg, count     }
        double getMEbalanced() { (MEneg + MEpos) / 2    }

        double getMSE()       { div sumSE, count      }
        double getMSEpos()    { div sumSEpos, count      }
        double getMSEneg()    { div sumSEneg, count      }
        double getMSEbalanced() { (MSEneg + MSEpos) / 2    }

        private double getFWeighted(double beta) {
            double betaSqr = beta*beta
            div ( (1+betaSqr)*p*r , r + betaSqr*p  )
        }

        Map<String, Double> toMap() {
            Map<String, Double> res = new HashMap<>()
            for (PropertyValue pv : this.metaPropertyValues) {
                if (pv.type == Double.class) {
                    res.put( pv.name.toUpperCase(), (Double) pv.value )
                }
            }
            return res
        }

    }

    //===========================================================================================================//

    Map<String, Double> getStatsMap() {
        stats.toMap()
    }

    //===========================================================================================================//

    //@CompileStatic
    String toCSV(String classifierDesc) {

        Stats s = stats

        double P = s.p      // precision / positive predictive value
        double R = s.r       // recall / sensitivity / true positive rate

        StringBuilder sb = new StringBuilder()

        stats.with {
            sb << "classifier: ${classifierDesc}\n"
            sb << "\n"
            sb << "n:,$count\n"
            sb << "\n"
            sb << ",TN   , FP, (spc)\n"
            sb << ",FN   , TP, (r)\n"
            sb << ",(npv),(p)\n"
            sb << "\n"
            sb << "pred:  , [0], [1]\n"
            sb << "obs[0] , ${tn}, ${fp}, ${pc(SPC)}\n"
            sb << "obs[1] , ${fn}, ${tp}, ${pc(R)}\n"
            sb << "       , ${pc(NPV)}, ${pc(P)}\n"
            sb << "\n"
            sb << "%:\n"
            sb << ", ${rel(tn)}, ${rel(fp)}\n"
            sb << ", ${rel(fn)}, ${rel(tp)}\n"
            sb << "\n"
            sb << "ACC:, ${format(ACC)}, accuracy\n"
            sb << "\n"
            sb << "P:, ${format(P)}, precision / positive predictive value    ,,TP / (TP + FP)\n"
            sb << "R:, ${format(R)}, recall / sensitivity / true positive rate,,TP / (TP + FN)\n"
            sb << "\n"
            sb << "NPV:, ${format(NPV)}, negative predictive value       ,,TN / (TN + FN)\n"
            sb << "SPC:, ${format(SPC)}, specificity / true negative rate,,TN / (TN + FP)\n"
            sb << "\n"
            sb << "FM:, ${format(f1)}, F-measure\n"
            sb << "MCC:, ${format(MCC)}, Matthews correlation coefficient\n"

            sb << "\n"
            sb << "ME:, ${format(ME)}, Mean error\n"
            sb << "MEpos:, ${format(MEpos)}, ME on positive observations\n"
            sb << "MEneg:, ${format(MEneg)}, Mean error on negative observations\n"
            sb << "MEbal:, ${format(MEbalanced)}, Mean error balanced\n"
            sb << "\n"
            sb << "MSE:, ${format(MSE)}, Mean squared error\n"
            sb << "MSEpos:, ${format(MSEpos)}, MSE on positive observations\n"
            sb << "MSEneg:, ${format(MSEneg)}, MSE on negative observations\n"
            sb << "MSEbal:, ${format(MSEbalanced)}, Mean error balanced\n"
        }

        return sb.toString()
    }

}
