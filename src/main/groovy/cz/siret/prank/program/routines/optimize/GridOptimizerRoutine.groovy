package cz.siret.prank.program.routines.optimize

import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.ListParam
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.rlang.RPlotter
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.sanitizeFilename

/**
 * Routine for grid optimization (prank ploop).
 * Loops through values of one or more ListParam and produces resulting statistics and plots.
 */
@Slf4j
@CompileStatic
class GridOptimizerRoutine extends ParamLooper {

    List<ListParam> listParams
    List<String> gridVariablesNames


    GridOptimizerRoutine(String outdir, List<ListParam> listParams) {
        super(outdir)
        this.listParams = listParams
        this.gridVariablesNames = listParams*.name
    }

    private String prepareDirLabel(Step step) {
        return sanitizeFilename(step.label)
    }

    /**
     * Iterate through al steps running closure.
     * Step is a particular assignment of flexible variables, (e.g. "param1=val1 param2=val2")
     * @param closure takes outdir as param
     *
     * TODO: merge with code in Experiments, there is no point in separation with closure
     */
    void runGridOptimization(Closure<EvalResults> closure) {
        def timer = startTimer()

        steps = generateSteps(listParams)
        log.info "STEPS: " + steps.toListString().replace("Step","\nStep")

        for (Step step in steps) {
            processStep(step, prepareDirLabel(step), closure)

            if (listParams.size()==2) {
                make2DTables(step)
            }
        }

        logTime "param iteration finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        if (params.r_generate_plots) {
            makePlots()
        }

        try {
            if (params.ploop_delete_runs) {
                Futils.delete(runsDir)
            } else if (params.ploop_zip_runs) {
                Futils.zipAndDelete(runsDir, Futils.ZIP_BEST_COMPRESSION)
            }
        } catch (Exception e) {
            log.error("failed to delete directory", e)
        }

        logTime "ploop routine finished in $timer.formatted"
    }

//===========================================================================================================//

    private void make2DTables(Step step) {
        tablesDir = "$outdir/tables"
        tables2D = [:]
        step.results.each {
            make2DTable(it.key as String)
        }
    }

    private makePlots() {
        if (listParams.size()==1 || listParams.size()==2) {
            def timer = startTimer()
            write "generating R plots..."
            mkdirs(plotsDir)
            if (listParams.size()==1) {
                make1DPlots()
            } else if (listParams.size()==2) {
                make2DPlots()
            }
            logTime "generating plots finished in $timer.formatted"
        }
    }

    private int getNumRThreads() {
        Math.min(params.threads, params.r_threads)
    }

    boolean isGridVariable(String name) {
        gridVariablesNames.contains(name)
    }

    boolean plotVariable(String name) {
        if (isGridVariable(name)) {
            return false
        }
        if (!params.r_plot_stddevs && name.startsWith('_stddev_')) {
            return false
        }
        return true
    }

    @CompileDynamic
    private make2DPlots() {
        def vars = tables2D.keySet().findAll { plotVariable(it) }.asList()
        GParsPool.withPool(numRThreads) {
            vars.eachParallel { String key ->
                String value = tables2D.get(key)
                String label = key
                String fname = Futils.absSafePath(value)
                String labelX = listParams[1].name
                String labelY = listParams[0].name
                new RPlotter(plotsDir).plotHeatMapTable(fname, label, labelX, labelY)
            }
        }
    }

    private make1DPlots() {
        def plotter = new RPlotter(statsTableFile, plotsDir)
        def vars = plotter.header.findAll { plotVariable(it) }.asList()
        plotter.plot1DVariables(vars, numRThreads)
    }

    private make2DTable(String statName) {
        ListParam paramX = listParams[0]
        ListParam paramY = listParams[1]

        Map<List, Double> valueMap = new HashMap()
        for (Step s : steps) {
            def key = [ s.params[0].value, s.params[1].value ]
            valueMap.put( key, s.results.get(statName) )
        }

        StringBuilder sb = new StringBuilder()
        //sb << "# resName \n"
        sb << "${paramX.name}/${paramY.name}," + paramY.values.collect { quote(it) }.join(",") + "\n"  // header

        for (def varX : paramX.values) {
            def row = paramY.values.collect { varY -> valueMap.get([varX, varY]) }.collect { fmt it }.join(",")

            sb << "" + quote(varX) + "," + row + "\n"
        }

        String fname = "$tablesDir/${statName}.csv"
        tables2D.put(statName, fname)
        Futils.writeFile fname, sb.toString()
    }

    private String quote(Object s) {
        if (s == null) return null
        return "\"$s\""
    }

    private List<Step> generateSteps(List<ListParam> lparams) {

        if (Cutils.empty(lparams)) {
            throw new PrankException("No list params were provided for grid optimization.")
        }

        genStepsRecur(new ArrayList<Step>(), new Step(), lparams)
    }
    
    private List<Step> genStepsRecur(List<Step> steps, Step base, List<ListParam> rparams) {
        if (rparams.empty) {
            steps.add(base)
            return steps
        }

        ListParam rparam = rparams.head()
        for (Object val : rparam.values) {
            Step deeperStep = base.extendWith(rparam.name, val)
            genStepsRecur(steps, deeperStep, rparams.tail())
        }

        return steps
    }

}
