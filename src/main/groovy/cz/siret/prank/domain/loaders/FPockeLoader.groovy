package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.LoaderParams
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.PDBUtils
import cz.siret.prank.utils.Futils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.*

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Loader for predictions produced by Fpocket (v1.0 and v2.0).
 */
@Slf4j
class FPockeLoader extends PredictionLoader {

    public static class FPocketPocket extends Pocket {

        Atoms vornoiCenters

        FPocketPocket() {
            stats = new FPocketStats()
        }

        @Override
        Atom getCentroid() {
            return vornoiCenters.centerOfMass
        }

        FPocketStats getFpstats() {
            return (FPocketStats) stats
        }
    }

    @Override
    Prediction loadPrediction(String pocketPredictionOutputFile, Protein liganatedProtein) {
        
        return loadResultFromFile(pocketPredictionOutputFile)
    }

    /**
     * must be called on main fpocket result pdb file in directory with ./pockets subdirectory
     *
     * @param resultPdbFileName
     */
    private Prediction loadResultFromFile(String resultPdbFileName) {

        Protein protein = Protein.load(resultPdbFileName, new LoaderParams(ignoreLigands: true)) // hetatm atoms here are pocket vornoi centers
        protein.allAtoms.withIndex()

        List<Pocket> pockets = new ArrayList<>()
        File resultFile = new File(resultPdbFileName)
        List<Group> fpocketGroups = protein.structure.hetGroups.findAll { Group g -> "STP" == g.PDBName}

        log.info "loading ${fpocketGroups.size()} pockets"

        int pocketIndex = 0;
        for (Group g in fpocketGroups) {
            assert g instanceof HetatomImpl
            HetatomImpl pocketGroup = g

            //println "loading het group $pocketIndex $pocketGroup.PDBName"

            FPocketPocket fpocket = new FPocketPocket()
            fpocket.rank = pocketIndex+1
            fpocket.vornoiCenters = new Atoms( pocketGroup.getAtoms() )

            //important to set element for center of mass calculation
            fpocket.vornoiCenters.list.each { Atom a -> a.setElement(Element.C)}

            String pocketAtmFile = resultFile.getParent() + File.separator + "pockets" + File.separator + "pocket${pocketIndex}_atm.pdb"
            Structure pocketAtmStructure = loadPocketStructureAndDetails(pocketAtmFile, fpocket)
            Atoms pocketAtmAtoms = Atoms.allFromStructure(pocketAtmStructure)

            // we want Atom objects from/linked to original structure
            Atoms surfaceAtoms = new Atoms()
            for (Atom atm in pocketAtmAtoms.list) {
                Atom linkedAtom = protein.allAtoms.getByID(atm.PDBserial)
                if (linkedAtom!=null) {
                    surfaceAtoms.add(linkedAtom)
                } else {
                    log.warn "linked atom from pocket not found in protein [id:$atm.PDBserial]"
                }
            }

            fpocket.surfaceAtoms = surfaceAtoms
            fpocket.name = "pocket.$fpocket.rank"
            fpocket.centroid = fpocket.vornoiCenters.centerOfMass

            pockets.add(fpocket)

            log.debug "$fpocket"

            pocketIndex++
        }

        return new Prediction(protein, pockets)
    }


    /**
     * read details from special fpocket output pdb file for one pocket (atom file)
     * @param pocketAtmFile
     * @param fpocket load details to
     */
    private Structure loadPocketStructureAndDetails(String pocketAtmFileName, FPocketPocket fpocket) {

        if (!Futils.exists(pocketAtmFileName)) {
            throw new FileNotFoundException(pocketAtmFileName)
        }

        File pocketFile = new File(pocketAtmFileName)

        StringBuilder tmpPdb = new StringBuilder(pocketFile.size() as int)

        // ignoring headers because of biojava

        for (String line : pocketFile.text.readLines()) {
            if (line.startsWith("HEADER")) {
                fpocket.fpstats.parseLine(line)
            } else {
                tmpPdb.append(line)
                tmpPdb.append("\n")
            }
        }

        fpocket.fpstats.consolidate()

        Structure struc = PDBUtils.loadFromString(tmpPdb.toString())

        return struc
    }



