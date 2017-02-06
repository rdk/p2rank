package cz.siret.prank.program.routines

import groovy.util.logging.Slf4j
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.futils

@Slf4j
class SeedLoop extends CompositeRoutine {

    CompositeRoutine routine  // routine to iterate on

    SeedLoop(CompositeRoutine routine, String outdir) {
        this.routine = routine
        this.outdir = outdir
    }

    @Override
    Results execute() {
        def timer = ATimer.start()

        Results results = new Results(0)

        int origSeed = params.seed
        int n = params.loop
        for (int seedi in 1..n) {
            write "random seed iteration: $seedi/$n"

            String label = "seed.${params.seed}"
            routine.outdir = "$outdir/$label"

            results.addAll(routine.execute())

            params.seed += 1
        }

        results.logAndStore(outdir, params.classifier)
        if (routine instanceof CrossValidation) {
            CrossValidation cv = (CrossValidation) routine
            logMainResults(cv.dataset.label, "crossvalidation", results)
        } else {
            logMainResults("--", "evaluation", results)
        }
        params.seed = origSeed // set seed back for other experiments

        logTime "random seed iteration finished in $timer.formatted"
        write "results saved to directory [${futils.absPath(outdir)}]"


        return results
    }

}
