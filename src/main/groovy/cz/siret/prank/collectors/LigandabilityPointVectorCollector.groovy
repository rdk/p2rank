package cz.siret.prank.collectors

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.samplers.SampledPoints
import cz.siret.prank.prediction.pockets.criteria.DCA
import cz.siret.prank.prediction.pockets.criteria.PocketCriterium
import cz.siret.prank.program.Failable
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
class LigandabilityPointVectorCollector extends VectorCollector implements Parametrized, Failable {

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

        if (params.positive_def_ligtypes.contains("relevant")) pair.ligands.relevantLigands*.atoms.each { res.addAll(it) }
        if (params.positive_def_ligtypes.contains("ignored"))  pair.ligands.ignoredLigands*.atoms.each { res.addAll(it) }
        if (params.positive_def_ligtypes.contains("small"))    pair.ligands.smallLigands*.atoms.each { res.addAll(it) }
        if (params.positive_def_ligtypes.contains("distant"))  pair.ligands.distantLigands*.atoms.each { res.addAll(it) }

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

//===========================================================================================================//

    static class Points {
        Atoms positives
        Atoms negatives

        Points(Atoms positives, Atoms negatives) {
            this.positives = positives
            this.negatives = negatives
        }

        Points(int positivesCapacity, int negativesCapacity) {
            this.positives = new Atoms(positivesCapacity)
            this.negatives = new Atoms(negativesCapacity)
        }
    }

    private enum PointClass { POSITIVE, NEGATIVE, IGNORE }

    /**
     *
     * @param sampledPositives may be the same object as sampledNegatives
     * @param sampledNegatives
     * @param classifier
     * @return
     */
    Points selectPoints(SampledPoints points, PointClassifier classifier) {
        Points res
        if (points.posNegDifferent) {
            res = new Points(points.sampledPositives.size(), points.sampledNegatives.size())

            for (Atom point : points.sampledPositives) {
                if (classifier.classify(point) == PointClass.POSITIVE) {
                    res.positives.add(point)
                }
            }
            for (Atom point : points.sampledNegatives) {
                if (classifier.classify(point) == PointClass.NEGATIVE) {
                    res.negatives.add(point)
                }
            }
        } else {
            res = new Points(points.points.size()/20 as int, points.points.size())

            for (Atom point : points.points) {
                PointClass pc = classifier.classify(point)
                if (pc == PointClass.POSITIVE) {
                    res.positives.add(point)
                } else if (pc == PointClass.NEGATIVE) {
                    res.negatives.add(point)
                }
            }
        }
        return res
    }

    static interface PointClassifier {
        PointClass classify(Atom point)        
    }

    void addCalculatedVectors(Result res, boolean positive, Atoms points, FeatureExtractor extractor) {
        for (Atom point : points) {
            try {
                FeatureVector vect = extractor.calcFeatureVector(point)
                res.addBinary(vect.array, positive)
            } catch (Exception e) {
                fail("failed extraction for point", e, log)
            }
        }
    }

    Result collectVectorsForPoints(Points points, FeatureExtractor extractor) {
        Result res = new Result()
        addCalculatedVectors(res, true,  points.positives, extractor)
        addCalculatedVectors(res, false, points.negatives, extractor)
        return res
    }

//===========================================================================================================//

    @CompileStatic
    Result collectWholeSurface(Atoms ligandAtoms, FeatureExtractor proteinExtractorPrototype) {

        FeatureExtractor proteinExtractor = (proteinExtractorPrototype as PrankFeatureExtractor).createInstanceForWholeProtein()
        SampledPoints points = proteinExtractor.sampledPoints

        if (params.train_lig_cutoff > 0) {
            points = points.cutoutShell(ligandAtoms, params.train_lig_cutoff)
        }

        Points selected = selectPoints(points, new PointClassifier() {
            @Override
            PointClass classify(Atom point) {
                double closestLigandDistance = ligandAtoms.dist(point)
                if (closestLigandDistance > NEGATIVES_DIST) {
                    return PointClass.NEGATIVE
                } else if (closestLigandDistance <= POSITIVE_VC_LIGAND_DISTANCE) {
                    return PointClass.POSITIVE
                } else {
                    return PointClass.IGNORE
                }
            }
        })

        return collectVectorsForPoints(selected, proteinExtractor)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    Result collectForPockets(Atoms ligandAtoms, PredictionPair pair, FeatureExtractor proteinExtractorPrototype) {

        proteinExtractorPrototype.prepareProteinPrototypeForPockets()

        List<Pocket> usePockets = pair.prediction.pockets  // use all pockets
        if (params.train_pockets > 0) {
            usePockets = [*pair.getCorrectlyPredictedPockets(positivePocketCriterium), *Cutils.head(params.train_pockets, pair.getFalsePositivePockets(positivePocketCriterium)) ]
        }

        Result result = new Result()
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
        final boolean ligPocket = pair.isCorrectlyPredictedPocket(pocket, positivePocketCriterium)

        Points selected = selectPoints(pocketExtractor.sampledPoints, new PointClassifier() {
            @Override
            PointClass classify(Atom point) {
                double closestLigandDistance = ligandAtoms.dist(point)
                if (closestLigandDistance > 100) {
                    closestLigandDistance = 100
                }
                
                if (closestLigandDistance <= POSITIVE_VC_LIGAND_DISTANCE) {
                    return PointClass.POSITIVE
                } else if (!ligPocket && closestLigandDistance > NEGATIVES_DIST) {  // GAP ... helps
                    // so we are skipping points in gap and negative points in positive pockets
                    return PointClass.NEGATIVE
                } else {
                    return PointClass.IGNORE
                }
            }
        })

        return collectVectorsForPoints(selected, pocketExtractor)
    }

//===========================================================================================================//

    @Override
    List<String> getHeader() {
        return extractorFactory.vectorHeader + "is_liganated_point"
    }

}
