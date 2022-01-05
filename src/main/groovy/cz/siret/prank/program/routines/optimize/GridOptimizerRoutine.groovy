package cz.siret.prank.program.routines.optimize

import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.IterativeParam
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

    static final int REGENERATE_PLOTS_EVERY_N_STEPS = 10

    List<IterativeParam> listParams
    List<String> gridVariablesNames

    List<TableToPlot> tablesToPlot

    GridOptimizerRoutine(String outdir, List<IterativeParam> listParams) {
        super(outdir)
        this.listParams = listParams
        this.gridVariablesNames = listParams*.name


    }

    private String prepareDirLabel(Step step) {

        String label = sanitizeFilename(step.label)

        if (label.length() > 200) {
            label = label.substring(0, 200)
        }

        label = String.format("%05d", steps.size()) + "_" + label

        return label
    }

    /**
     * Iterate through al steps running closure.
     * Step is a particular assignment of flexible variables, (e.g. "param1=val1 param2=val2")
     * @param closure takes outdir as param
     *
     * TODO: merge with code in Experiments, there is no point in separation with closure
     */
    void runGridOptimization(Closure<EvalResults> eval) {

        def timer = startTimer()

        processSteps(eval)

        logTime "param iteration finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        if (params.r_generate_plots) {
            makePlots()
        }

        try {
            if (params.ploop_delete_runs) {
                Futils.delete(runsDir)
            } else if (params.ploop_zip_runs) {
                Futils.zipAndDelete(runsDir)
            }
        } catch (Exception e) {
            log.error("failed to delete directory", e)
        }

        logTime "ploop routine finished in $timer.formatted"

    }

    private void runInfiniteIteration(Closure<EvalResults> eval) {

    }

    private void processSteps(Closure<EvalResults> eval) {

        IterativeParam first = listParams[0]

        // if first iteretive param is generative
        boolean generative = listParams.size() == 1 && !(first instanceof ListParam)

        if (generative) {

            Object val = first.nextValue
            while (val != null) {
                Step step = new Step().extendWith(first.name, val)
                steps.add(step)

                processStep(step, prepareDirLabel(step), eval)

                make1DOr2DTables(steps)
                if (steps.size() % REGENERATE_PLOTS_EVERY_N_STEPS == 0) {
                    if (params.r_generate_plots) {
                        makePlots()
                    } 
                }

                val = first.nextValue
            }

        } else { // all static

            steps = generateSteps(listParams)
            log.info "STEPS: " + steps.toListString().replace("Step","\nStep")

            for (Step step in steps) {
                processStep(step, prepareDirLabel(step), eval)

                make1DOr2DTables(steps)
            }
        }

    }

