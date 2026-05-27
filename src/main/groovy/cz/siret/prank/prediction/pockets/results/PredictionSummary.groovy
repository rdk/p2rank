package cz.siret.prank.prediction.pockets.results

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.utils.csv.CSV
import cz.siret.prank.utils.csv.CsvRow
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.Formatter.*

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
        add "name"
        add "rank"
        add "score"
        add "probability"
        add "sas_points"
        add "surf_atoms"
        add "center_x"
        add "center_y"
        add "center_z"
        add "residue_ids"
        add "surf_atom_ids"
    }}.toString()

    CSV toCSV() {
        StringBuilder sb = new StringBuilder(8192)

        sb << HEADER << "\n"

        for (Pocket p : prediction.outputPockets) {

            String score = formatScore(p.newScore)
            String proba = formatProbScore(p.auxInfo.probaTP)

            String x = formatCoord(p.centroid.x)
            String y = formatCoord(p.centroid.y)
            String z = formatCoord(p.centroid.z)

            String surfAtomIds = (p.surfaceAtoms*.PDBserial).toSorted().join(" ")

            Set resIds = new TreeSet(p.residues.collect { it.key.toString() }) // sorted
            String strResIds = resIds.join(" ")


            CsvRow row = new CsvRow()
            row.add p.name
            row.add p.newRank.toString()
            row.add score
            row.add proba
            row.add p.sasPoints.count.toString()
            row.add p.surfaceAtoms.count.toString()
            row.add x
            row.add y
            row.add z
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
