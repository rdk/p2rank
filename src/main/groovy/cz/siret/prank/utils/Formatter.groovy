package cz.siret.prank.utils

import groovy.transform.CompileStatic

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

@CompileStatic
class Formatter {

    static final DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols() {
        {
            this.setDecimalSeparator('.' as char)
        }
    }

    static final List<DecimalFormat> DECIMAL_FORMATS = (0..10).collect {
        new DecimalFormat("0."+("#"*it), formatSymbols)
    }

    static final List<DecimalFormat> DECIMAL_FORMATS_0 = (0..10).collect {
        new DecimalFormat("0."+("0"*it), formatSymbols)
    }


    static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.00", formatSymbols)
    static final DecimalFormat PROB_SCORE_FORMAT = new DecimalFormat("0.000", formatSymbols)
    static final DecimalFormat COORD_FORMAT = new DecimalFormat("0.0000", formatSymbols)

    static String format(double d, int places) {
        DECIMAL_FORMATS[places].format(d)
    }

    static String format0(double d, int places) {
        DECIMAL_FORMATS_0[places].format(d)
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
            sprintf "%8.4f", val
        else
            ""+val
    }

//===========================================================================================================//

    static String formatScore(double score) {
        SCORE_FORMAT.format(score)
    }

    static String formatProbScore(double score) {
        PROB_SCORE_FORMAT.format(score)
    }

    static String formatCoord(double coord) {
        COORD_FORMAT.format(coord)
    }

//===========================================================================================================//

    static String formatTime(long ms) {
        ATimer.formatTime(ms)
    }

    static String formatSeconds(long ms) {
        sprintf "%8.3f", ((double)ms) / 1000d
    }

}
