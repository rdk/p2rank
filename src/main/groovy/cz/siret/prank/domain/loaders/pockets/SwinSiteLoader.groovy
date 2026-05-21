package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import javax.annotation.Nullable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Loader for predictions produced by SwinSite, a 3D Swin-Transformer
 * voxel-density binding-site predictor (https://doi.org/10.1021/acs.jcim.5c02734).
 *
 * SwinSite emits a per-protein output directory containing two Mol2 files
 * per detected pocket:
 *   - pocket{N}_score_{S:.4f}.mol2  protein atoms within 4.5 A of the grid
 *   - grid{N}_score_{S:.4f}.mol2    raw voxel points (~1.5 A spacing, type Du)
 *
 * Score S is encoded in the filename. We read only grid*.mol2 and derive
 * surface atoms by cutoutShell against queryProtein.exposedAtoms, mirroring
 * ConcavityLoader. The pocket*.mol2 file is intentionally ignored: its
 * atoms are standalone (no parent Group/Chain) and its chain is reset to
 * 'A', so they cannot serve as P2Rank surface atoms without breaking
 * downstream feature lookups (conservation, residue type, ASA).
 *
 * The loader does NOT load any auxiliary Protein from prediction-side
 * files; the returned Prediction is bound to the caller-supplied
 * queryProtein. See ConcavityLoader for the bug class this avoids.
 */
@Slf4j
@CompileStatic
class SwinSiteLoader extends PredictionLoader implements Parametrized {

    /** Distance from grid points used to define pocket surface atoms (A). */
    static final double SURFACE_ATOMS_CUTOFF = 4.5d
    /** Upper bound on the expanding-shell retry loop (A). */
    static final double SURFACE_ATOMS_MAX_CUTOFF = 10.0d

    /** Approximate per-voxel volume: spacing = 1 / SCALE = 1 / 0.66 ~= 1.515 A; v ~= 3.48 A^3. */
    private static final double VOXEL_VOLUME_A3 = 3.48d

    /** Permissive enough for "0", "0.5", "1.0000", "10.5234". */
    private static final Pattern GRID_FILE = ~/^grid(\d+)_score_([0-9]*\.?[0-9]+)\.mol2$/

    /** Whitespace splitter for mol2 ATOM records (token compiles once). */
    private static final Pattern WS = ~/\s+/

    /**
     * @param predictionOutputFile per-protein SwinSite output directory
     * @param queryProtein actual protein from the .ds protein column;
     *                     the Prediction is tied to this protein
     */
    @Override
    Prediction loadPrediction(String predictionOutputFile, @Nullable Protein queryProtein) {
        return new Prediction(queryProtein, loadPockets(predictionOutputFile, queryProtein))
    }

    List<SwinSitePocket> loadPockets(String pocketDir, @Nullable Protein queryProtein) {
        List<File> gridFiles = Futils.listFiles(pocketDir, { File f ->
            GRID_FILE.matcher(f.name).matches()
        })
        gridFiles.sort { File a, File b -> a.name <=> b.name }

        log.info('Found {} SwinSite grid files in {}', gridFiles.size(), pocketDir)

        List<SwinSitePocket> res = new ArrayList<>(gridFiles.size())
        for (File f : gridFiles) {
            Matcher m = GRID_FILE.matcher(f.name)
            if (!m.matches()) continue   // unreachable after pre-filter; self-documenting
            double score = Double.parseDouble(m.group(2))

            Atoms gridPoints = parseGridMol2(f)
            if (gridPoints.empty) {
                log.warn('SwinSite: empty grid file [{}], skipping', f.name)
                continue
            }

            SwinSitePocket pocket = new SwinSitePocket(gridPoints)
            pocket.score = score
            pocket.centroid = gridPoints.centroid
            pocket.stats.realVolumeApprox = gridPoints.count * VOXEL_VOLUME_A3

            if (queryProtein != null) {
                queryProtein.calcuateSurfaceAndExposedAtoms()
                double dist = SURFACE_ATOMS_CUTOFF
                while (pocket.surfaceAtoms.empty && dist < SURFACE_ATOMS_MAX_CUTOFF) {
                    pocket.surfaceAtoms = queryProtein.exposedAtoms.cutoutShell(gridPoints, dist)
                    if (pocket.surfaceAtoms.empty) {
                        log.warn('SwinSite: no surface atoms in dist={} from grid points of [{}]', dist, f.name)
                    }
                    dist += 1.0
                }
                pocket.sasPoints = queryProtein.accessibleSurface.points
                        .cutoutShell(pocket.surfaceAtoms, params.getSasCutoffDist())
            }

            res.add(pocket)
            log.info('Loaded SwinSite pocket [{}] score={} gridPoints={} surfaceAtoms={}',
                    f.name, score, gridPoints.count, pocket.surfaceAtoms.count)
        }

        // sort descending by score; stable sort preserves the deterministic
        // filename order from `gridFiles` for any score ties
        res.sort { SwinSitePocket a, SwinSitePocket b -> Double.compare(b.score, a.score) }
        int rank = 1
        for (SwinSitePocket p : res) {
            p.rank = rank
            p.name = "pocket.${rank}"
            rank++
        }

        return res
    }

    /**
     * Reads x/y/z coords from the @<TRIPOS>ATOM block of a SwinSite grid mol2.
     *
     * Mol2 ATOM line layout (whitespace-separated):
     *   serial name x y z atom_type [subst_id [subst_name [charge]]]
     *
     * Inline parser instead of CDK's Mol2Reader: CDK's reader has a
     * lazy-init race in AtomTypeFactory that NPEs under parallel
     * dataset processing. mol2 atom records are trivially regular, so
     * a small inline scan is both thread-safe and lighter than dragging
     * in cdk-io.
     */
    private static Atoms parseGridMol2(File file) {
        List<Atom> points = new ArrayList<>()
        boolean inAtomSection = false
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.US_ASCII)) {
            String line
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim()
                if (trimmed.startsWith('@<TRIPOS>')) {
                    inAtomSection = (trimmed == '@<TRIPOS>ATOM')
                    continue
                }
                if (!inAtomSection || trimmed.isEmpty()) continue
                String[] tok = WS.split(trimmed)
                if (tok.length < 5) continue
                double x = Double.parseDouble(tok[2])
                double y = Double.parseDouble(tok[3])
                double z = Double.parseDouble(tok[4])
                points.add(new Point(x, y, z))
            }
        }
        return new Atoms(points)
    }

    static class SwinSitePocket extends Pocket {
        Atoms gridPoints
        SwinSitePocket(Atoms gridPoints) { this.gridPoints = gridPoints }
    }

}
