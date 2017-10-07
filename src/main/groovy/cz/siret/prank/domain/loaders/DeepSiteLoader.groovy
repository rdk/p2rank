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
 * Loader for DeepSite pocket predictions from *_results.pdb file
 */
@Slf4j
@CompileStatic
class DeepSiteLoader extends PredictionLoader {

    /**
     * cutoff for determining surface atoms around centroid
     */
    double SURFACE_ATOMS_CUTOFF = 8


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
     HETATM 2770  O   HOH X   1      37.560  27.272  15.616  0.00  1.00      XXX
     HETATM 2771  O   HOH X   2      45.560  21.272  35.616  0.00  0.99      XXX
     HETATM 2772  O   HOH X   3      53.560  31.272  25.616  0.00  0.95      XXX
     HETATM 2773  O   HOH X   4      47.560  25.272   5.616  0.00  0.98      XXX
     HETATM 2774  O   HOH X   5      51.560  21.272   9.616  0.00  0.74      XXX
     --
     TER
     HETATM 2865  O   HOH X   1      55.323  17.696  29.383  0.00  1.00      XXX
     ---
     TER
     HETATM 2845  O   HOH X   1     -59.044-104.242 -23.764  0.00  0.95      XXX
     HETATM 2846  O   HOH X   2     -49.044-116.242 -39.764  0.00  0.97      XXX
     HETATM 2847  O   HOH X   3     -65.044-106.242 -11.764  0.00  0.89      XXX
     */
    List<DeepSitePocket> loadPockets(String predictionOutputFile, Protein liganatedProtein) {

        List<DeepSitePocket> res = new ArrayList<>()

        List<String> lines = new File(predictionOutputFile).text.trim().readLines().findAll {
            it.startsWith('HETATM') && it.contains('HOH X') && it.contains('XXX')
        }.toList()

        for (String line : lines) {
            List<String> cols = StrUtils.splitOnWhitespace(line)

            DeepSitePocket poc = new DeepSitePocket()

            poc.rank = cols[5].toInteger() 
            poc.name =  "pocket" + poc.rank
            poc.score = line.substring(60, 66).toDouble()
            double x = line.substring(30, 37).toDouble()
            double y = line.substring(38, 45).toDouble()
            double z = line.substring(46, 53).toDouble()
            poc.centroid = new Point(x, y, z)

            if (liganatedProtein!=null) {
                poc.surfaceAtoms = liganatedProtein.exposedAtoms.cutoffAroundAtom(poc.centroid, SURFACE_ATOMS_CUTOFF)
            }

            res.add(poc)
        }

        // for some inputs pockets are not sorted correctly
        res = res.toSorted { -it.score }

        return res
    }

    static class DeepSitePocket extends Pocket {

    }

}
