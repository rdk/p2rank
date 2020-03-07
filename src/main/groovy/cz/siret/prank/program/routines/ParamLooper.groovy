package cz.siret.prank.program.routines

import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.results.EvalResults
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Cutils.prefixMapKeys
import static cz.siret.prank.utils.Futils.appendl
import static cz.siret.prank.utils.Futils.exists
import static cz.siret.prank.utils.Futils.mkdirs

/**
 * Base class for hyperparameter optimization routines
 */
@Slf4j
@CompileStatic
abstract class ParamLooper extends Routine {

    List<Step> steps = new ArrayList<>()

    String statsTableFile
    String selectedStatsFile
    String plotsDir
    String tablesDir
    String runsDir

    Map<String, String> tables2D = new LinkedHashMap()

    ParamLooper(String outdir) {
        super(outdir)
        plotsDir = "$outdir/plots"
        statsTableFile = "$outdir/param_stats.csv"
        selectedStatsFile = "$outdir/selected_stats.csv"
        runsDir = "$outdir/runs"
    }

    ParamLooper init() {
        mkdirs(outdir)
        mkdirs(runsDir)
        writeParams(outdir)
        this
    }

    /**
     * Execute and proecss resuts of one experiment step
     * init() must be called before first calling this method
     */
    public EvalResults processStep(Step step, String dirLabel, Closure<EvalResults> closure) {
        def stepTimer = startTimer()

        step.applyToParams(params)

        String stepDir = "$runsDir/$dirLabel"
        EvalResults res = closure.call(stepDir)     // execute an experiment in closure for a step

        step.results.putAll( res.stats )
        step.results.TIME_MINUTES = stepTimer.minutes // absolute time spent on this step
        if (res.subResults.size() > 1) {
            step.results.putAll prefixMapKeys(res.statsStddev, '_stddev_')
        }

        // save stats
        if (!exists(statsTableFile)) {
            appendl statsTableFile, step.header
            appendl selectedStatsFile, step.getHeader(params.selected_stats)
        }
        appendl statsTableFile, step.toCSV()
        appendl selectedStatsFile, step.toCSV(params.selected_stats)

        return res
    }

//===========================================================================================================//

    static String fmt(Object x) {
        if (x==null) return ""

        if (x instanceof Double) {
            return sprintf("%8.4f", x)
        } else {
            return x.toString()
        }
    }

//===========================================================================================================//

    @TupleConstructor
    static class Step {

        List<ParamVal> params = new ArrayList<>() // order is important
        Map<String, Double> results = new LinkedHashMap()

        void applyToParams(Params globalParams) {
            params.each { globalParams.setParam(it.name, it.value) }
        }

        Step extendWith(String pname, Object pval) {
            return new Step(params: params + new ParamVal(name: pname, value: pval))
        }

        String getLabel() {
            params.collect { it.name + "." + it.value }.join(".")
        }

        String getHeader() {
            (params*.name).join(', ') + ', ' + results.keySet().join(', ')
        }

        String getHeader(List<String> selectedStats) {
            (params*.name).join(', ') + ', ' + selectedStats.join(', ')
        }

        @CompileDynamic
        String toCSV() {
            params.collect{ fmt(it.value) }.join(', ') + ', ' + results.values().collect{ fmt(it) }.join(', ')
        }

        @CompileDynamic
        String toCSV(List<String> selectedStats) {
            params.collect{ fmt(it.value) }.join(', ') + ', ' + selectedStats.collect{ fmt(results.get(it)) }.join(', ')
        }

        public String toString() {
            return "Step{${params.toListString()}, ${results.toMapString()}}";
        }

    }

    @TupleConstructor
    public static class ParamVal {
        String name
        Object value

        public String toString() {
            name + ":" + value
        }
    }

}
