package cz.siret.prank.prediction.pockets.results

import cz.siret.prank.domain.Prediction
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.csv.CSV
import groovy.transform.CompileStatic

/**
 * Summary of rescoring pockets on one protein.
 */
@CompileStatic
class RescoringSummary {

    private Prediction prediction

    RescoringSummary(Prediction prediction) {
        this.prediction = prediction
    }

    private String changeVisualAid(int change) {
        int MAX = 16
        int n = prediction.outputPockets.size()

        int absc = change.abs()

        String changeVis = ""
        if (change!=0) {
            int len = Math.round((float) ((absc/(float)n) * MAX) )
            if (absc < MAX/2) {
                len = absc
            }
            def ch = (change>0) ? '+' : '-'
            changeVis = ch*len
        }
        return changeVis
    }

    CSV toCSV() {
        StringBuilder sb = new StringBuilder()

        sb << "name,score,rank,old_rank,change,change_visual_aid" << '\n'

        for (p in prediction.outputPockets) {
            int change = p.rank - p.newRank

            String fmtScore = PerfUtils.formatDouble(p.newScore)
            String changeAid = changeVisualAid(change)

            sb << "$p.name,$fmtScore,$p.newRank,$p.rank,$change,$changeAid\n"
        }

        return new CSV(sb.toString())
    }

    String toTable() {
        return toCSV().tabulated(15,15,8,10,8)
    }

}
