package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Point
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.readFile

/**
 * Loader for P2Rank's own pocket predictions from *_predictions.csv file
 */
@Slf4j
@CompileStatic
class P2RankLoader extends PredictionLoader {

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
     name,rank,score,connolly_points,surf_atoms,center_x,center_y,center_z,residue_ids,surf_atom_ids
     pocket1,1,12.2198,28,33,53.5525,-6.1944,84.0798,115 116 12 13 14 145 15 16 17 18 59 60 61 63 64,2081 2082 2084...
     pocket2,2,7.0166,22,26,54.6471,-5.8876,64.3958,110 112 129 131 132 133 134 137 139 81 91 94 95 98,2631 2712 27...
     pocket3,3,3.1349,9,20,44.2965,-2.9532,74.4911,100 11 63 66 67 68 93 96 97,2078 2485 2511 2512 2513 2514 2515 2...
     pocket4,4,2.5382,6,17,60.375,-10.8494,82.832,115 116 118 144 145 18,2119 2120 2121 2909 2910 2911 2915 2916 29...
     pocket5,5,1.6915,4,14,55.3714,13.3478,81.8956,31 33 36 40 5 55 57,2030 2033 2231 2233 2254 2273 2299 2300 2301...
     pocket6,6,1.0779,3,12,68.6204,0.9515,61.521,135 138 156 159 160 165,3070 3071 3098 3254 3256 3259 3279 3281 32...

     */
    List<P2RankPocket> loadPockets(String predictionOutputFile, Protein liganatedProtein) {

        List<P2RankPocket> res = new ArrayList<>()

        List<String> lines = readFile(predictionOutputFile).trim().readLines().tail()


        int i = 1
        for (String line : lines) {
            List<String> cols = Sutils.split(line, ',')

            P2RankPocket poc = new P2RankPocket()

            poc.rank = i++
            poc.name =  cols[0]
            poc.score = cols[2].toDouble()
            double x = cols[5].toDouble()
            double y = cols[6].toDouble()
            double z = cols[7].toDouble()
            poc.centroid = new Point(x, y, z)

            if (liganatedProtein!=null) {
                poc.surfaceAtoms = liganatedProtein.exposedAtoms.cutoutSphere(poc.centroid, SURFACE_ATOMS_CUTOFF)
            }

            res.add(poc)
        }

        return res
    }

    static class P2RankPocket extends Pocket {

    }

}
