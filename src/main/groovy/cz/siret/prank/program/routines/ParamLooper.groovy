package cz.siret.prank.program.routines

import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import cz.siret.prank.program.ThreadPoolFactory
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.params.RangeParam
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.futils
import cz.siret.prank.utils.plotter.RPlotter

/**
 * routine for grid optimization. Loops through values of one or more RangeParam and produces resulting statistics and plots.
 */
@Slf4j
class ParamLooper extends Routine {

    String outdir
    List<RangeParam> rparams

    List<Step> steps

    String paramsTableFile
    String plotsDir
    String tablesDir

    Map tables2D = new LinkedHashMap()

    ParamLooper(String outdir, List<RangeParam> rparams) {
        this.rparams = rparams
        this.outdir = outdir
        plotsDir = "$outdir/plots"
    }

    /**
     *
     * @param routine takes label as param (e.g. "prram1.val1.param2.val2")
     */
    public void iterateParams(Closure<CompositeRoutine.Results> closure) {
        def timer = ATimer.start()

        steps = generateSteps()
        log.info "STEPS: " + steps.toListString().replace("Step","\nStep")

        paramsTableFile = "$outdir/param_stats.csv"
        PrintWriter table = futils.overwrite paramsTableFile

        boolean doheader = true
        for (Step step in steps) {
            step.applyToParams(params)

            def tim = ATimer.start()
            CompositeRoutine.Results res = closure.call(step.label)

            step.results.putAll( res.stats )
            step.results.TIME_MINUTES = tim.minutes

            if (doheader) {
                table << step.header + "\n";  doheader = false
            }
            table << step.toCSV() + "\n"; table.flush()

            if (paramsCount==2) {
                make2DTables(step)
            }
        }
        table.close()

        logTime "param iteration finished in $timer.formatted"
        write "results saved to directory [${futils.absPath(outdir)}]"

        makePlots()
    }

    private void make2DTables(Step step) {
        tablesDir = "$outdir/tables"
        tables2D = [:]
        step.results.each {
            make2DTable(it.key)
        }
    }

    int getParamsCount() {
        rparams.size()
    }

    private makePlots() {
        write "generating R plots..."
        futils.mkdirs(plotsDir)
        if (paramsCount==1) {
            make1DPlots()
        } else if (paramsCount==2) {
            make2DPlots()
        }
    }

    private make2DPlots() {
        GParsPool.withExistingPool(ThreadPoolFactory.pool) {
            tables2D.entrySet().eachParallel {
                String fname = futils.absSafePath(it.value)
                String label = it.key
                String xlab = rparams[1].name
                String ylab = rparams[0].name
                new RPlotter(plotsDir).plotHeatMapTable(fname, label, xlab, ylab)
            }
        }
    }

    private make1DPlots() {
        new RPlotter( paramsTableFile, plotsDir).plot1DAll()
    }

    private make2DTable(String statName) {
        RangeParam pa = rparams[0]
        RangeParam pb = rparams[1]

        Map map =[:]
        for (Step s : steps) {
            def key = [ s.params[0].value, s.params[1].value ]
            map.put( key, s.results."$statName" )
        }

        StringBuilder sb = new StringBuilder()
        sb << "# resName \n"
        sb << "${pa.name}/${pb.name}," + pb.values.collect { it }.join(",") + "\n"

        for (def va : pa.values) {
            def row = pb.values.collect { vb -> map.get([va,vb]) }.collect { fmt it }.join(",")

            sb << va + "," + row + "\n"
        }

        def fname = "$tablesDir/${statName}.csv"
        tables2D.put(statName, fname)
        futils.overwrite fname, sb.toString()
    }

    private List<Step> generateSteps() {
        genStepsRecur(new ArrayList<Step>(), new Step(), rparams)
    }
    private List<Step> genStepsRecur(List<Step> steps, Step base, List<RangeParam> rparams) {
        if (rparams.empty) {
            steps.add(base); return
        }

        RangeParam rparam = rparams.head()
        for (Object val : rparam.values) {
            Step deeperStep = base.extendWith(rparam.name, val)
            genStepsRecur(steps, deeperStep, rparams.tail())
        }

        return steps
    }

    static String fmt(Double x) {
        if (x==null) return ""
        sprintf "%8.4f", x
    }

//===========================================================================================================//

    @TupleConstructor
    private static class Step {

        List<ParamVal> params = new ArrayList<>()
        Map results = new LinkedHashMap()

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
            (params*.name).join(',') + ',' + results.keySet().join(',')
        }

        String toCSV() {
            (params*.value).join(',') + ',' + results.values().collect{ fmt(it) }.join(',')
        }

        public String toString() {
            return "Step{${params.toListString()}, ${results.toMapString()}}";
        }
    }

    @TupleConstructor
    private static class ParamVal {
        String name
        Object value

        public String toString() {
            name + ":" + value
        }
    }

}
