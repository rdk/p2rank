package cz.siret.prank.domain.loaders

import com.google.common.base.Splitter
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.geom.Struct
import cz.siret.prank.utils.PDBUtils
import cz.siret.prank.utils.StrUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.Structure

/**
 *
 */
@Slf4j
@CompileStatic
class SiteHoundLoader {


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

        List<SiteHoundPocket> pockets = loadSitehoundPockets(predictionOutputFile)

        return new Prediction(liganatedProtein, pockets)
    }

    List<SiteHoundPocket> loadSitehoundPockets(String predictionOutputFile) {

        List<SiteHoundPocket> res = new ArrayList<>()

        for (String line : new File(predictionOutputFile).text.trim().readLines()) {

            List<String> cols = StrUtils.splitOnWhitespace(line)

            SiteHoundPocket poc = new SiteHoundPocket()

            poc.rank = cols[0].toInteger()
            poc.name =  "pocket." + poc.rank
            poc.totalInteractionEnergy = cols[1].toDouble()
            poc.newScore = - poc.totalInteractionEnergy
            poc.volume = cols[2].toDouble()

            double x = cols[3].toDouble()
            double y = cols[4].toDouble()
            double z = cols[5].toDouble()

            poc.centroid = new Point(x, y, z)

            res.add(poc)
        }

        return res
    }

    public static class SiteHoundPocket extends Pocket {

        Atoms gridPoints

        double volume
        double totalInteractionEnergy
    }

}
