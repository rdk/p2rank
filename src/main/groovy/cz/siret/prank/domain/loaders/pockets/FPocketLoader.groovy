package cz.siret.prank.domain.loaders.pockets

import com.google.common.base.Splitter
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Structure

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Loader for predictions produced by Fpocket (1.0 - 4.2.2).
 */
@Slf4j
@CompileStatic
class FPocketLoader extends PredictionLoader implements Parametrized {

    public static class FPocketPocket extends Pocket {

        Atoms voronoiCenters
        Atoms sasPoints

        FPocketPocket() {
            stats = new FPocketStats()
        }

        @Override
        Atom getCentroid() {
            return voronoiCenters.centerOfMass
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
    Prediction loadPrediction(String pocketPredictionOutputFile, Protein queryProtein) {
        
        return loadResultFromFile(pocketPredictionOutputFile, queryProtein)
    }

    /**
     * since Fpocket 3.0 pockets are indexed starting with 1 (older versions from 0)
     *
     * TODO find better way to check fpocket version
     */
    boolean isFpocket3Prediction(File resultFile) {
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
     * @param resultStructFileName
     */
    private Prediction loadResultFromFile(String resultStructFileName, Protein queryProtein) {

        Protein protein = queryProtein   // original query protein (input to fpocket)
        protein.proteinAtoms.withIndex() // create index on protein atoms



        List<Pocket> pockets = new ArrayList<>()
        File resultFile = new File(resultStructFileName)

        String extension = Futils.lastExt(resultStructFileName)  // pdb or cif
        String pocketsDir = "${resultFile.parent}/pockets" 

        boolean isFpocket3OrNewer = isFpocket3Prediction(resultFile)


        List<Atoms> fpocketGroups = loadPocketGroups(resultStructFileName)
        log.info "loading ${fpocketGroups.size()} pockets"

        if (hasTransformation()) {
            for (Atoms atoms : fpocketGroups) {
                transformation.applyToAtoms(atoms)
            }
        }

        int pocketIndex = isFpocket3OrNewer ? 1 : 0

        int rank = 1
        for (Atoms g in fpocketGroups) {

            //println "loading het group $pocketIndex $pocketGroup.PDBName"

            FPocketPocket pocket = new FPocketPocket()
            pocket.rank = rank++
            pocket.voronoiCenters = g

            String pocketAtmFile = "${pocketsDir}/pocket${pocketIndex}_atm.${extension}"
            if (!Futils.exists(pocketAtmFile)) {
                pocketAtmFile += ".gz"
            }
            Atoms pocketAtmAtoms = loadPocketAtomsAndDetails(pocketAtmFile, pocket)

            // we want Atom objects from/linked to original structure
            Atoms surfaceAtoms = new Atoms()
            for (Atom atm in pocketAtmAtoms.list) {
                Atom linkedAtom = protein.proteinAtoms.getByID(atm.PDBserial)
                
                if (linkedAtom != null) {
                    surfaceAtoms.add(linkedAtom)
                } else {
                    // TODO fpocket3: check why ids not found / select atoms by distance
                    log.warn "linked atom from pocket not found in protein [id:$atm.PDBserial]"
                }
            }

            pocket.surfaceAtoms = surfaceAtoms
            pocket.name = "pocket.$pocket.rank"
            pocket.centroid = pocket.voronoiCenters.centerOfMass
            pocket.score = pocket.stats.pocketScore

            // sas points
            double surfaceSasCutoff = params.getSasCutoffDist()
            Atoms sas2 = queryProtein.accessibleSurface.points.cutoutShell(surfaceAtoms, surfaceSasCutoff)
            Atoms sas1 = queryProtein.accessibleSurface.points.cutoutShell(pocket.voronoiCenters, params.extended_pocket_cutoff) // probably not needed
            Atoms sas = Atoms.union(sas1, sas2)
            pocket.sasPoints = sas

            pockets.add(pocket)

            log.debug "$pocket"

            pocketIndex++
        }

        return new Prediction(protein, pockets)
    }

    private List<Atoms> loadPocketGroups(String resultPdbFileName) {
        String formatExtension = Futils.realExtension(resultPdbFileName)
        if (formatExtension == "cif") {
            return loadPocketGroupsFromCif(resultPdbFileName)
        } else {
            return loadPocketGroupsFromPdb(resultPdbFileName)
        }
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
    private List<Atoms> loadPocketGroupsFromPdb(String resultPdbFileName) {

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

    Splitter CIF_ATOM_LINE_SPLITTER = Splitter.on(" ").omitEmptyStrings().trimResults()

    /**

     loop_
     _atom_site.group_PDB
     _atom_site.id
     _atom_site.type_symbol
     _atom_site.label_atom_id
     _atom_site.label_alt_id
     _atom_site.label_comp_id
     _atom_site.label_asym_id
     _atom_site.label_seq_id
     _atom_site.pdbx_PDB_ins_code
     _atom_site.Cartn_x
     _atom_site.Cartn_y
     _atom_site.Cartn_z
     _atom_site.occupancy
     _atom_site.pdbx_formal_charge
     _atom_site.auth_seq_id
     _atom_site.auth_asym_id

     ATOM    2965     C   CB ?  CYS      A 367 ?   82.889  112.429   -5.721   0.00  0 466 A
     ATOM    2966     S   SG ?  CYS      A 367 ?   82.149  110.870   -5.104   0.00  0 466 A
     HETATM  2971    Zn   ZN ?   ZN      F 0 ?   67.379   73.289  -14.367   0.00  0 997 A
     HETATM  2972    Zn   ZN ?   ZN      G 0 ?   70.574   84.649  -10.924   0.00  0 998 A
     HETATM  1        V APOL .  STP      C 1 .   72.107   81.083   -5.962   0.00  0 1 C
     HETATM  2        V APOL .  STP      C 1 .   72.593   82.262   -8.528   0.00  0 1 C

     * @param resultCifFile e.g. 1fbl_out.cif
     * @return
     */
    private List<Atoms> loadPocketGroupsFromCif(String resultCifFile) {
        // Biojava parser fails, missing many columns

        List<String> lines = Futils.readPossiblyCompressedFile(resultCifFile).readLines()

        Map<Integer, Atoms> groups = new HashMap<>()

        for (String line : lines) {
            if (!line.startsWith('HETATM')) {
                continue
            }

            List<String> cols = CIF_ATOM_LINE_SPLITTER.splitToList(line)

            if (!"STP".equals(cols[5])) {
                continue
            }

            int pocketNum = cols[7].toInteger()
            double x = cols[9].toDouble()
            double y = cols[10].toDouble()
            double z = cols[11].toDouble()

            Point p = new Point(x, y, z)

            if (!groups.containsKey(pocketNum)) {
                groups.put(pocketNum, new Atoms())
            }
            groups.get(pocketNum).add(p)
        }

        List<Atoms> res = new ArrayList<>()

        for (int i=1; i<=groups.keySet().size(); i++) {
            res.add(groups.get(i))
        }

        return res
    }

    /**
     * read details from special fpocket output pdb file for one pocket (atom file)
     *
     * ! for now only pdb works due to biojava cif parsing error
     *
     * @param pocketAtmFile
     * @param fpocket load details to
     */
    private Atoms loadPocketAtomsAndDetails(String pocketAtmFileName, FPocketPocket pocket) {
        if (!Futils.exists(pocketAtmFileName)) {
            throw new FileNotFoundException(pocketAtmFileName)
        }

        String formatExtension = Futils.realExtension(pocketAtmFileName)
        if (formatExtension == "cif") {
            return loadPocketAtomsAndDetailsFromCif(pocketAtmFileName, pocket)
        } else { // pdb
            return loadPocketAtomsAndDetailsFromPdb(pocketAtmFileName, pocket)
        }
    }


    private Atoms loadPocketAtomsAndDetailsFromPdb(String pocketAtmFileName, FPocketPocket pocket) {

        List<String> lines = Futils.readPossiblyCompressedFile(pocketAtmFileName).readLines()

        StringBuilder tmpPdb = new StringBuilder(lines.size() * 80)

        // ignoring headers because of biojava

        int nAtomLines = 0
        for (String line : lines) {
            if (line.startsWith("HEADER")) {
                pocket.fpstats.parseLine(line)
            } else {
                tmpPdb.append(line)
                tmpPdb.append("\n")
            }
            if (line.startsWith("ATOM")) {
                nAtomLines++
            }
        }
        log.info("ATOM lines in pocket file: {}", nAtomLines)

        pocket.fpstats.consolidate()

        Structure structure = PdbUtils.loadFromString(tmpPdb.toString())

        return Atoms.allFromStructure(structure)
    }

    /**

     loop_
     _struct.pdbx_descriptor
     This is a mmcif format file writen by the programm fpocket.
     It represents the atoms contacted by the voronoi vertices of the pocket.

     Information about the pocket     1:
     0  - Pocket Score                      : 0.6703
     1  - Drug Score                        : 0.4038
     2  - Number of alpha spheres           :    60
     3  - Mean alpha-sphere radius          : 3.9971
     4  - Mean alpha-sphere Solvent Acc.    : 0.5171
     5  - Mean B-factor of pocket residues  : 0.0000
     6  - Hydrophobicity Score              : 11.5000
     7  - Polarity Score                    :     6
     8  - Amino Acid based volume Score     : 3.5833
     9  - Pocket volume (Monte Carlo)       : 469.0056
     10  -Pocket volume (convex hull)       : 47.4540
     11 - Charge Score                      :     1
     12 - Local hydrophobic density Score   : 16.0000
     13 - Number of apolar alpha sphere     :    17
     14 - Proportion of apolar alpha sphere : 0.2833
     #
     loop_
     _atom_site.group_PDB
     _atom_site.id
     _atom_site.type_symbol
     _atom_site.label_atom_id
     _atom_site.label_alt_id
     _atom_site.label_comp_id
     _atom_site.label_asym_id
     _atom_site.label_seq_id
     _atom_site.pdbx_PDB_ins_code
     _atom_site.Cartn_x
     _atom_site.Cartn_y
     _atom_site.Cartn_z
     _atom_site.occupancy
     _atom_site.pdbx_formal_charge
     _atom_site.auth_seq_id
     _atom_site.auth_asym_id
     ATOM    942      C  CG1 ?  VAL      A 116 ?   69.427   79.635   -4.211   0.00  0 215 A
     ATOM    972      O  OE2 ?  GLU      A 120 ?   68.915   81.437   -7.387   0.00  0 219 A
     HETATM  2972    Zn   ZN ?   ZN      G 0 ?   70.574   84.649  -10.924   0.00  0 998 A

     * @param pocketAtmFileName
     * @param pocket
     * @return
     */
    private Atoms loadPocketAtomsAndDetailsFromCif(String pocketAtmFileName, FPocketPocket pocket) {
        // Biojava parser fails, missing many columns
        // org.rcsb.cif.EmptyColumnException: column pdbx_PDB_model_num is undefined
        // at org.rcsb.cif.model.Column$EmptyColumn.getStringData(Column.java:111) ~[ciftools-java-jdk8-3.0.0.jar:?]


        List<String> lines = Futils.readPossiblyCompressedFile(pocketAtmFileName).readLines()

        Atoms atoms = new Atoms()
        for (String line : lines) {
            if (!(line.startsWith('ATOM') || line.startsWith('HETATM'))) {
                continue
            }

            List<String> cols = CIF_ATOM_LINE_SPLITTER.splitToList(line)

            int pdbId = cols[1].toInteger()
            // String element = cols[3]
            double x = cols[9].toDouble()
            double y = cols[10].toDouble()
            double z = cols[11].toDouble()

            atoms.add(simpleAtom(pdbId, x, y, z))
        }

        //TODO implement more sensible way to parse headers

        List<String> headerLines = Sutils.selectLinesBetweenExcluding(lines,
                { String s -> s.startsWith("Information about the pocket") },
                { String s -> s.startsWith("#") }
        )
        headerLines = Sutils.prefixEach("HEADER ", headerLines)
        if (headerLines.empty) {
            throw new PrankException("No header lines found for fpocket pocket in [$pocketAtmFileName]")
        }

        for (String line : headerLines) {
            pocket.fpstats.parseLine(line)
        }
        pocket.fpstats.consolidate()

        return atoms
    }

    private static Atom simpleAtom(int pdbId, double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.setPDBserial(pdbId)
        a.setX(x)
        a.setY(y)
        a.setZ(z)
        return a
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

     Fpocket 4.0.1

     HEADER 0  - Pocket Score                      : 0.2226
     HEADER 1  - Drug Score                        : 0.9780
     HEADER 2  - Number of alpha spheres           :   336
     HEADER 3  - Mean alpha-sphere radius          : 3.9803
     HEADER 4  - Mean alpha-sphere Solvent Acc.    : 0.4466
     HEADER 5  - Mean B-factor of pocket residues  : 0.6192
     HEADER 6  - Hydrophobicity Score              : 31.2459
     HEADER 7  - Polarity Score                    :    23
     HEADER 8  - Amino Acid based volume Score     : 3.7377
     HEADER 9  - Pocket volume (Monte Carlo)       : 2820.3418
     HEADER 10  -Pocket volume (convex hull)       : 3057.8672
     HEADER 11 - Charge Score                      :     1
     HEADER 12 - Local hydrophobic density Score   : 46.8166
     HEADER 13 - Number of apolar alpha sphere     :   169
     HEADER 14 - Proportion of apolar alpha sphere : 0.5030

     Fpocket 4.2

     HEADER Information about the pocket     1:
     HEADER 0  - Pocket Score                      : 0.6703
     HEADER 1  - Drug Score                        : 0.4038
     HEADER 2  - Number of alpha spheres           :    60
     HEADER 3  - Mean alpha-sphere radius          : 3.9971
     HEADER 4  - Mean alpha-sphere Solvent Acc.    : 0.5171
     HEADER 5  - Mean B-factor of pocket residues  : 0.0000
     HEADER 6  - Hydrophobicity Score              : 11.5000
     HEADER 7  - Polarity Score                    :     6
     HEADER 8  - Amino Acid based volume Score     : 3.5833
     HEADER 9  - Pocket volume (Monte Carlo)       : 472.6400
     HEADER 10 - Pocket volume (convex hull)       : 47.4540
     HEADER 11 - Charge Score                      :     1
     HEADER 12 - Local hydrophobic density Score   : 16.0000
     HEADER 13 - Number of apolar alpha sphere     :    17
     HEADER 14 - Proportion of apolar alpha sphere : 0.2833


     TODO fix stats parsing for newer versions of fpocket
     */
    @CompileStatic(value = TypeCheckingMode.SKIP)
    static class FPocketStats extends Pocket.PocketStats {

        static final Pattern PATTERN = ~ /HEADER\s*(\d+)\s*-[^\:]* :\s* ([-\.\d]*).*/

        Double[] headers = new Double[20]

        double pocketScore
        int voronoiVertices
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
                    log.warn "invalid pocket stat value: [$line] ($e.message)"
                    headers[id] = 0
                }
            }
        }

        /**
            fpocket1
         */
        public void consolidate() {

            pocketScore = headers[0] ?: Double.NaN
            voronoiVertices = headers[1] ?: Double.NaN
            polarityScore = headers[6] ?: Double.NaN
            realVolumeApprox = headers[8] ?: Double.NaN
            
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
                    "\n  voronoiVertices=" + voronoiVertices +
                    "\n  polarityScore=" + polarityScore +
                    "\n  realVolumeApprox=" + realVolumeApprox +
                    '\n}';
        }

    }

}
