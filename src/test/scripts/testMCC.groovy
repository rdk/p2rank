
double calcMCC(double TP, double FP, double TN, double FN) {
    double n = TP*TN - FP*FN
    double d = (TP+FP)*(TP+FN)*(TN+FP)*(TN+FN)
    d = Math.sqrt(d);
    if (d == 0) {
        d = 1;
    }

    return n / d;

    //return ((0G)+TP*TN - FP*FN) / Math.sqrt( ((0G)+TP+FP)*(TP+FN)*(TN+FP)*(TN+FN) )
}

System.println calcMCC(23861,15594,1411622,109115)
