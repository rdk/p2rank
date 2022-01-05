package cz.siret.prank.program.params

import cz.siret.prank.utils.*
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import static cz.siret.prank.utils.Sutils.splitRespectInnerParentheses
import static cz.siret.prank.utils.Sutils.trimEach

/**
 * Parameter which represents s list of values (defined explicitly or as range and step)
 * Used in grid optimization with 'prank ploop'
 */
@CompileStatic
class ListParam<T> implements IterativeParam<T> {

    private final String name
    private final List<T> values
    int i = 0

    ListParam(String name, List<T> values) {
        this.name = name
        this.values = values
    }

    @Override
    String getName() {
        return name
    }

    @Override
    List<T> getValues() {
        return values
    }

    @Override
    T getNextValue() {
        return Cutils.listElement(i++, values)
    }

//===========================================================================================================//


    static ListParam parse(String name, String value) {

        value = value.trim()
        boolean isList = value.startsWith("(") || value.contains(",")
        def inner = value.substring(1, value.length()-1)
        def values = []

        if (isList) {

            values = trimEach(splitRespectInnerParentheses(inner, ',' as char))

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

            values = nums
        }

        return new ListParam(name, values)
    }



    /**
     * TODO improve to use MetaClass info instead of type of current value
     * @param name
     * @return
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    static boolean isListParam(String name) {
        try {
            return Params.inst."$name" instanceof List
        } catch (Exception e) {
            // skip for cmd line params that are not attributes of Params, like "-t"
            return false
        }
    }

    /**
     * True if parameter value is in iterative/list format. I.e. represents multiple values for grid optimiation.
     */
    static boolean isIterativeArgValue(String name, String value) {
        value = value.trim()

        if (value.startsWith("[") && value.contains(":")) return true

        if (isListParam(name)) {  // for list params we want to see list of lists
            value = value.replace(' ', '')
            return value.startsWith("((")
        } else {
            value.startsWith("(")
        }
    }

    static List<ListParam> parseListArgs(CmdLineArgs args) {
        args.namedArgs
                .findAll { isIterativeArgValue(it.name, it.value) }
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
