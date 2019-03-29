package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Point
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

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

        return new Prediction(liganatedProtein, loadPockets(predictionOutputFile, liganatedProtein))
    }

    /**
                                     x         y       z           score
     0         1         2         3         4         5         6
     HETATM    0  R   ClA     0      88.000  70.000   4.000 100.00 15.29
     HETATM    0  R   ClB     1      93.000  76.000  19.000 100.00 14.92
     */
    List<LisePocket> loadPockets(String predictionOutputFile, Protein liganatedProtein) {

        List<LisePocket> res = new ArrayList<>()

        List<String> lines = new File(predictionOutputFile).text.trim().readLines().findAll {
            it.startsWith('HETATM')
        }.toList()

        int i = 1
        for (String line : lines) {
            List<String> cols = Sutils.splitOnWhitespace(line)

            LisePocket poc = new LisePocket()

            poc.rank = i++
            poc.name =  "pocket" + poc.rank
            poc.score = cols.last().toDouble()
            double x = line.substring(30, 37).toDouble()
            double y = line.substring(38, 45).toDouble()
            double z = line.substring(46, 53).toDouble()
            poc.centroid = new Point(x, y, z)
            
            if (liganatedProtein!=null) {
                poc.surfaceAtoms = liganatedProtein.exposedAtoms.cutoutSphere(poc.centroid, SURFACE_ATOMS_CUTOFF)
            }

            res.add(poc)
        }

        return res
    }

    static class LisePocket extends Pocket {

    }

}
