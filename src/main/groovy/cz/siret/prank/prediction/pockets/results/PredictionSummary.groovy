package cz.siret.prank.prediction.pockets.results

import cz.siret.prank.domain.Prediction
import cz.siret.prank.prediction.pockets.PrankPocket
import cz.siret.prank.utils.CSV
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.PerfUtils.formatDouble

/**
 * Summary of predicted pockets for one protein.
 */
@CompileStatic
class PredictionSummary {

    private Prediction prediction

    PredictionSummary(Prediction prediction) {
        this.prediction = prediction
    }

    CSV toCSV() {
        StringBuilder sb = new StringBuilder()

        sb << "name, rank, score, sas_points, surf_atoms, center_x, center_y, center_z, residue_ids, surf_atom_ids   " << '\n'

        for (pp in prediction.reorderedPockets) {

            PrankPocket p = (PrankPocket) pp

            String fmtScore = formatDouble(p.newScore)

            def x = formatDouble(p.centroid.x)
            def y = formatDouble(p.centroid.y)
            def z = formatDouble(p.centroid.z)

            def surfAtomIds = (p.surfaceAtoms*.PDBserial).toSorted().join(" ")

            Set resIds = new TreeSet(p.residues.collect { it.key.toString() })
            String strResIds = resIds.join(" ")

            sb << "$p.name,$p.newRank,$fmtScore,${p.sasPoints.count},$p.surfaceAtoms.count,$x,$y,$z,$strResIds,$surfAtomIds\n"
        }

        return new CSV(sb.toString())
    }

    String toTable() {
        return toCSV().tabulated(10,10,10,10,10)
    }

}
