package cz.siret.prank.utils

import groovy.transform.CompileStatic

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

@CompileStatic
class Formatter {

    static DecimalFormatSymbols formatSymbols;

    static List<DecimalFormat> DECIMAL_FORMATS;

    static {
        formatSymbols = new DecimalFormatSymbols();
        formatSymbols.setDecimalSeparator('.' as char);
        //
        DECIMAL_FORMATS = (0..6).collect {
            new DecimalFormat("#."+("#"*it), formatSymbols)
        }.asList()
    }

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
        return new DecimalFormat("##.0", formatSymbols).format(x*100)
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

//===========================================================================================================//

    static String formatTime(long ms) {
        ATimer.formatTime(ms)
    }

}
