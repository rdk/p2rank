package cz.siret.prank.utils.csv


import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class CsvRow {

    enum Justify { RIGHT, LEFT }


    List<String> cells = new ArrayList<>()


    void add(String val) {
        cells.add(val)
    }

    void add(Justify justify, int colLength, String val) {
        if (val == null) {
            val = ""
        }

        if (justify == Justify.RIGHT) {
            val = val.padLeft(colLength)
        } else {
            val = val.padRight(colLength)
        }

        add(val)
    }

    @Override
    String toString() {
        return cells.join(", ")
    }

}
