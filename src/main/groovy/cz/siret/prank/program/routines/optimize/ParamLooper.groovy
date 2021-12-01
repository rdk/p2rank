package cz.siret.prank.program.routines.optimize

import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.results.EvalResults
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Cutils.prefixMapKeys
import static cz.siret.prank.utils.Futils.*

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

    ParamLooper(String outdir) {
        super(outdir)
        plotsDir = "$outdir/plots"
        tablesDir = "$outdir/tables"
        statsTableFile = "$outdir/param_stats.csv"
        selectedStatsFile = "$outdir/selected_stats.csv"
        runsDir = "$outdir/runs"
    }

    ParamLooper init() {

        delete(plotsDir)
        delete(runsDir)
        delete(statsTableFile)
        delete(selectedStatsFile)

        mkdirs(outdir)
        mkdirs(runsDir)
        writeParams(outdir)

        this
    }

    /**
     * Execute and process results of one experiment step
     * init() must be called before first calling this method
     */
    EvalResults processStep(Step step, String dirLabel, Closure<EvalResults> closure) {
        def stepTimer = startTimer()

        step.applyToParams(params)

        String stepDir = "$runsDir/$dirLabel"
        EvalResults res = closure.call(stepDir)     // execute an experiment in closure for a step

        step.resultStats.putAll( res.stats )
        step.resultStats.TIME_STEP_MINUTES = stepTimer.minutes // absolute time spent on this step
        if (res.subResults.size() > 1) {
            step.resultStats.putAll prefixMapKeys(res.statsStddev, '_stddev_')
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
            return sprintf("%8.5f", x)
        } else {
            return x.toString()
        }
    }

//===========================================================================================================//

    @TupleConstructor
    static class Step {

        List<ParamVal> params = new ArrayList<>() // order is important
        Map<String, Double> resultStats = new LinkedHashMap()

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
            (params*.name).join(', ') + ', ' + resultStats.keySet().join(', ')
        }

        String getHeader(List<String> selectedStats) {
            (params*.name).join(', ') + ', ' + selectedStats.join(', ')
        }

        private String getParamValueColumns() {
            // param value may contain commas
            params.collect{'"' + fmt(it.value) + '"' }.join(', ')
        }

        @CompileDynamic
        String toCSV() {
            paramValueColumns + ', ' + resultStats.values().collect{ fmt(it) }.join(', ')
        }

        @CompileDynamic
        String toCSV(List<String> selectedStats) {
            paramValueColumns + ', ' + selectedStats.collect{ fmt(resultStats.get(it)) }.join(', ')
        }

        String toString() {
            return "Step{${params.toListString()}, ${resultStats.toMapString()}}"
        }

    }

    @TupleConstructor
    static class ParamVal {
        String name
        Object value

        String toString() {
            name + ":" + value
        }
    }

}
