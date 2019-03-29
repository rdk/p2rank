package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Structure

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Loader for predictions produced by Fpocket (v1.0 and v2.0).
 */
@Slf4j
class FPocketLoader extends PredictionLoader implements Parametrized {

    public static class FPocketPocket extends Pocket {

        Atoms vornoiCenters
        Atoms sasPoints

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

        @Override
        Atoms getSasPoints() {
            return sasPoints
        }
    }

    @Override
    Prediction loadPrediction(String pocketPredictionOutputFile, Protein liganatedProtein) {
        
        return loadResultFromFile(pocketPredictionOutputFile, liganatedProtein)
    }


    boolean isFpocket3Prediction(File resultFile) {
        // Fpocket 3.0 pockets are indexed starting with 1 (older versions from 0)
        // TODO find better way to check fpocket version

        File pocketsSubdir = new File(resultFile.parent + "/pockets")
        for (String fname : pocketsSubdir.list()) {
            if (fname.startsWith("pocket0")) {
                return false
            }
        }
        return true
    }

    /**
     * must be called on main fpocket result pdb file in directory with ./pockets subdirectory
     *
     * @param resultPdbFileName
     */
    private Prediction loadResultFromFile(String resultPdbFileName, Protein queryProtein) {

        Protein protein = queryProtein   // original query protein (input to fpocket)
        protein.proteinAtoms.withIndex() // create index on protein atoms

        List<Pocket> pockets = new ArrayList<>()
        File resultFile = new File(resultPdbFileName)

        boolean isFpocket3 = isFpocket3Prediction(resultFile)


        List<Atoms> fpocketGroups = loadPocketGroups(resultPdbFileName)
        log.info "loading ${fpocketGroups.size()} pockets"

        int pocketIndex = 0
        if (isFpocket3) {
            pocketIndex = 1
        }

        int rank = 1
        for (Atoms g in fpocketGroups) {

            //println "loading het group $pocketIndex $pocketGroup.PDBName"

            FPocketPocket pocket = new FPocketPocket()
            pocket.rank = rank++
            pocket.vornoiCenters = g

            String pocketAtmFile = resultFile.parent + File.separator + "pockets" + File.separator + "pocket${pocketIndex}_atm.pdb"
            Structure pocketAtmStructure = loadPocketStructureAndDetails(pocketAtmFile, pocket)
            Atoms pocketAtmAtoms = Atoms.allFromStructure(pocketAtmStructure)

            // we want Atom objects from/linked to original structure
            Atoms surfaceAtoms = new Atoms()
            for (Atom atm in pocketAtmAtoms.list) {
                Atom linkedAtom = protein.proteinAtoms.getByID(atm.PDBserial)
                // TODO fpocket3: check why ids not found / select atoms by distance
                if (linkedAtom!=null) {
                    surfaceAtoms.add(linkedAtom)
                } else {
                    log.warn "linked atom from pocket not found in protein [id:$atm.PDBserial]"
                }
            }

            pocket.surfaceAtoms = surfaceAtoms
            pocket.name = "pocket.$pocket.rank"
            pocket.centroid = pocket.vornoiCenters.centerOfMass
            pocket.score = pocket.stats.pocketScore

            // sas points
            double surfaceSasCutoff = params.getSasCutoffDist()
            Atoms sas2 = queryProtein.accessibleSurface.points.cutoutShell(surfaceAtoms, surfaceSasCutoff)
            Atoms sas1 = queryProtein.accessibleSurface.points.cutoutShell(pocket.vornoiCenters, params.extended_pocket_cutoff) // probably not needed
            Atoms sas = Atoms.union(sas1, sas2)
            pocket.sasPoints = sas

            pockets.add(pocket)

            log.debug "$pocket"

            pocketIndex++
        }

        return new Prediction(protein, pockets)
    }

    /**
     * ! fpocket sometimes produces files unparsable by biojava with letters in id column (here:...975f)
     *
     HETATM91317 APOL STP C   1      43.189 -15.571 -19.933  0.00  0.00          Ve
     HETATM91317  POL STP C   1      43.122 -15.632 -19.896  0.00  0.00          Ve
     HETATM99532  POL STP C   1      44.632 -16.282 -19.585  0.00  0.00          Ve
     HETATM1975f APOL STP C   1      52.281 -25.921  -7.631  0.00  0.00          Ve
     HETATM24676 APOL STP C   2      -2.155 -21.045  -4.717  0.00  0.00          Ve
     HETATM40261 APOL STP C   2      -1.977 -22.364  -5.748  0.00  0.00          Ve
     HETATM40261 APOL STP C   2      -2.370 -22.325  -5.943  0.00  0.00          Ve
     HETATM55930 APOL STP C   2      -2.407 -22.341  -6.002  0.00  0.00          Ve
     */
    private List<Atoms> loadPocketGroups(String resultPdbFileName) {

        List<String> lines = new File(resultPdbFileName).text.trim().readLines()

        Map<Integer, Atoms> groups = new HashMap<>()

        for (String line : lines) {
            if (!(line.startsWith('HETATM') && line.contains('STP C') && line.contains('Ve'))) {
                continue
            }

            int seqNum = line.substring(22, 26).toInteger()
            double x = line.substring(30, 37).toDouble()
            double y = line.substring(38, 45).toDouble()
            double z = line.substring(46, 53).toDouble()

            Point p = new Point(x, y, z)

            if (!groups.containsKey(seqNum)) {
                groups.put(seqNum, new Atoms())
            }
            groups.get(seqNum).add(p)
        }

        List<Atoms> res = new ArrayList<>()

        for (int i=1; i<=groups.keySet().size(); i++) {
            res.add(groups.get(i))
        }

        return res
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

        Structure struc = PdbUtils.loadFromString(tmpPdb.toString())

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

        Double[] headers = new Double[20]

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
