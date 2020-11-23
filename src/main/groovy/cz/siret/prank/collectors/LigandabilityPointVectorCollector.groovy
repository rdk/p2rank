package cz.siret.prank.collectors

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.pockets.criteria.DCA
import cz.siret.prank.prediction.pockets.criteria.PocketCriterium
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Cutils
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * extracts vectors for sampled points in predicted pockets
 * judging correct by distance to the closest ligand atom
 */
@Slf4j
@CompileStatic
class LigandabilityPointVectorCollector extends VectorCollector implements Parametrized {

    /** Criterion for calling true positive / false positive pockets */
    static final PocketCriterium DEFAULT_POSITIVE_POCKET_CRITERIUM = new DCA(5)

    /** distance from the point to the ligand that identifies positive point */
    final double POSITIVE_VC_LIGAND_DISTANCE = params.positive_point_ligand_distance
    final double NEGATIVES_DIST = params.positive_point_ligand_distance + params.neutral_points_margin

    final FeatureExtractor extractorFactory
    final PocketCriterium positivePocketCriterium = DEFAULT_POSITIVE_POCKET_CRITERIUM

    LigandabilityPointVectorCollector(FeatureExtractor extractorFactory) {
        this.extractorFactory = extractorFactory
    }

    Atoms getTrainingRelevantLigandAtoms(PredictionPair pair) {
        Atoms res = new Atoms()

        if (params.positive_def_ligtypes.contains("relevant")) pair.protein.ligands*.atoms.each { res.addAll(it) }
        if (params.positive_def_ligtypes.contains("ignored"))  pair.protein.ignoredLigands*.atoms.each { res.addAll(it) }
        if (params.positive_def_ligtypes.contains("small"))    pair.protein.smallLigands*.atoms.each { res.addAll(it) }
        if (params.positive_def_ligtypes.contains("distant"))  pair.protein.distantLigands*.atoms.each { res.addAll(it) }

        return res
    }

    @Override
    Result collectVectors(PredictionPair pair, ProcessedItemContext context) {
        FeatureExtractor proteinExtractorPrototype = extractorFactory.createPrototypeForProtein(pair.prediction.protein, context)

        try {
            return doCollectVectors(pair, proteinExtractorPrototype)
        } finally {
            proteinExtractorPrototype.finalizeProteinPrototype()
        }
    }

    private Result doCollectVectors(PredictionPair pair, FeatureExtractor proteinExtractorPrototype) {
        Atoms ligandAtoms = getTrainingRelevantLigandAtoms(pair)

        if (ligandAtoms.empty) {
            log.error "Protein has no relevant ligands - all SAS points will be negative [{}]", pair.protein.name
        }

        if (params.sample_negatives_from_decoys) {
            return collectForPockets(ligandAtoms, pair, proteinExtractorPrototype)
        } else {
            return collectWholeSurface(ligandAtoms, proteinExtractorPrototype)
        }
    }


    @CompileStatic
    Result collectWholeSurface(Atoms ligandAtoms, FeatureExtractor proteinExtractorPrototype) {

        FeatureExtractor proteinExtractor = (proteinExtractorPrototype as PrankFeatureExtractor).createInstanceForWholeProtein()
        Result res = new Result()

        Atoms points = proteinExtractor.sampledPoints

        if (params.train_lig_cutoff > 0) {
            points = points.cutoutShell(ligandAtoms, params.train_lig_cutoff)
        }

        for (Atom point in points) {        // TODO lot of repeated code with next method... refactor!

            try {
                double closestLigandDistance = ligandAtoms.dist(point)
                boolean ligPoint = (closestLigandDistance <= POSITIVE_VC_LIGAND_DISTANCE)
                boolean negPoint = (closestLigandDistance > NEGATIVES_DIST)
                // points in between are left out from training

                if (ligPoint || negPoint) {
                    double clazz = ligPoint ? 1d : 0d

                    FeatureVector vect = proteinExtractor.calcFeatureVector(point)
                    res.add(vect.array, clazz)

                    if (ligPoint) {
                        res.positives++
                    } else {
                        res.negatives++
                    }
                }

            } catch (Exception e) {
                if (params.fail_fast) {
                    throw new PrankException("failed extraction for point", e)
                } else {
                    log.error("skipping extraction for point", e)
                }
            }
        }

        return res
    }


    @CompileStatic(TypeCheckingMode.SKIP)
    Result collectForPockets(Atoms ligandAtoms, PredictionPair pair, FeatureExtractor proteinExtractorPrototype) {
        Result result = new Result()
        proteinExtractorPrototype.prepareProteinPrototypeForPockets()

        List<Pocket> usePockets = pair.prediction.pockets  // use all pockets
        if (params.train_pockets > 0) {
            usePockets = [*pair.getCorrectlyPredictedPockets(positivePocketCriterium), *Cutils.head(params.train_pockets, pair.getFalsePositivePockets(positivePocketCriterium)) ]
        }

        for (Pocket pocket in usePockets) {
            try {
                FeatureExtractor pocketExtractor = proteinExtractorPrototype.createInstanceForPocket(pocket)
                Result pocketRes = collectForPocket(pocket, pair, ligandAtoms, pocketExtractor)
                //synchronized (result) {
                result.addAll(pocketRes)
                //}
            } catch (Exception e) {
                log.error("skipping extraction from pocket:$pocket.name reason: " + e.message, e)
            }
        }

        return result
    }

    @CompileStatic
    private Result collectForPocket(Pocket pocket, PredictionPair pair, Atoms ligandAtoms, FeatureExtractor pocketExtractor) {
        boolean ligPocket = pair.isCorrectlyPredictedPocket(pocket, positivePocketCriterium)

        Result res = new Result()

        for (Atom point in pocketExtractor.sampledPoints) {

            double closestLigandDistance = ligandAtoms.dist(point)
            if (closestLigandDistance > 100) closestLigandDistance = 100

            boolean ligPoint = (closestLigandDistance <= POSITIVE_VC_LIGAND_DISTANCE)

            boolean includePoint = false
            double clazz = ligPoint ? 1d : 0d

            if (ligPoint) {
                res.positives++
                includePoint = true
            //} else {
            } else if (!ligPocket && !ligPoint && closestLigandDistance > NEGATIVES_DIST) {  // GAP ... helps
                // so we are skipping points in gap and negative points in positive pockets
                res.negatives++
                includePoint = true
            }

            if (includePoint) {
                FeatureVector vect = pocketExtractor.calcFeatureVector(point)
                res.add(vect.array, clazz)
                //log.trace "TRAIN VECT: " + vect
            }
        }

        return res
    }

//===========================================================================================================//

   // private addVectorsFor

   // TODO

//===========================================================================================================//

    @Override
    List<String> getHeader() {
        return extractorFactory.vectorHeader + "is_liganated_point"
    }

}
