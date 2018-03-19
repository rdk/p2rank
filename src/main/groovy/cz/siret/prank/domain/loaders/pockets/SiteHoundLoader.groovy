package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Point
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Loader for SiteHound pocket predictions (*_summary.dat file)
 */
@Slf4j
@CompileStatic
class SiteHoundLoader extends PredictionLoader {

    /**
     * cutoff for determining surface atoms around centroid
     */
    double SURFACE_ATOMS_CUTOFF = 8

    int POCKET_LIMIT = 12


    /**
     * @param *_summary.dat output file from SiteHound (i.e. a.001.001.001_1s69a_CMET_summary.dat)
     *
     * File columns:
     *      0. rank
     *      1. Total Interaction Energy (TIE) =  score
     *      2. Volume [A^3]
     *      3. center_X
     *      4. center_Y
     *      5. center_Z
     *
     * Example:
     * <pre>
     * 1	-957.773	82	   3.890	  24.703	  -0.964
     * 2	-818.970	73	   4.350	  18.976	   3.204
     * 3	-479.808	47	   6.486	  30.078	   7.861
     * </pre>
     *
     * @return
     */
    @Override
    Prediction loadPrediction(String predictionOutputFile, Protein liganatedProtein) {

        return new Prediction(liganatedProtein, loadPockets(predictionOutputFile, liganatedProtein))
    }

    List<SiteHoundPocket> loadPockets(String predictionOutputFile, Protein liganatedProtein) {

        List<SiteHoundPocket> res = new ArrayList<>()

        for (String line : new File(predictionOutputFile).text.trim().readLines()) {

            List<String> cols = Sutils.splitOnWhitespace(line)

            SiteHoundPocket poc = new SiteHoundPocket()

            poc.rank = cols[0].toInteger()
            poc.name =  "pocket." + poc.rank
            poc.energy = cols[1].toDouble()
            poc.score = -poc.energy
            poc.volume = cols[2].toDouble()

            double x = cols[3].toDouble()
            double y = cols[4].toDouble()
            double z = cols[5].toDouble()

            poc.centroid = new Point(x, y, z)
            if (liganatedProtein!=null) {
                poc.surfaceAtoms = liganatedProtein.exposedAtoms.cutoutSphere(poc.centroid, SURFACE_ATOMS_CUTOFF)
            }

            res.add(poc)

            if (res.size()>=POCKET_LIMIT) {
                break
            }
        }

        // no sorting needed, SiteHound correctly sorts them by energy already

        return res
    }

    static class SiteHoundPocket extends Pocket {

        double volume
        double energy
    }

}
