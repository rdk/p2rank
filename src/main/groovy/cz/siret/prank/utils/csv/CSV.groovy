package cz.siret.prank.utils.csv

import cz.siret.prank.utils.PerfUtils
import groovy.transform.CompileStatic

@CompileStatic
class CSV {

    String csvString

//===========================================================================================================//

    CSV(String csvString) {
        this.csvString = csvString
    }

    String tabulated() {
        tabulate(csvString)
    }

    String tabulated(int... colWidths) {
        tabulate(csvString, colWidths)
    }

    @Override
    String toString() {
        csvString
    }

//===========================================================================================================//

    static CSV fromDoubles(List<Double> vect) {
        StringBuilder sb = new StringBuilder(vect.size()*10)

        for (Double d : vect) {
            if (d==null || d.isNaN()) {
                sb << "0"
            } else {
                sb << PerfUtils.formatDouble(d)
            }
            sb << ","
        }
        sb.deleteCharAt(sb.size()-1)

        return new CSV(sb.toString())
    }

    static String tabulate(String csvString) {
        return csvString.replace(',', '\t')
    }

    static String tabulate(CSV csv) {
        return csv.tabulated()
    }

    static String tabulate(String str, int... colWidths) {
        str.readLines().collect { String line ->
            if (line.isEmpty()) return line
            def split = line.split(',')
            int col = 0
            def fmt = split.collect { "%-${colWidths[col++%colWidths.length]}s " }.join("")
            return System.sprintf(fmt, split)
        }.join('\n')
    }

}
