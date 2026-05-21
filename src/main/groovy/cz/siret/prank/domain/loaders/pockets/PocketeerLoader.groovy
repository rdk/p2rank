package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import javax.annotation.Nullable

/**
 * Loader for predictions produced by Pocketeer.
 * Parses pockets.json containing alpha-sphere-based pocket predictions.
 */
@Slf4j
@CompileStatic
class PocketeerLoader extends PredictionLoader implements Parametrized {

    @Override
    Prediction loadPrediction(String predictionOutputFile, @Nullable Protein queryProtein) {
        return new Prediction(queryProtein, loadPockets(predictionOutputFile, queryProtein))
    }

    List<PocketeerPocket> loadPockets(String predictionOutputFile, @Nullable Protein queryProtein) {
        String json = Futils.readFile(predictionOutputFile)
        List<Map> pocketMaps = (List<Map>) Sutils.GSON.fromJson(json, List.class)

        log.info('Loading {} pockets from {}', pocketMaps.size(), predictionOutputFile)

        List<PocketeerPocket> pockets = new ArrayList<>(pocketMaps.size())

        for (Map pocketMap : pocketMaps) {
            PocketeerPocket pocket = parsePocket(pocketMap, queryProtein)
            pockets.add(pocket)
        }

        // sort by score descending
        pockets.sort { PocketeerPocket a, PocketeerPocket b -> b.score <=> a.score }

        // assign ranks
        int rank = 1
        for (PocketeerPocket pocket : pockets) {
            pocket.rank = rank
            pocket.name = "pocket.${rank}"
            rank++
        }

        return pockets
    }

    private PocketeerPocket parsePocket(Map pocketMap, @Nullable Protein queryProtein) {
        PocketeerPocket pocket = new PocketeerPocket()

        pocket.pocketId = ((Number) pocketMap.get('pocket_id')).intValue()
        pocket.score = ((Number) pocketMap.get('score')).doubleValue()
        pocket.volume = ((Number) pocketMap.get('volume')).doubleValue()
        pocket.nSpheres = ((Number) pocketMap.get('n_spheres')).intValue()
        pocket.nResidues = ((Number) pocketMap.get('n_residues')).intValue()
        pocket.stats.realVolumeApprox = pocket.volume

        // parse centroid
        List<Number> centroidCoords = (List<Number>) pocketMap.get('centroid')
        pocket.centroid = new Point(
                centroidCoords.get(0).doubleValue(),
                centroidCoords.get(1).doubleValue(),
                centroidCoords.get(2).doubleValue()
        )

        // parse alpha spheres
        List<Map> sphereMaps = (List<Map>) pocketMap.get('spheres')
        pocket.alphaSpheres = new ArrayList<>(sphereMaps.size())
        List<Atom> sphereCenterList = new ArrayList<>(sphereMaps.size())

        Set<Integer> allAtomIds = new LinkedHashSet<>()

        for (Map sphereMap : sphereMaps) {
            AlphaSphere sphere = parseSphere(sphereMap)
            pocket.alphaSpheres.add(sphere)
            sphereCenterList.add(sphere.center)
            allAtomIds.addAll(sphere.atomIndices)
        }

        pocket.sphereCenters = new Atoms(sphereCenterList)

        // parse residues
        List<Map> residueMaps = (List<Map>) pocketMap.get('residues')
        pocket.pocketeerResidues = new ArrayList<>(residueMaps.size())
        for (Map residueMap : residueMaps) {
            pocket.pocketeerResidues.add(parseResidue(residueMap))
        }

        // derive surfaceAtoms from atom_indices (0-based indices into protein atom list)
        if (queryProtein != null) {
            List<Atom> proteinAtomList = queryProtein.allAtoms.list
            int atomCount = proteinAtomList.size()
            Atoms surfaceAtoms = new Atoms()
            for (int atomIdx : allAtomIds) {
                if (atomIdx >= 0 && atomIdx < atomCount) {
                    surfaceAtoms.add(proteinAtomList.get(atomIdx))
                } else {
                    log.warn('Atom index {} out of range (0-{}) in protein {}', atomIdx, atomCount - 1, queryProtein.name)
                }
            }
            pocket.surfaceAtoms = surfaceAtoms
            if (!surfaceAtoms.empty) {
                queryProtein.calcuateSurfaceAndExposedAtoms()
                pocket.sasPoints = queryProtein.accessibleSurface.points
                        .cutoutShell(surfaceAtoms, params.getSasCutoffDist())
            }
        }

        return pocket
    }

    private static AlphaSphere parseSphere(Map sphereMap) {
        AlphaSphere sphere = new AlphaSphere()
        sphere.sphereId = ((Number) sphereMap.get('sphere_id')).intValue()
        sphere.radius = ((Number) sphereMap.get('radius')).doubleValue()
        sphere.meanSasa = ((Number) sphereMap.get('mean_sasa')).doubleValue()

        List<Number> centerCoords = (List<Number>) sphereMap.get('center')
        sphere.center = new Point(
                centerCoords.get(0).doubleValue(),
                centerCoords.get(1).doubleValue(),
                centerCoords.get(2).doubleValue()
        )

        List<Number> indices = (List<Number>) sphereMap.get('atom_indices')
        sphere.atomIndices = new ArrayList<>(indices.size())
        for (Number idx : indices) {
            sphere.atomIndices.add(idx.intValue())
        }

        return sphere
    }

    private static PocketeerResidue parseResidue(Map residueMap) {
        PocketeerResidue residue = new PocketeerResidue()
        residue.chainId = (String) residueMap.get('chain_id')
        residue.resId = ((Number) residueMap.get('res_id')).intValue()
        residue.resName = (String) residueMap.get('res_name')
        return residue
    }

    // --- Data classes ---

    static class PocketeerPocket extends Pocket {
        int pocketId
        List<AlphaSphere> alphaSpheres
        Atoms sphereCenters
        double volume
        List<PocketeerResidue> pocketeerResidues
        int nSpheres
        int nResidues
    }

    static class AlphaSphere {
        int sphereId
        Point center
        double radius
        double meanSasa
        List<Integer> atomIndices
    }

    static class PocketeerResidue {
        String chainId
        int resId
        String resName
    }

}