    /**
     HEADER 0  - Pocket Score                      : -1.5909
     HEADER 1  - Number of V. Vertices             :    54
     HEADER 2  - Mean alpha-sphere radius          : 3.5404
     HEADER 3  - Mean alpha-sphere SA              : 0.4613
     HEADER 4  - Mean B-factor                     : 0.3546
     HEADER 5  - Hydrophobicity Score              : 30.8333
     HEADER 6  - Polarity Score                    :     4
     HEADER 7  - Volume Score                      : 3.4167
     HEADER 8  - Real volume (approximation)       : 1217.1342
     HEADER 9  - Charge Score                      :     1
     HEADER 10 - Local hydrophobic density Score   : 9.3333
     HEADER 11 - Number of apolar alpha sphere     :    12
     HEADER 12 - Proportion of apolar alpha sphere : 0.2222

     Fpocket2

     HEADER 0  - Pocket Score                      : 33.0770
     HEADER 1  - Drug Score                        : 0.8698
     HEADER 2  - Number of V. Vertices             :   153
     HEADER 3  - Mean alpha-sphere radius          : 3.7240
     HEADER 4  - Mean alpha-sphere SA              : 0.4269
     HEADER 5  - Mean B-factor                     : 0.1456
     HEADER 6  - Hydrophobicity Score              : 38.3200
     HEADER 7  - Polarity Score                    :     9
     HEADER 8  - Volume Score                      : 4.3200
     HEADER 9  - Real volume (approximation)       : 1022.6770
     HEADER 10 - Charge Score                      :     3
     HEADER 11 - Local hydrophobic density Score   : 72.2016
     HEADER 12 - Number of apolar alpha sphere     :   129
     HEADER 13 - Proportion of apolar alpha sphere : 0.8431

     */
    static class FPocketStats extends Pocket.PocketStats {

        static final Pattern PATTERN = ~ /HEADER (\d+) [^\:]* :\s* ([-\.\d]*).*/

        Double[] headers = new Double[14]

        double pocketScore
        int vornoiVertices
        double polarityScore
        double realVolumeApprox

        public parseLine(String line) {
            Matcher matcher = PATTERN.matcher(line)
            if (matcher.matches()) {
                int id = matcher[0][1] as int
                String vals = matcher[0][2]

                try {
                    double val = Double.parseDouble(vals)
                    headers[id] = val
                } catch (Exception e) {
                    log.warn "invalid pocket stat value: " + line
                    headers[id] = 0
                }
            }
        }

        /**
            fpocket1
         */
        public void consolidate() {

            pocketScore = headers[0]
            vornoiVertices = headers[1]
            polarityScore = headers[6]
            realVolumeApprox = headers[8]
        }

        public List<Double> getVector() {
            return headers
        }

        static List<String> getHeader() {
            return [
                    "Pocket Score",
                    "Number of V. Vertices",
                    "Mean alpha-sphere radius",
                    "Mean alpha-sphere SA",
                    "Mean B-factor",
                    "Hydrophobicity Score",
                    "Polarity Score",
                    "Volume Score",
                    "Real volume (approximation)",
                    "Charge Score",
                    "Local hydrophobic density Score",
                    "Number of apolar alpha sphere",
                    "Proportion of apolar alpha sphere"
            ]
        }

        @Override
        public String toString() {
            return "Stats {" +
                    "\n  pocketScore=" + pocketScore +
                    "\n  vornoiVertices=" + vornoiVertices +
                    "\n  polarityScore=" + polarityScore +
                    "\n  realVolumeApprox=" + realVolumeApprox +
                    '\n}';
        }

    }

}
