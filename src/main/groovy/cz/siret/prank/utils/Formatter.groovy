package cz.siret.prank.utils

import cz.siret.prank.score.results.Evaluation
import groovy.transform.CompileStatic

import java.text.DecimalFormat

@CompileStatic
class Formatter {

    static DecimalFormat[] DECIMAL_FORMATS = (DecimalFormat[]) (0..6).collect { new DecimalFormat("#."+("#"*it)) }.toArray()

    public static String format(double d, int places) {
        DECIMAL_FORMATS[places].format(d)
    }


    static String formatPercent(double x) {
        return new DecimalFormat("##.0").format(x*100)
    }

    static String pc(double x) {
        formatPercent(x)
    }

    static String fmt(Object val) {
        if (val==null)
            "--"
        else if (val instanceof Integer || val instanceof Long)
            sprintf "%8d", val
        else if (val instanceof Number)
            sprintf "%8.3f", val
        else
            ""+val
    }

}
