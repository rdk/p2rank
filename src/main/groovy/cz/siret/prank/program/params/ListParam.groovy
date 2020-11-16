package cz.siret.prank.program.params

import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic

/**
 * Parameter which represents s list of values (defined explicitly or as range and step)
 * Used in grid optimization with 'prank ploop'
 */
@CompileStatic
class ListParam {

    String name
    List values

    ListParam(String name, List values) {
        this.name = name
        this.values = values
    }

    static ListParam parse(String name, String svals) {
        ListParam res = new ListParam(name, null)

        def inner = svals.substring(1, svals.length()-1)

        boolean list = svals.startsWith("(") || svals.contains(",")

        if (list) {

            res.values = splitRespectInnerPatentheses(inner, ',' as char)

        } else { // range

            def vals = Sutils.split(inner, ":")

            assert vals.size() == 3, "invalid range definition!"

            double start = vals[0].toDouble()
            double end =   vals[1].toDouble()
            double step =  vals[2].toDouble()

            start = PerfUtils.round(start, 6)
            end =   PerfUtils.round(end, 6)
            step =  PerfUtils.round(step, 6)

            def nums = []
            double next = start
            while (next <= end) {
                nums.add(Formatter.format(next,6))
                next += step
            }

            res.values = nums
        }

        return res
    }

    /**
     * Recursively respects inner parentheses.
     * Includes empty tokens.
     *
     * @param str
     * @param delimiter
     * @return
     */
    static List<String> splitRespectInnerPatentheses(String str, char delimiter) {
        List<String> res = new ArrayList<>()

        int i = 0
        int tokenStart = 0
        while (i < str.size()) {
            if (str.charAt(i).equals(delimiter)) {
                res.add str.substring(tokenStart, i)
                tokenStart = i + 1
            }
            if (str.charAt(i).equals('(' as char)) {
                i = findClosingParenthese(str, i)
            }
            i++
        }
        if (tokenStart <= str.size()) {
            res.add str.substring(tokenStart, str.size())
        }
        return res
    }

    /**
     * @return index of a closing ')' for opening '(' at start (recursively respects inner parentheses) or str.length if not found
     */
    static int findClosingParenthese(String str, int start) {
        int i = start + 1
        while (i < str.size()) {
            if (str.charAt(i).equals(')' as char)) {
                return i
            }
            if (str.charAt(i).equals('(' as char)) {
                i = findClosingParenthese(str, i)
            }
            i++
        }
        return str.length()
    }

    static boolean isListArgValue(String value) {
        ( value.startsWith("[") && value.contains(":") ) || ( value.startsWith("(") && value.contains(",") )
    }

    static List<ListParam> parseListArgs(CmdLineArgs args) {
        args.namedArgs
                .findAll { isListArgValue(it.value) }
                .collect { parse(it.name, it.value) }
                .toList()
    }

    @Override
    public String toString() {
        return "ListParam{" +
                "name='" + name + '\'' +
                ", values=" + values.toListString() +
                '}';
    }

}
