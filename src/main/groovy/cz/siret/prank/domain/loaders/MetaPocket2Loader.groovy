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
 * Loader for MetaPocket 2.0 pocket predictions from *_mpt.pdb file
 */
@Slf4j
@CompileStatic
class MetaPocket2Loader extends PredictionLoader {

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

        List<MetaPocket2Pocket> pockets = loadSitehoundPockets(predictionOutputFile, liganatedProtein)

        return new Prediction(liganatedProtein, pockets)
    }

    /**
                                     x         y       z           score
     ...
     ATOM     16  C3  GHE     6      43.740   4.703  81.152    2   0.70
     ATOM     17  C3  PAS     7      53.154  -5.672  85.079    2   1.34
     TER
     ATOM      1  C3  MPT     1      54.978  -6.722  63.250    3   7.13
     ATOM      2  C3  MPT     2      67.185  -9.621  63.264    5   5.15
     ATOM      3  C3  MPT     3      43.850  -1.771  73.576    2   3.58
     ATOM      4  C3  MPT     4      71.490   2.115  69.358    3   2.76
     ATOM      5  C3  MPT     5      62.504 -20.316  65.450    1   2.53
     ATOM      6  C3  MPT     6      43.755   4.354  81.911    2   1.69
     ATOM      7  C3  MPT     7      53.154  -5.672  85.079    1   1.34
     */
    List<MetaPocket2Pocket> loadSitehoundPockets(String predictionOutputFile, Protein liganatedProtein) {

        List<MetaPocket2Pocket> res = new ArrayList<>()

        Structure struct = PDBUtils.loadFromFile(predictionOutputFile)

        List<String> lines = new File(predictionOutputFile).text.trim().readLines().findAll { it.contains('MPT') }.toList()

        for (String line : lines) {

            List<String> cols = StrUtils.splitOnWhitespace(line)

            MetaPocket2Pocket poc = new MetaPocket2Pocket()

            poc.rank = cols[4].toInteger()
            poc.name =  "pocket" + poc.rank
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

    static class MetaPocket2Pocket extends Pocket {

        double score

    }

}
