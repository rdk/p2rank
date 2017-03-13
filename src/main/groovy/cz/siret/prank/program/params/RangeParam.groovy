package cz.siret.prank.program.params

import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.StrUtils
import groovy.transform.CompileStatic

/**
 * parameter witch range of values
 */
@CompileStatic
class RangeParam {

    String name
    List values

    RangeParam(String name, List values) {
        this.name = name
        this.values = values
    }

    static RangeParam parse(String name, String svals) {
        RangeParam res = new RangeParam(name, null)

        def inner = svals.substring(1, svals.length()-1)

        boolean list = svals.startsWith("(") || svals.contains(",")

        if (list) {

            res.values = StrUtils.split(inner, ",")

        } else { // range

            def vals = StrUtils.split(inner, ":")

            assert vals.size() == 3, "inavlid range definition!"

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

    static boolean isRangedArgValue(String value) {
        ( value.startsWith("[") && value.contains(":") ) || ( value.startsWith("(") && value.contains(",") )
    }

    static List<RangeParam> parseRangedArgs(CmdLineArgs args) {
        args.namedArgs
                .findAll { RangeParam.isRangedArgValue(it.value) }
                .collect { RangeParam.parse(it.name, it.value) }
                .toList()
    }

    @Override
    public String toString() {
        return "RangeParam{" +
                "name='" + name + '\'' +
                ", values=" + values.toListString() +
                '}';
    }

}
