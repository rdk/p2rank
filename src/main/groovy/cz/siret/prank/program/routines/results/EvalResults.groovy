package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.Dataset
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.csv.CSV
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.MathUtils.stddev

/**
 * results for eval-rescore, traineval and ploop routines
 */
@Slf4j
@CompileStatic
class EvalResults extends ResultsBase {

    /**
     * number of Eval runs this result represents
     */
    int runs = 0

    Evaluation eval
    Evaluation origEval                  // stores original results by other prediction method when rescoring
    ClassifierStats classifierStats
    ClassifierStats classifierTrainStats // classifier stats on train data

    ClassifierStats residuePredictionStats

    Dataset.Result datasetResult

    /** total time in ms spent by model training */
    long totalTrainingTime = 0
    /** time of first evaluation, may be longer than subsequent ones in seedloop due to caching */
    Long firstEvalTime = null

    long train_positives = 0
    long train_negatives = 0

    List<Double> featureImportances
    List<EvalResults> subResults = new ArrayList<>()

    Map<String, Double> additionalStats = new HashMap<>()

    boolean mode_residues = params.predict_residues
    boolean mode_pockets = !mode_residues
    boolean rescoring = !params.predictions  // in mode_pockets: new predictions vs. rescoring

    EvalResults(int runs) {
        this.runs = runs
        eval = new Evaluation()
        origEval = new Evaluation()
        classifierStats = new ClassifierStats()
        if (params.classifier_train_stats) {
            classifierTrainStats = new ClassifierStats()
        }

        residuePredictionStats = new ClassifierStats()
    }

    private static List<Double> repeat(Double value, int times) {
        List<Double> res = new ArrayList<>(times)
        for (int i=0; i!=times; i++)
            res.add(value)
        return res
    }

    private static List<Double> addVectors(List<Double> va, List<Double> vb) {
        if (va==null && vb==null) return null

        if (va==null) va = repeat(0d, vb.size())
        if (vb==null) vb = repeat(0d, va.size())

        List res = new ArrayList(va.size())
        for (int i=0; i!=va.size(); i++) {
            res.add ((double)va[i] + (double)vb[i])
        }

        return res
    }

    void addSubResults(EvalResults results) {
        subResults.add(results)

        eval.addAll(results.eval)
        origEval.addAll(results.origEval)
        classifierStats.addAll(results.classifierStats)
        if (classifierTrainStats!=null && results.classifierTrainStats!=null) {
            classifierTrainStats.addAll(results.classifierTrainStats)
        }

        residuePredictionStats.addAll(results.residuePredictionStats)

        totalTrainingTime += results.totalTrainingTime
        if (firstEvalTime==null) firstEvalTime = results.firstEvalTime  // set only once for first run because of varoius caching mechanisms

        train_negatives += results.train_negatives
        train_positives += results.train_positives

        featureImportances = addVectors(featureImportances, results.featureImportances)

        runs += results.runs
    }

    int getAvgTrainVectors() {
        avgTrainPositives + avgTrainNegatives
    }

    int getAvgTrainPositives() {
        (double)train_positives / runs
    }

    int getAvgTrainNegatives() {
        (double)train_negatives / runs
    }

    double getTrainPositivesRatio() {
        if (train_positives + train_negatives==0) return 0

        (double)train_positives / (train_positives + train_negatives)
    }

    double getTrainRatio() {
        if (train_negatives==0) return 1

        (double)train_positives / train_negatives
    }

    long getAvgTrainingTime() {
        Math.round((double)totalTrainingTime / runs)
    }

    double getAvgTrainingTimeMinutes() {
        (double)(avgTrainingTime ?: 0d) / 60000d
    }

    double getEvalTimeMinutes() {
        (double)(firstEvalTime ?: 0d) / 60000d
    }

    Map<String, Double> getStats() {
        Map<String, Double> m = new TreeMap<>()

        if (mode_pockets) {
            m.putAll(eval.stats)
            m.PROTEINS         = (double)m.PROTEINS         / runs
            m.POCKETS          = (double)m.POCKETS          / runs
            m.LIGANDS          = (double)m.LIGANDS          / runs
            m.LIGANDS_IGNORED  = (double)m.LIGANDS_IGNORED  / runs
            m.LIGANDS_SMALL    = (double)m.LIGANDS_SMALL    / runs
            m.LIGANDS_DISTANT  = (double)m.LIGANDS_DISTANT  / runs
        }

        m.TIME_TRAIN_M = avgTrainingTimeMinutes
        m.TIME_EVAL_M = evalTimeMinutes
        m.TIME_M = avgTrainingTimeMinutes + evalTimeMinutes

        m.TRAIN_VECTORS = avgTrainVectors
        m.TRAIN_POSITIVES = avgTrainPositives
        m.TRAIN_NEGATIVES = avgTrainNegatives
        m.TRAIN_RATIO = trainRatio
        m.TRAIN_POS_RATIO = trainPositivesRatio

        if (mode_pockets) {
            m.putAll classifierStats.getMetricsMap("point_")
            if (params.classifier_train_stats && classifierTrainStats!=null) {
                m.putAll classifierTrainStats.getMetricsMap("train_point_")
            }
        }

        if (mode_residues) {
            m.putAll residuePredictionStats.getMetricsMap("residue_")
            m.putAll classifierStats.getMetricsMap("point_")
        }

        if (params.feature_importances && featureImportances!=null) {
            featureImportances = featureImportances.collect { (double)it/runs }.<Double>toList()
            FeatureImportances.from(featureImportances).items.each {
                m.put "_FI_"+it.name, it.importance
            }
        }

        m.putAll(additionalStats)

        return m
    }

