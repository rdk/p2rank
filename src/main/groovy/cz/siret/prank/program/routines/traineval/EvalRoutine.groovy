package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Futils
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

        long proteins = results.eval.proteinCount
        long ligands = results.eval.ligandCount
        long pockets = results.eval.pocketCount

        double top1 = results.origEval.calcDefaultCriteriumSuccessRate(0)
        double all = results.origEval.calcDefaultCriteriumSuccessRate(999)
        double rescored = results.eval.calcDefaultCriteriumSuccessRate(0)

        double orig_DCA4_0 = results.origEval.calcDefaultCriteriumSuccessRate(0)
        double orig_DCA4_2 = results.origEval.calcDefaultCriteriumSuccessRate(2)
        double DCA4_0 = results.eval.calcDefaultCriteriumSuccessRate(0)
        double DCA4_2 = results.eval.calcDefaultCriteriumSuccessRate(2)

        double diff = rescored - top1
        double possible = all - top1
        double pcPossible = diff / possible

        double P = results.classifierStats.metrics.p
        double R = results.classifierStats.metrics.r
        double FM = results.classifierStats.metrics.f1
        double MCC = results.classifierStats.metrics.MCC

        double ligSize = results.eval.avgLigandAtoms
        double pocketVol = results.eval.avgPocketVolume
        double pocketSurf = results.eval.avgPocketSurfAtoms

        String dir = Futils.shortName(outdir)

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

        // collecting results (runs.csv in .. dir)
        //File collectedf = new File("$outdir/../runs.csv")
        //if (!collectedf.exists()) {
        //    collectedf << mainRes.readLines()[0] + "\n" // add header
        //}
        //collectedf << mainRes.readLines()[1] + "\n"
    }

    String getEvalRoutineOutdir() {
        outdir
    }


    /**
     * Create EvalRoutine for residues or pockets depending on residueMode
     */
    static EvalRoutine create(boolean residueMode, Dataset dataset, Model model, String outdir) {
        dataset.forTraining(false)
        if (residueMode) {
            return new EvalResiduesRoutine(dataset, model, outdir)
        } else {
            return new EvalPocketsRoutine(dataset, model, outdir)
        }
    }

}
