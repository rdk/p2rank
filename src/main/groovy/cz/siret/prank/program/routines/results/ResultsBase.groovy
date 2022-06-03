package cz.siret.prank.program.routines.results

import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.prediction.metrics.Curves
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 *
 */
@CompileStatic
class ResultsBase implements Parametrized, Writable {

    String logClassifierStats(String fileLabel, String classifierLabel, ClassifierStats cs, String outdir) {

        // main stats

        String stats_str    = cs.toCSV(" $classifierLabel ")
        writeFile "$outdir/${fileLabel}.csv", stats_str

        // additional stats: histograms, roc

        String dir = "$outdir/$fileLabel"
        mkdirs(dir)

        cs.histograms.allHistograms.each {
            writeFile "$dir/hist_${it.label}.csv", it.toCSV()
        }

        if (cs.collecting && params.stats_curves)
            writeFile "$dir/roc_curve.csv", Curves.roc(cs.predictions).toCSV()

        return stats_str
    }

}
