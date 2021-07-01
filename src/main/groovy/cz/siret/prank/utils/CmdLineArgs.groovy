package cz.siret.prank.utils

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import javax.annotation.Nullable

/**
 * Represents parsed command line arguments
 * <p>
 * prog unnamedArg -switch1 -arg1 val1 -switch2 -arg2 val2
 * </p>
 */
@CompileStatic
class CmdLineArgs {

    static class NamedArg {
        String name
        String value

        String toString() {
            return name + "=" + value
        }
    }

//===========================================================================================================//

    boolean hasListParams = false

    String[] argList

    /** "-count 7" .... maps to count->7 */
    Map<String,String> namedArgMap = new TreeMap<>()

    /** arguments with '-' or '--' prefix */
    List<NamedArg> namedArgs = new ArrayList<>()

    /** arguments with '-' or '--' prefix but no followup value */
    List<String> switches = new ArrayList<>()

    /** arguments without '-' or '--' prefix that are not values of any dashed argument */
    List<String> unnamedArgs = new ArrayList<>()

//===========================================================================================================//

    static CmdLineArgs parse(String[] args) {
        return new CmdLineArgs(args)
    }

    private CmdLineArgs(String[] args) {
        this.argList = args

        int i = 0
        while (i < args.length) {
            String arg = args[i]
            String next = (i+1<args.length) ? args[i+1] : null

            if (isArgName(arg)) {
                String argName = stripArgName(arg)
                if (next==null || isArgName(next) ) {
                    switches.add(argName)
                } else {
                    String val = next
                    namedArgs.add(new NamedArg(name: argName, value: val))
                    namedArgMap.put(argName, val)
                    i++
                }
            } else {
                unnamedArgs.add(arg)
            }
            i++
        }
    }

    // private (Groovy 3 has a problem with it)
    String stripArgName(String arg) {
        if (arg.startsWith("--")) return arg.substring(2)
        if (arg.startsWith("-")) return arg.substring(1)
        return arg
    }

    // private (Groovy 3 has a problem with it)
    boolean isArgName(String arg) {
        if (arg==null) return false
        return arg.startsWith("-") || arg.startsWith("--")
    }

    String get(String argName) {
        return namedArgMap.get(argName)
    }

    String get(String ... aliases) {
        for (String alias : aliases) {
            if (namedArgMap.containsKey(alias)) {
                return namedArgMap.get(alias)
            }
        }
        return null
    }

    /**
     * @return basically all arg names that are prefixed with '-' or '--' (without the prefix)
     */
    List<String> getNamedArgsAndSwitches() {
        return getSwitches() + (getNamedArgs()*.name as List<String>)
    }

    boolean hasSwitch(String... vals) {
        for (String s : vals) {
            if (switches.contains(s)) return true
        }
        return false
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    void applyToObject(Object obj) {
        for (String propName : obj.properties.keySet()) {
            if (namedArgMap.containsKey(propName)) {

                if (obj."$propName" instanceof String || obj."$propName" == null) {
                    obj."$propName" = get(propName)
                } else {
                    Class propClass = obj."$propName".class
                    obj."$propName" = propClass.valueOf(get(propName)) // for enums
                }
            } else if (switches.contains(propName)) {
                obj."$propName" = true
            }
        }
    }

    void shiftUnnamedArgs() {
        if (!unnamedArgs.isEmpty()) {
            unnamedArgs = unnamedArgs.tail()
        }
    }

    @Nullable
    String getFirstUnnamedArg() {
        if (!unnamedArgs.isEmpty()) {
            return unnamedArgs[0]
        }
        return null
    }

    @Nullable
    String popFirstUnnamedArg() {
        String res = getFirstUnnamedArg()
        shiftUnnamedArgs()
        return res
    }

    @Override
    String toString() {
        return Sutils.toStr(this)
    }
    
}
