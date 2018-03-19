package cz.siret.prank.utils

import groovy.transform.CompileStatic

import java.text.DecimalFormat

@CompileStatic
class Formatter {

    static List<DecimalFormat> DECIMAL_FORMATS = (0..6).collect { new DecimalFormat("#."+("#"*it)) }.asList()

    static String format(double d, int places) {
        DECIMAL_FORMATS[places].format(d)
    }

    static String formatd(Double d, int places) {
        d == null ? "null" : format(d, places)
    }

    static formatNumbers(List<Double> list, int places) {
        "[" + list.collect { formatd(it, places) }.join(", ") + "]"
    }

    static String bton(boolean b) {
        b ? "1" : "0"
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
