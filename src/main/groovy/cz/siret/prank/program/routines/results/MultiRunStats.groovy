package cz.siret.prank.program.routines.results

import cz.siret.prank.utils.StatSample
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.StatSample.newStatSample

/**
 * Stats table for multiple runs
 */
@CompileStatic
class MultiRunStats {

    List<String> names
    List<Map<String, Double>> statsForRuns

    int nRuns
    Map<String, Double> mean           = new HashMap<>()
    Map<String, Double> stddev          = new HashMap<>()
    Map<String, Double> relativeStddev  = new HashMap<>()

    MultiRunStats(List<String> names, List<Map<String, Double>> statsForRuns) {
        this.names = names
        this.statsForRuns = statsForRuns

        nRuns = statsForRuns.size()
        aggregate()
    }

    void aggregate() {
        for (String stat : names) {
            StatSample sample = newStatSample(statsForRuns.collect { it.get(stat) })

            mean.put(stat, sample.mean)
            stddev.put(stat, sample.stddev)
            relativeStddev.put(stat, sample.relativeStdev)
        }
    }

    String toCSV() {
        StringBuilder sb = new StringBuilder()

        sb << fs('FEATURE') << ', '
        sb << fs('MEAN') << ', '
        sb << fs('STDDEV') << ', '
        sb << fs('REL_STDDEV[%]') << ', '
        sb << (1..nRuns).collect{ fs "run_$it" }.join(', ') << '\n'

        for (String stat : names) {
            sb << fs(stat) << ', '
            sb << fmt(mean[stat]) << ', '
            sb << fmt(stddev[stat]) << ', '
            sb << fmt(relativeStddev[stat]) << ', '
            sb << statsForRuns.collect{ fmt(it[stat]) }.join(', ') << '\n'
        }

        sb.toString()
    }

    static String fmt(Object x) {
        if (x==null) return ""
        try {
            sprintf "%13.5f", x
        } catch (Exception e) {
            fs x.toString()
        }
    }
    
    static String fs(String s) {
        String.format('%1$-13s', s)
    }

}