    MultiRunStats getMultiStats() {
        assert !subResults.isEmpty()

        List<Map<String, Double>> subStats = subResults.collect { it.stats }.toList()
        List<String> statNames = subStats[0].keySet().toList()

        new MultiRunStats(statNames, subStats)
    }

    /**
     * Calculates sample standard deviation for all stats.
     * Only works for composite results (those that have subResults).
     */
    Map<String, Double> getStatsStddev() {
        assert !subResults.isEmpty()

        List<Map<String, Double>> subStats = subResults.collect { it.stats }.toList()

        Map res = new HashMap()
        for (String stat : subStats.head().keySet()) {
            double val = stddev subStats.collect { it.get(stat) }
            res.put(stat, val)
        }
        res
    }

    String statsCSV(Map stats) {
        stats.collect { "$it.key, ${Formatter.fmt(it.value)}" }.join("\n")
    }

    /**
     *
     * @param outdir
     * @param classifierName
     * @param summaryStats  in summary stats (like over multiple seed runs) we dont want all pocket details multiple times
     */
    void logAndStore(String outdir, String classifierName, Boolean logIndividualCases = params.log_cases) {

        mkdirs(outdir)

        writeFile "$outdir/stats.csv", statsCSV(getStats())
        // multiple runs
        if (subResults.size() > 1) {
            writeFile "$outdir/stats_stddev.csv", statsCSV(getStatsStddev()) // TODo remove
            writeFile "$outdir/stats_runs.csv", multiStats.toCSV()
        }


        write "\n"
        def pst = logClassifierStats("point_classification", classifierName,  classifierStats, outdir)
        write CSV.tabulate(pst)
        if (mode_residues) {
            def rst = logClassifierStats("residue_classification", "Residue classification",  residuePredictionStats, outdir)
            write  CSV.tabulate(rst)
        }
        write "\n"


        if (mode_pockets) {
            List<Integer> tolerances = params.eval_tolerances

            String succ_rates = eval.toSuccRatesCSV(tolerances)  // P2RANK predictions are in eval
            String succ_rates_original = null                    // predictions of other method in origEval (in rescore mode)
            String succ_rates_diff     = null
            if (rescoring) {
                succ_rates_original = origEval.toSuccRatesCSV(tolerances)
                succ_rates_diff = eval.diffSuccRatesCSV(tolerances, origEval)
            }

            writeFile "$outdir/success_rates.csv", succ_rates
            if (rescoring) {
                writeFile "$outdir/success_rates_original.csv", succ_rates_original
                writeFile "$outdir/success_rates_diff.csv", succ_rates_diff
            }

            if (logIndividualCases) {
                origEval.sort()
                eval.sort()

                String casedir = "$outdir/cases"
                mkdirs(casedir)
                writeFile "$casedir/proteins.csv", eval.toProteinsCSV()
                writeFile "$casedir/ligands.csv", eval.toLigandsCSV()
                writeFile "$casedir/pockets.csv", eval.toPocketsCSV()
                writeFile "$casedir/ranks.csv", eval.toRanksCSV()
                if (rescoring) {
                    writeFile "$casedir/ranks_original.csv", origEval.toRanksCSV()
                }
            }

            if (rescoring) {
                log.info "\nSuccess Rates - Original:\n" + CSV.tabulate(succ_rates_original) + "\n"
            }
            write "\nSuccess Rates:\n" + CSV.tabulate(succ_rates) + "\n"
            if (rescoring) {
                log.info "\nSuccess Rates - Diff:\n" + CSV.tabulate(succ_rates_diff) + "\n\n"
            }
        }

        if (params.feature_importances && featureImportances!=null) {
            writeFile"$outdir/feature_importances_sorted.csv", FeatureImportances.from(featureImportances).sorted().toCsv()
        }

    }

}