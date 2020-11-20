package cz.siret.prank.prediction.pockets.results

import cz.siret.prank.domain.Prediction
import cz.siret.prank.prediction.pockets.PrankPocket
import cz.siret.prank.utils.csv.CSV
import cz.siret.prank.utils.csv.CsvRow
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.Formatter.*
import static cz.siret.prank.utils.csv.CsvRow.Justify.LEFT
import static cz.siret.prank.utils.csv.CsvRow.Justify.RIGHT

/**
 * Summary of predicted pockets for one protein.
 */
@CompileStatic
class PredictionSummary {

    private Prediction prediction

    PredictionSummary(Prediction prediction) {
        this.prediction = prediction
    }

    static String HEADER = new CsvRow() {{
        add LEFT,   9, "name"
        add RIGHT,  5, "rank"
        add RIGHT,  7, "score"
        add RIGHT, 11, "probability"
        add RIGHT, 10, "sas_points"
        add RIGHT, 10, "surf_atoms"
        add RIGHT, 10, "center_x"
        add RIGHT, 10, "center_y"
        add RIGHT, 10, "center_z"
        add LEFT,  10, "residue_ids"
        add LEFT,  10, "surf_atom_ids"
    }}.toString()

    CSV toCSV() {
        StringBuilder sb = new StringBuilder(8192)

        sb << HEADER << "\n"

        for (pp in prediction.reorderedPockets) {

            PrankPocket p = (PrankPocket) pp

            String score = formatScore(p.newScore)
            String proba = formatProbScore(p.auxInfo.probaTP)

            String x = formatCoord(p.centroid.x)
            String y = formatCoord(p.centroid.y)
            String z = formatCoord(p.centroid.z)

            String surfAtomIds = (p.surfaceAtoms*.PDBserial).toSorted().join(" ")

            Set resIds = new TreeSet(p.residues.collect { it.key.toString() }) // sorted
            String strResIds = resIds.join(" ")


            CsvRow row = new CsvRow()
            row.add LEFT,   9, p.name
            row.add RIGHT,  5, p.newRank.toString()
            row.add RIGHT,  7, score
            row.add RIGHT, 11, proba
            row.add RIGHT, 10, p.sasPoints.count.toString()
            row.add RIGHT, 10, p.surfaceAtoms.count.toString()
            row.add RIGHT, 10, x
            row.add RIGHT, 10, y
            row.add RIGHT, 10, z
            row.add strResIds
            row.add surfAtomIds


            sb << row.toString() << "\n"
        }

        return new CSV(sb.toString())
    }


    String toTable() {
        return toCSV().tabulated(10,10,10,10,10)
    }

}
