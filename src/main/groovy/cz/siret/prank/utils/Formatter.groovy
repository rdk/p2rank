package cz.siret.prank.utils

import groovy.transform.CompileStatic

import java.text.DecimalFormat

@CompileStatic
class Formatter {

    static DecimalFormat[] DECIMAL_FORMATS = (DecimalFormat[]) (0..6).collect { new DecimalFormat("#."+("#"*it)) }.toArray()

    public static String format(double d, int places) {
        DECIMAL_FORMATS[places].format(d)
    }

}
