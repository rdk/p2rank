package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Point
import cz.siret.prank.utils.PDBUtils
import cz.siret.prank.utils.StrUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Structure

/**
 * Loader for LISE pocket predictions (*_top10.pdb file)
 */
@Slf4j
@CompileStatic
class LiseLoader extends PredictionLoader {

    /**
     * cutoff for determining surface atoms around centroid
     */
    double SURFACE_ATOMS_CUTOFF = 8

    int POCKET_LIMIT = 12


    /**
     *
     * @return
     */
    @Override
    Prediction loadPrediction(String predictionOutputFile, Protein liganatedProtein) {

        List<LisePocket> pockets = loadSitehoundPockets(predictionOutputFile, liganatedProtein)

        return new Prediction(liganatedProtein, pockets)
    }

    /**
                                     x         y       z           score
     HETATM    0  R   ClA     0      88.000  70.000   4.000 100.00 15.29
     HETATM    0  R   ClB     1      93.000  76.000  19.000 100.00 14.92
     */
    List<LisePocket> loadSitehoundPockets(String predictionOutputFile, Protein liganatedProtein) {

        List<LisePocket> res = new ArrayList<>()

//        Structure struct = PDBUtils.loadFromFile(predictionOutputFile)

        for (String line : new File(predictionOutputFile).text.trim().readLines()) {

            List<String> cols = StrUtils.splitOnWhitespace(line)

            LisePocket poc = new LisePocket()

            poc.rank = cols[4].toInteger() + 1
            poc.name =  cols[3]
            poc.score = cols[9].toDouble()

            double x = cols[5].toDouble()
            double y = cols[6].toDouble()
            double z = cols[7].toDouble()

            poc.centroid = new Point(x, y, z)
            if (liganatedProtein!=null) {
                poc.surfaceAtoms = liganatedProtein.exposedAtoms.cutoffAroundAtom(poc.centroid, SURFACE_ATOMS_CUTOFF)
            }

            res.add(poc)
        }

        return res
    }

    static class LisePocket extends Pocket {

        double score

    }

}
