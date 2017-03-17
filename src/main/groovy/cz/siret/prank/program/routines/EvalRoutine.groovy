package cz.siret.prank.program.routines

import cz.siret.prank.program.routines.results.EvalResults
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Formatter.fmt
import static cz.siret.prank.utils.Formatter.pc
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Routine that produces EvalResults.
 * Can be composed of subroutines and produce joined EvalResults.
 */
@Slf4j
@CompileStatic
abstract class EvalRoutine extends Routine {

    EvalRoutine(String outdir) {
        super(outdir)
    }

    abstract EvalResults execute()

    String toMainResultsCsv(String label, String model, EvalResults results) {

        int proteins = results.originalEval.proteinCount
        int ligands = results.originalEval.ligandCount
        int pockets = results.rescoredEval.pocketCount

        double top1 = results.originalEval.calcDefaultCriteriumSuccessRate(0)
        double all = results.rescoredEval.calcDefaultCriteriumSuccessRate(999)
        double rescored = results.rescoredEval.calcDefaultCriteriumSuccessRate(0)

        double orig_DCA4_0 = results.originalEval.calcDefaultCriteriumSuccessRate(0)
        double orig_DCA4_2 = results.originalEval.calcDefaultCriteriumSuccessRate(2)
        double DCA4_0 = results.rescoredEval.calcDefaultCriteriumSuccessRate(0)
        double DCA4_2 = results.rescoredEval.calcDefaultCriteriumSuccessRate(2)

        double diff = rescored - top1
        double possible = all - top1
        double pcPossible = diff / possible

        double P = results.classifierStats.stats.p
        double R = results.classifierStats.stats.r
        double FM = results.classifierStats.stats.f1
        double MCC = results.classifierStats.stats.MCC

        double ligSize = results.rescoredEval.avgLigandAtoms
        double pocketVol = results.rescoredEval.avgPocketVolume
        double pocketSurf = results.rescoredEval.avgPocketSurfAtoms

        String dir = new File(outdir).name

        String s = "dir,dataset,model,#proteins,#ligands,#pockets,orig_DCA4_0,orig_DCA4_2,DCA4_0,DCA4_2,top1,all,rescored,diff,%possible,possible,P,R,FM,MCC,avgLigSize,avgPocketVol,avgPocketSurfAtoms\n"
        s += "$dir,$label,$model,$proteins,$ligands,$pockets," +
                "${pc(orig_DCA4_0)},${pc(orig_DCA4_2)},${pc(DCA4_0)},${pc(DCA4_2)}," +
                "${pc(top1)},${pc(all)},${pc(rescored)},${pc(diff)},${pc(pcPossible)},${pc(possible)}," +
                "${fmt(P)},${fmt(R)},${fmt(FM)},${fmt(MCC)},${fmt(ligSize)},${fmt(pocketVol)},${fmt(pocketSurf)}\n"

        return s
    }

    void logSummaryResults(String label, String model, EvalResults results) {
        String mainRes = toMainResultsCsv(label, model, results)
        writeFile "$outdir/summary.csv", mainRes

        // collecting results
        File collectedf = new File("$outdir/../runs.csv")
        if (!collectedf.exists()) {
            collectedf << mainRes.readLines()[0] + "\n" // add header
        }
        collectedf << mainRes.readLines()[1] + "\n"
    }



}