//===========================================================================================================//

    private void make1DOr2DTables(List<Step> steps) {
        if (listParams.size() == 1) {
            make1DTables(steps)
        } else if (listParams.size() == 2) {
            make2DTables(steps)
        }
    }

    private void make2DTables(List<Step> steps) {
        tablesToPlot = [] // clear
        steps[0].resultStats.each {
            make2DTable(it.key as String)
        }
    }

    private void make1DTables(List<Step> steps) {
        tablesToPlot = [] // clear
        steps[0].resultStats.each {
            make1DTableWithSorted(it.key as String)
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
    private make1DPlots() {
        GParsPool.withPool(numRThreads) {
            tablesToPlot.eachParallel { TableToPlot tp ->
                if (plotVariable(tp.label)) {
                    //new RPlotter(tp.plotDir).plot1DVariable(tp.tableFile, tp.label)
                    new RPlotter(tp.plotDir).plot1DVariableHorizontal(tp.tableFile, tp.label)
                }
            }
        }
    }

    @CompileDynamic
    private make2DPlots() {
        GParsPool.withPool(numRThreads) {
            tablesToPlot.eachParallel { TableToPlot tp ->
                if (plotVariable(tp.label)) {
                    String labelX = listParams[1].name
                    String labelY = listParams[0].name
                    new RPlotter(tp.plotDir).plotHeatMapTable(tp.tableFile, tp.label, labelX, labelY)
                }
            }
        }
    }

//    @Deprecated
//    private make1DPlotsOld() {
//        def plotter = new RPlotter(statsTableFile, plotsDir)
//        def vars = plotter.header.findAll { plotVariable(it) }.asList()
//        plotter.plot1DVariables(vars, numRThreads)
//    }

    private make2DTable(String statName) {
        IterativeParam paramX = listParams[0]
        IterativeParam paramY = listParams[1]

        Map<List, Double> valueMap = new HashMap()
        for (Step s : steps) {
            def key = [ s.params[0].value, s.params[1].value ]
            valueMap.put( key, s.resultStats.get(statName) )
        }

        StringBuilder sb = new StringBuilder()
        //sb << "# resName \n"
        sb << "${paramX.name}/${paramY.name}," + paramY.values.collect { quote(it) }.join(",") + "\n"  // header

        for (def varX : paramX.values) {
            def row = paramY.values.collect { varY -> valueMap.get([varX, varY]) }.collect { fmt it }.join(",")

            sb << "" + quote(varX) + "," + row + "\n"
        }

        String fname = "$tablesDir/${statName}.csv"
        tablesToPlot.add(new TableToPlot(statName, fname, plotsDir))
        Futils.writeFile fname, sb.toString()
    }


    /**
     * makes 2 tables: normal and sorted by stat values desc nulls last
     */
    private make1DTableWithSorted(String statName) {
        IterativeParam paramX = listParams[0]

        List<ParamStat> statValues = new ArrayList<>(steps.size())
        for (Step s : steps) {
            statValues.add new ParamStat(""+s.params[0].value, s.resultStats.get(statName))
        }

        String tablef = "$tablesDir/${statName}.csv"
        write1DTable(statName, paramX.name, statValues, tablef)
        tablesToPlot.add(new TableToPlot(statName, tablef, plotsDir))

        statValues.sort(ParamStat.ORDER)
        String sortedTablef = "${tablesDir}_sorted/${statName}.csv"
        write1DTable(statName, paramX.name, statValues, sortedTablef)
        tablesToPlot.add(new TableToPlot(statName, sortedTablef, "${plotsDir}_sorted"))
    }

    private write1DTable(String statName, String paramName, List<ParamStat> statValues, String fname) {
        StringBuilder sb = new StringBuilder()
        sb << "$paramName, $statName\n"  // header

        for (ParamStat row : statValues) {
            sb << quote(row.paramValue) + "," + fmt(row.statValue) + "\n"
        }

        Futils.writeFile fname, sb.toString()
    }

    private String quote(Object s) {
        if (s == null) return null
        return "\"$s\""
    }

    private List<Step> generateSteps(List<IterativeParam> iterativeParams) {

        if (Cutils.empty(iterativeParams)) {
            throw new PrankException("No iterative params were provided for grid optimization.")
        }

        genStepsRecur(new ArrayList<Step>(), new Step(), iterativeParams)
    }
    
    private List<Step> genStepsRecur(List<Step> steps, Step base, List<IterativeParam> iterativeParams) {
        if (iterativeParams.empty) {
            steps.add(base)
            return steps
        }

        IterativeParam rparam = iterativeParams.head()
        for (Object val : rparam.values) {
            Step deeperStep = base.extendWith(rparam.name, val)
            genStepsRecur(steps, deeperStep, iterativeParams.tail())
        }

        return steps
    }

//===========================================================================================================//

    static class TableToPlot {
        String label
        String tableFile
        String plotDir

        TableToPlot(String label, String tableFile, String plotDir) {
            this.label = label
            this.tableFile = tableFile
            this.plotDir = plotDir
        }
    }

    static class ParamStat {
        String paramValue
        Object statValue

        ParamStat(String paramValue, Object statValue) {
            this.paramValue = paramValue
            this.statValue = statValue
        }

        static Comparator<ParamStat> ORDER = Comparator.<ParamStat, Object>comparing({ ParamStat o -> o.statValue },
                Comparator.nullsFirst(Comparator.naturalOrder())
        ).reversed()
    }

}
