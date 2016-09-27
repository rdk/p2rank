package atomtabledata


//
// script for extracting residue atom statistics comming reom xls. file
// results are saved in atomic-properties.csv
//

List<List<String>> table = new ArrayList<>()

new File(args[0]).splitEachLine(",") { List<String> fields ->
    table.add(new ArrayList(fields))
}


class Fun {
    static int colWhereInLineIs(int line, String query, table) {
        int i = 0
        for ( s in table.get(1) ) {
            if (s.trim() == query) {
                return i
            }
            i++
        }
    }
}


Map<String, String> outp = new TreeMap<>()

for (String atm in ["N","CA","C","O","CB","CG","CD"]) {
    int col = Fun.colWhereInLineIs(1,atm, table)

    for (int row in 2..21) {
        if (col+1<table.get(row).size()) {
            def res = table.get(row).get(4)
            def val = table.get(row).get(col+1)

            if (val!=null && !val.isEmpty()) {
                outp.put("${res}.${atm}", val)
                //println "${res}.${atm}  " + val
            }

        }
    }
}

for (int row in 2..21) {
    def res = table.get(row).get(4)

    int col = 5
    while (col < table.get(row).size()-2) {
        def atm = table.get(row).get(col)
        if (atm!=null && !atm.isEmpty() && atm.length()<=3 && !atm.isNumber()) {
            val = table.get(row).get(col+2)

            outp.put("${res}.${atm}", val)
        }
        col++
    }
}

outp.each { println "$it.key,$it.value" }









