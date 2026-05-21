package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import javax.annotation.Nullable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

/**
 * Loader for predictions produced by Seq2Pocket
 * (Skrhak et al. 2026, biorxiv 10.64898/2026.01.28.702257).
 *
 * Seq2Pocket emits a per-protein output directory containing:
 *   - <ID>_predictions.txt   semicolon-delimited CSV of ranked pockets
 *   - <ID>_residues.txt      per-residue scores (not consumed here)
 *   - <ID>_meta.json         timing/diagnostics (not consumed here)
 *
 * <ID>_predictions.txt body line (header is "name;rank;score;residue_ids;atom_ids"):
 *   pocket1;1;0.9256;A_98 A_102 ...;779 780 782 ...
 *
 * The atom_ids field holds space-separated PDB atom serial numbers identifying
 * the SAS-surface atoms within the predicted pocket cluster. The loader looks
 * those atoms up in queryProtein.allAtoms (already indexed by PDB serial via
 * withIndex()) and uses them directly as pocket surfaceAtoms; the pocket
 * centroid is the centroid of those atoms.
 *
 * The loader does NOT load any auxiliary Protein from prediction-side files;
 * the returned Prediction is bound to the caller-supplied queryProtein. See
 * ConcavityLoader for the bug class this avoids.
 */
@Slf4j
@CompileStatic
class Seq2PocketLoader extends PredictionLoader implements Parametrized {

    /** Suffix of the only file in the per-protein dir that this loader reads. */
    private static final String PREDICTIONS_SUFFIX = '_predictions.txt'

    /** CSV header (first non-empty line of <ID>_predictions.txt). */
    private static final String HEADER_PREFIX = 'name;'

    /** Whitespace splitter for atom_ids tokens. */
    private static final Pattern WS = ~/\s+/

    /**
     * @param predictionOutputFile per-protein Seq2Pocket output directory,
     *                             e.g. .../SEQ2POCKET_PREDS/coach420/1a26A
     * @param queryProtein actual protein from the .ds protein column;
     *                     the Prediction is tied to this protein
     */
    @Override
    Prediction loadPrediction(String predictionOutputFile, @Nullable Protein queryProtein) {
        return new Prediction(queryProtein, loadPockets(predictionOutputFile, queryProtein))
    }

    List<Seq2PocketPocket> loadPockets(String pocketDir, @Nullable Protein queryProtein) {
        File predFile = findPredictionsFile(pocketDir)
        if (predFile == null) {
            log.warn('Seq2Pocket: no *{} file in [{}], returning empty pockets',
                    PREDICTIONS_SUFFIX, pocketDir)
            return Collections.emptyList()
        }

        // ensure the PDB-serial -> Atom index is built; cheap if already done
        if (queryProtein != null) {
            queryProtein.allAtoms.withIndex()
        }

        int totalMissingSerials = 0
        List<Seq2PocketPocket> res = new ArrayList<>()

        try (BufferedReader reader = Files.newBufferedReader(predFile.toPath(), StandardCharsets.US_ASCII)) {
            String line
            boolean headerSeen = false
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue
                if (!headerSeen) {
                    headerSeen = true
                    if (line.startsWith(HEADER_PREFIX)) continue
                }
                String[] cols = line.split(';', -1)
                if (cols.length < 5) {
                    log.warn('Seq2Pocket: malformed line in [{}]: [{}]', predFile.name, line)
                    continue
                }
                double score = Double.parseDouble(cols[2])
                String atomIdsField = cols[4].trim()

                List<Atom> atomList = new ArrayList<>()
                int requestedSerials = 0
                if (queryProtein != null && !atomIdsField.isEmpty()) {
                    String[] tokens = WS.split(atomIdsField)
                    requestedSerials = tokens.length
                    for (String tok : tokens) {
                        int serial = Integer.parseInt(tok)
                        Atom a = queryProtein.allAtoms.getByID(serial)
                        if (a != null) {
                            atomList.add(a)
                        } else {
                            totalMissingSerials++
                        }
                    }
                }
                // skip degenerate pocket: input named atoms but none could be resolved.
                // Such a pocket would carry empty surfaceAtoms and a null centroid,
                // which would NPE downstream feature extraction.
                if (requestedSerials > 0 && atomList.isEmpty()) {
                    log.warn('Seq2Pocket: skipping pocket score={} in [{}] — none of {} atom serial(s) resolved',
                            score, predFile.name, requestedSerials)
                    continue
                }
                Atoms surfaceAtoms = new Atoms(atomList)

                Seq2PocketPocket pocket = new Seq2PocketPocket()
                pocket.score = score
                pocket.surfaceAtoms = surfaceAtoms
                pocket.centroid = surfaceAtoms.empty ? null : surfaceAtoms.centroid
                if (queryProtein != null && !surfaceAtoms.empty) {
                    queryProtein.calcuateSurfaceAndExposedAtoms()
                    pocket.sasPoints = queryProtein.accessibleSurface.points
                            .cutoutShell(surfaceAtoms, params.getSasCutoffDist())
                }
                res.add(pocket)
            }
        }

        if (totalMissingSerials > 0) {
            log.warn('Seq2Pocket: {} atom serial(s) in [{}] not found in queryProtein.allAtoms (skipped)',
                    totalMissingSerials, predFile.name)
        }

        // sort descending by score; input is expected to be pre-sorted but we
        // sort defensively for uniformity with SwinSiteLoader
        res.sort { Seq2PocketPocket a, Seq2PocketPocket b -> Double.compare(b.score, a.score) }
        int rank = 1
        for (Seq2PocketPocket p : res) {
            p.rank = rank
            p.name = "pocket.${rank}"
            rank++
        }

        log.info('Loaded {} Seq2Pocket pockets from [{}]', res.size(), predFile.name)
        return res
    }

    private static File findPredictionsFile(String pocketDir) {
        if (!new File(pocketDir).isDirectory()) return null
        List<File> matches = Futils.listFiles(pocketDir, { File f ->
            f.name.endsWith(PREDICTIONS_SUFFIX)
        })
        return matches.empty ? null : matches[0]
    }

    /** Marker subclass; carries no extra state — exists only because {@link Pocket} is abstract. */
    static class Seq2PocketPocket extends Pocket {}

}
