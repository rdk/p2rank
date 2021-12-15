package cz.siret.prank.features.implementation.table

import com.google.common.base.Splitter
import cz.siret.prank.domain.AA
import groovy.transform.CompileStatic

/**
 * AAIndex1 table of residue features
 */
@CompileStatic
class AAIndex1 {

    static class Entry {
        String id
        String description
        Map<AA, Double> values = new HashMap<>()

        Entry(String id) {
            this.id = id
        }
    }

    Map<String, Entry> entryMap = new HashMap<>()

    /**
     * @param text index txt file content
     * @return
     */
    static AAIndex1 parse(String text) {

        AAIndex1 result = new AAIndex1()

        Entry nextEntry

        List<String> lines = text.readLines()

        Iterator<String> it = lines.iterator()
        while (it.hasNext()) {
            String line = it.next()

            if (line.startsWith("H")) {
                String id = line.substring(2).trim()
                nextEntry = new Entry(id)
            } else if (line.startsWith("D")) {
                nextEntry.description = line.substring(2).trim()
            } else if (line.startsWith("I")) {

                String line1 = it.next()
                String line2 = it.next()

                List<Double> num1 = parseNumLine(line1)
                List<Double> num2 = parseNumLine(line2)

                nextEntry.values.put(AA.ALA, num1[0])
                nextEntry.values.put(AA.ARG, num1[1])
                nextEntry.values.put(AA.ASN, num1[2])
                nextEntry.values.put(AA.ASP, num1[3])
                nextEntry.values.put(AA.CYS, num1[4])
                nextEntry.values.put(AA.GLN, num1[5])
                nextEntry.values.put(AA.GLU, num1[6])
                nextEntry.values.put(AA.GLY, num1[7])
                nextEntry.values.put(AA.HIS, num1[8])
                nextEntry.values.put(AA.ILE, num1[9])

                nextEntry.values.put(AA.LEU, num2[0])
                nextEntry.values.put(AA.LYS, num2[1])
                nextEntry.values.put(AA.MET, num2[2])
                nextEntry.values.put(AA.PHE, num2[3])
                nextEntry.values.put(AA.PRO, num2[4])
                nextEntry.values.put(AA.SER, num2[5])
                nextEntry.values.put(AA.THR, num2[6])
                nextEntry.values.put(AA.TRP, num2[7])
                nextEntry.values.put(AA.TYR, num2[8])
                nextEntry.values.put(AA.VAL, num2[9])

                result.entryMap.put(nextEntry.id, nextEntry)
            }

        }

        return result
    }

    public Collection<Entry> getEntries() {
        return entryMap.values()
    }

    //                A/L     R/K     N/M     D/F     C/P     Q/S     E/T     G/W     H/Y     I/V
    //
    //                Ala	A
    //                Arg	R
    //                Asn	N
    //                Asp	D
    //                Cys	C
    //                Glu	E
    //                Gln	Q
    //                Gly	G
    //                His	H
    //                Ile	I
    //                Leu	L
    //                Lys	K
    //                Met	M
    //                Phe	F
    //                Pro	P
    //                Ser	S
    //                Thr	T
    //                Trp	W
    //                Tyr	Y
    //                Val	V

    private static List<Double> parseNumLine(String line) {
        Splitter sp = Splitter.on(' ').trimResults().omitEmptyStrings()

        return new ArrayList<Double>( sp.split(line).toList().collect{ toDouble(it) } )
    }

    private static double toDouble(String s) {
        if (s=="NA") {
            return Double.NaN
        } else {
            return s.toDouble()
        }

    }

}
