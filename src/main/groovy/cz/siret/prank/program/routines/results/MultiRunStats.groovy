package cz.siret.prank.program.routines.results

import cz.siret.prank.utils.StatSample

import static cz.siret.prank.utils.StatSample.newStatSample

/**
 * Stats table for multiple runs
 */
class MultiRunStats {

    List<String> names
    List<Map<String, Double>> statsForRuns

    int n
    Map<String, Double> mean           = new HashMap<>()
    Map<String, Double> stddev          = new HashMap<>()
    Map<String, Double> relativeStddev  = new HashMap<>()

    MultiRunStats(List<String> names, List<Map<String, Double>> statsForRuns) {
        this.names = names
        this.statsForRuns = statsForRuns

        n = statsForRuns.size()
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

        sb << 'FEATURE, MEAN, STDDEV, REL_STDDEV[%], ' + (1..n).collect{"run_$it"}.join(', ') + '\n'
        for (String stat : names) {
            sb << stat << ', '
            sb << fmt(mean[stat]) << ', '
            sb << fmt(stddev[stat]) << ', '
            sb << fmt(mean[stat]) << ', '
            sb << statsForRuns.collect{ fmt(it[stat]) }.join(', ') << '\n'
        }

        sb.toString()
    }

    static String fmt(Object x) {
        if (x==null) return ""
        sprintf "%12.5f", x
    }
    
//    static String fs(String s) {
//        String.format('%1$-12s', s)
//    }

}
