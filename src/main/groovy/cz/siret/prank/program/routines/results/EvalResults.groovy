package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.score.criteria.DCA
import cz.siret.prank.score.criteria.DCC
import cz.siret.prank.score.criteria.DPA
import cz.siret.prank.score.criteria.DSA
import cz.siret.prank.score.criteria.IdentificationCriterium
import cz.siret.prank.score.results.ClassifierStats
import cz.siret.prank.score.results.Evaluation
import cz.siret.prank.utils.CSV
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.Writable
import cz.siret.prank.utils.stat.Histogram

import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.Futils.writeFile

/**
 * results for eval-rescore, traineval and ploop routines
 */
class EvalResults implements Parametrized, Writable  {

    /**
     * number of Eval runs this result represents
     */
    int runs = 0

    Evaluation originalEval
    Evaluation rescoredEval
    ClassifierStats classifierStats
    ClassifierStats classifierTrainStats // classifier stats on train data

    Dataset.Result datasetResult

    Long trainTime
    Long evalTime

    int train_positives = 0
    int train_negatives = 0

    List<Double> featureImportances

    EvalResults(int runs) {
        this.runs = runs
        originalEval = new Evaluation( getDefaultEvalCrtieria() )
        rescoredEval = new Evaluation( getDefaultEvalCrtieria() )
        classifierStats = new ClassifierStats()
        if (params.classifier_train_stats) {
            classifierTrainStats = new ClassifierStats()
        }
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

    /**
     * get list of evaluation criteria used during eval routines
     */
    static List<IdentificationCriterium> getDefaultEvalCrtieria() {
        ((1..15).collect { new DCA(it) }) + ((1..10).collect { new DCC(it) }) + ((1..6).collect { new DPA(it) }) + ((1..6).collect { new DSA(it) })
    }

    void addAll(EvalResults other) {
        originalEval.addAll(other.originalEval)
        rescoredEval.addAll(other.rescoredEval)
        classifierStats.addAll(other.classifierStats)
        if (classifierTrainStats!=null && other.classifierTrainStats!=null) {
            classifierTrainStats.addAll(other.classifierTrainStats)
        }

        // set only once to because of varoius caching
        if (trainTime==null) trainTime = other.trainTime
        if (evalTime==null) evalTime = other.evalTime

        train_negatives += other.train_negatives
        train_positives += other.train_positives

        featureImportances = addVectors(featureImportances, other.featureImportances)

        runs += other.runs
    }

    int getAvgTrainVectors() {
        avgTrainPositives + avgTrainNegatives
    }

    int getAvgTrainPositives() {
        train_positives / runs
    }

    int getAvgTrainNegatives() {
        train_negatives / runs
    }

    double getTrainPositivesRatio() {
        if (train_positives + train_negatives==0) return 0

        train_positives / (train_positives + train_negatives)
    }

    double getTrainRatio() {
        if (train_negatives==0) return 1

        train_positives / train_negatives
    }

    Map getStats() {
        Map m = rescoredEval.stats

        m.PROTEINS         /= runs
        m.POCKETS          /= runs
        m.LIGANDS          /= runs
        m.LIGANDS_IGNORED  /= runs
        m.LIGANDS_SMALL    /= runs
        m.LIGANDS_DISTANT  /= runs

        // m.LIG_COUNT = originalEval.ligandCount / runs
        // m.LIG_COUNT_IGNORED = originalEval.ignoredLigandCount / runs
        // m.LIG_COUNT_SMALL = originalEval.smallLigandCount / runs
        // m.LIG_COUNT_DISTANT = originalEval.distantLigandCount / runs

        //===========================================================================================================//

        m.TIME_TRAIN = trainTime
        m.TIME_EVAL = evalTime

        m.TRAIN_VECTORS = avgTrainVectors
        m.TRAIN_POSITIVES = avgTrainPositives
        m.TRAIN_NEGATIVES = avgTrainNegatives
        m.TRAIN_RATIO = trainRatio
        m.TRAIN_POS_RATIO = trainPositivesRatio

        m.putAll classifierStats.statsMap
        if (params.classifier_train_stats && classifierTrainStats!=null) {
            m.putAll classifierTrainStats.statsMap.collectEntries { key, value -> ["train_" + key, value] }
        }

        if (params.feature_importances && featureImportances!=null) {
            featureImportances = featureImportances.collect { it/runs }.toList()
            getNamedImportances().each {
                m.put "_FI_"+it.name, it.importance
            }
        }

        return m
    }

    String getMiscStatsCSV() {
        stats.collect { "$it.key, ${fmt(it.value)}" }.join("\n")
    }



    static void logClassifierStats(ClassifierStats cs, String outdir) {
        String dir = "$outdir/classifier"
        mkdirs(dir)

        cs.histograms.properties.findAll { it.value instanceof Histogram }.each {
            String label = it.key
            Histogram hist = (Histogram) it.value

            writeFile "$dir/hist_${label}.csv", hist.toCSV()
        }
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

        String succ_rates          = originalEval.toSuccRatesCSV(tolerances)
        String succ_rates_rescored = rescoredEval.toSuccRatesCSV(tolerances)  // P2RANK predictions are in rescoredEval
        String succ_rates_diff     = rescoredEval.diffSuccRatesCSV(tolerances, originalEval)
        String classifier_stats    = classifierStats.toCSV(" $classifierName ")
        String stats               = getMiscStatsCSV()

        writeFile "$outdir/success_rates_original.csv", succ_rates
        writeFile "$outdir/success_rates.csv", succ_rates_rescored
        writeFile "$outdir/success_rates_diff.csv", succ_rates_diff
        writeFile "$outdir/classifier.csv", classifier_stats
        writeFile "$outdir/stats.csv", stats

        logClassifierStats(classifierStats, outdir)

        if (logIndividualCases) {
            originalEval.sort()
            rescoredEval.sort()

            String casedir = "$outdir/cases"
            mkdirs(casedir)
            writeFile "$casedir/proteins.csv", originalEval.toProteinsCSV()
            writeFile "$casedir/ligands.csv", rescoredEval.toLigandsCSV()
            writeFile "$casedir/pockets.csv", rescoredEval.toPocketsCSV()
            writeFile "$casedir/ranks.csv", originalEval.toRanksCSV()
            writeFile "$casedir/ranks_rescored.csv", rescoredEval.toRanksCSV()
        }

        if (params.feature_importances && featureImportances!=null) {
            List<FeatureImportance> namedImportances = getNamedImportances()
            namedImportances.sort { -it.importance } // descending
            String sortedCsv = namedImportances.collect { it.name + ", " + PerfUtils.formatDouble(it.importance) }.join("\n") + "\n"
            writeFile("$outdir/feature_importances_sorted.csv", sortedCsv)
        }

        log.info "\n" + CSV.tabulate(classifier_stats) + "\n\n"
        log.info "\nSucess Rates - Original:\n" + CSV.tabulate(succ_rates) + "\n"
        write    "\nSucess Rates:\n" + CSV.tabulate(succ_rates_rescored) + "\n"
        log.info "\nSucess Rates - Diff:\n" + CSV.tabulate(succ_rates_diff) + "\n\n"
    }

    List<FeatureImportance> getNamedImportances() {
        if (featureImportances==null) return null

        List<String> names = FeatureExtractor.createFactory().vectorHeader
        List<FeatureImportance> namedImportances = new ArrayList<>()

        [names, featureImportances].transpose().each {
            namedImportances.add new FeatureImportance((String)it[0], (Double)it[1])
        }
        return namedImportances
    }

    static class FeatureImportance {
        String name
        double importance

        FeatureImportance(String name, double importance) {
            this.name = name
            this.importance = importance
        }
    }

}