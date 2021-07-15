package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

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

        return new Prediction(liganatedProtein, loadPockets(predictionOutputFile, liganatedProtein))
    }

    /**
     Record Format

     COLUMNS        DATA  TYPE    FIELD        DEFINITION
     -------------------------------------------------------------------------------------
     1 -  6        Record name   "ATOM  "
     7 - 11        Integer       serial       Atom  serial number.
     13 - 16        Atom          name         Atom name.
     17             Character     altLoc       Alternate location indicator.
     18 - 20        Residue name  resName      Residue name.
     22             Character     chainID      Chain identifier.
     23 - 26        Integer       resSeq       Residue sequence number.
     27             AChar         iCode        Code for insertion of residues.
     31 - 38        Real(8.3)     x            Orthogonal coordinates for X in Angstroms.
     39 - 46        Real(8.3)     y            Orthogonal coordinates for Y in Angstroms.
     47 - 54        Real(8.3)     z            Orthogonal coordinates for Z in Angstroms.
     55 - 60        Real(6.2)     occupancy    Occupancy.
     61 - 66        Real(6.2)     tempFactor   Temperature  factor.
     77 - 78        LString(2)    element      Element symbol, right-justified.
     79 - 80        LString(2)    charge       Charge  on the atom.

                                     x         y       z           score
     0         1         2         3         4         5         6
     ...
     ATOM     16  C3  GHE     6      43.740   4.703  81.152    2   0.70
     ATOM     17  C3  PAS     7      53.154  -5.672  85.079    2   1.34
     TER
     ATOM      1  C3  MPT     1     -25.464  69.313-132.566    6  15.41
     ATOM      2  C3  MPT     2     -14.482  55.768-102.619    5   9.13
     ATOM      3  C3  MPT     3     -28.586  58.568 -94.116    1   2.89
     ATOM      4  C3  MPT     4     -21.459  52.766-115.823    2   1.91
     ATOM      5  C3  MPT     5     -19.953  62.558-115.965    1   1.23
     ATOM      6  C3  MPT     6     -22.499  67.767 -92.176    1   1.11
     ATOM      7  C3  MPT     7     -24.911  52.100 -91.121    1   0.83

     */
    List<MetaPocket2Pocket> loadPockets(String predictionOutputFile, Protein liganatedProtein) {

        List<MetaPocket2Pocket> res = new ArrayList<>()

        List<String> lines = new File(predictionOutputFile).text.trim().readLines().findAll { it.contains('MPT') }.toList()

        int i = 1
        for (String line : lines) {

            MetaPocket2Pocket poc = new MetaPocket2Pocket()

            poc.rank = i++
            poc.name =  "pocket" + poc.rank
            poc.score = line.substring(60, 66).toDouble()
            double x = line.substring(30, 37).toDouble()
            double y = line.substring(38, 45).toDouble()
            double z = line.substring(46, 53).toDouble()
            poc.centroid = new Point(x, y, z)

            if (liganatedProtein!=null) {
                poc.surfaceAtoms = liganatedProtein.exposedAtoms.cutoutSphere(poc.centroid, SURFACE_ATOMS_CUTOFF)
            }

            res.add(poc)
        }

        res = res.toSorted { -it.score }

        return res
    }

    static class MetaPocket2Pocket extends Pocket {

    }

}
