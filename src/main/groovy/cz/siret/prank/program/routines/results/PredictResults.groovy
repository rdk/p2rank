package cz.siret.prank.program.routines.results

import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.utils.csv.CSV
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Formatter.fmt
import static cz.siret.prank.utils.Formatter.formatPercent
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * results for eval-predict routine
 */
@Slf4j
@CompileStatic
class PredictResults extends ResultsBase {

    Evaluation evaluation
    ClassifierStats classStats

    PredictResults() {
        evaluation = new Evaluation()
        classStats = new ClassifierStats()
    }

    Map getStats() {
        Map m = evaluation.stats
        m.putAll( classStats.getMetricsMap("point_") )

        return m
    }

    String getMiscStatsCSV() {
        stats.collect { "$it.key, ${fmt it.value}" }.join("\n")
    }

    /**
     *
     * @param outdir
     * @param classifierName
     * @param summaryStats  in summary stats (like over multiple seed runs) we dont want all pocket details multiple times
     */
    void logAndStore(String outdir, String classifierName, Boolean logIndividualCases=null) {

        if (logIndividualCases==null) {
            logIndividualCases = params.log_cases
        }

        mkdirs(outdir)

        List<Integer> tolerances = params.eval_tolerances

        String succ_rates          = evaluation.toSuccRatesCSV(tolerances)
        String stats             = getMiscStatsCSV()

        writeFile "$outdir/success_rates.csv", succ_rates
        writeFile "$outdir/stats.csv", stats
        def classifier_stats = logClassifierStats("point_classification", classifierName,  classStats, outdir)

        if (logIndividualCases) {
            evaluation.sort()

            String casedir = "$outdir/cases"
            mkdirs(casedir)

            writeFile "$casedir/proteins.csv", evaluation.toProteinsCSV()
            writeFile "$casedir/ligands.csv", evaluation.toLigandsCSV()
            writeFile "$casedir/pockets.csv", evaluation.toPocketsCSV()
            writeFile "$casedir/ranks.csv", evaluation.toRanksCSV()
        }

        log.info "\n" + CSV.tabulate(classifier_stats) + "\n\n"

        write "\nSuccess Rates:\n" + CSV.tabulate(succ_rates) + "\n"
    }

//===========================================================================================================//

    String toMainResultsCsv(String outdir, String label) {

        def m = new LinkedHashMap()

        m.dir = new File(outdir).name
        m.label = label

        m.proteins = evaluation.proteinCount
        m.ligands =  evaluation.ligandCount
        m.pockets =  evaluation.pocketCount

        m.DCA_4_0 = formatPercent evaluation.calcDefaultCriteriumSuccessRate(0)
        m.DCA_4_2 = formatPercent evaluation.calcDefaultCriteriumSuccessRate(2)

        m.P =   fmt classStats.metrics.p
        m.R =   fmt classStats.metrics.r
        m.F1 =  fmt classStats.metrics.f1
        m.MCC = fmt classStats.metrics.MCC

        m.ligSize =    fmt evaluation.avgLigandAtoms
        m.pocketVol =  fmt evaluation.avgPocketVolume
        m.pocketSurf = fmt evaluation.avgPocketSurfAtoms


        return m.keySet().join(",") + "\n" + m.values().join(",")
    }

    void logMainResults(String outdir, String label) {
        String mainRes = toMainResultsCsv(outdir, label)
        writeFile "$outdir/summary.csv", mainRes

        // collecting results
        File collectedf = new File("$outdir/../runs_pred.csv")
        if (!collectedf.exists()) {
            collectedf << mainRes.readLines()[0] + "\n" // add header
        }
        collectedf << mainRes.readLines()[1] + "\n"
    }

}