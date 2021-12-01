package cz.siret.prank.features

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.generic.GenericHeader
import cz.siret.prank.features.implementation.chem.ChemFeature
import cz.siret.prank.features.implementation.table.AtomTableFeature
import cz.siret.prank.features.implementation.table.ResidueTableFeature
import cz.siret.prank.features.weight.WeightFun
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.geom.samplers.PointSampler
import cz.siret.prank.geom.samplers.SampledPoints
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static java.lang.Math.max

/**
 * Handles the process of calculating prank feature vectors.
 * At first it calculates features for solvent exposed atoms of the protein.
 * Then it projects them onto SAS points and calculates additional features for SAS point vector.
 */
@Slf4j
@CompileStatic
class PrankFeatureExtractor extends FeatureExtractor<PrankFeatureVector> implements Parametrized {

    /**
     * Setup of enabled and filtered features for given run
     */
    FeatureSetup featureSetup

    /**
     * Header of calculated feature vector
     * (before filtering)
     */
    GenericHeader calculatedFeatureVectorHeader

    /**
     * Header of feature vector hat is returned -- after feature_filters are applied
     * If there are no feature_filters it is the same as calculatedFeatureVectorHeader.
     */
    GenericHeader finalFeatureVectorHeader

    Atoms surfaceLayerAtoms

    /**
     * Feature vectors that are first calculated for atoms and then (projected to SAS points)
     */
    private Map<Integer, PrankFeatureVector> surfaceAtomVectors = new HashMap<>()

    /**
     * deep layer of atoms under the protein surface
     * serves as cache for speeding up calculation of protrusion and some other features
     */
    Atoms deepLayer

    /**
     * SAS point for given protein (or pocket in pocket mode) for which we calculate feature vectors
     */
    SampledPoints sampledPoints

    /**
     * only used in pocket mode (i.e. sample_negatives_from_decoys=true)  tied to a protein
     */
    private PointSampler pocketPointSampler

    /**
     * only used in pocket mode (i.e. sample_negatives_from_decoys=true)
     */
    Pocket pocket

    //===================
    // params
    //===================

    private double NEIGH_CUTOFF_DIST = params.neighbourhood_radius
    private final boolean AVERAGE_FEAT_VECTORS = params.average_feat_vectors
    private final boolean AVG_WEIGHTED = params.avg_weighted
    private final boolean CHECK_VECTORS = params.check_vectors
    private final double AVG_POW = params.avg_pow
    private final WeightFun weightFun = WeightFun.create(params.weight_function)

//===========================================================================================================//

    PrankFeatureExtractor() {
        initHeader()
    }

    private PrankFeatureExtractor(Protein protein) {
        super(protein)

        initHeader()
    }

//===========================================================================================================//

    /**
     * header of final feature vector used to train model
     */
    @Override
    List<String> getVectorHeader() {
        return finalFeatureVectorHeader.colNames
    }

    @Override
    SampledPoints getSampledPoints() {
        return sampledPoints
    }
    
//===========================================================================================================//

    private void initHeader() {
        List<String> enabledFeatures = new ArrayList<>(params.selectedFeatures)

        // add implicit table features
        if (!enabledFeatures.contains(AtomTableFeature.NAME)) {
            if (!params.atom_table_features.empty) {
                enabledFeatures.add(AtomTableFeature.NAME)
            }
        }
        if (!enabledFeatures.contains(ResidueTableFeature.NAME)) {
            if (!params.residue_table_features.empty) {
                enabledFeatures.add(ResidueTableFeature.NAME)
            }
        }

        featureSetup = new FeatureSetup(enabledFeatures, params.feature_filters)
        calculatedFeatureVectorHeader = new GenericHeader(featureSetup.subFeaturesHeader)
        finalFeatureVectorHeader = calculatedFeatureVectorHeader
        if (featureSetup.filteringEnabled) {
            finalFeatureVectorHeader = new GenericHeader(featureSetup.filteredSubFeaturesHeader)
        }
        
    }

    @Override
    FeatureExtractor createPrototypeForProtein(Protein protein, ProcessedItemContext context) {
        PrankFeatureExtractor res = new PrankFeatureExtractor(protein)
        res.forTraining = this.forTraining

        protein.calcuateSurfaceAndExposedAtoms()
        double thickness = max(params.protrusion_radius, params.pair_hist_radius)
        res.deepLayer = protein.proteinAtoms.cutoutShell(protein.exposedAtoms, thickness).buildKdTree()

        // init features
        for (FeatureSetup.Feature feature : featureSetup.enabledFeatures) {
            feature.calculator.preProcessProtein(protein, context)
        }

        return res
    }

    @Override
    void prepareProteinPrototypeForPockets() {
        pocketPointSampler = PointSampler.create(protein, forTraining)

        if (params.deep_surrounding) {
            surfaceLayerAtoms = deepLayer
        } else {
            surfaceLayerAtoms = protein.exposedAtoms
        }

        log.debug "surfaceLayerAtoms: $surfaceLayerAtoms.count"

        preCalculateVectorsForAtoms(surfaceLayerAtoms)
    }

    /**
     * Create extractor for pocket.
     * @param protein
     * @param pocket nay be null if it is an instance for whole protein
     * @param proteinPrototype
     */
    private PrankFeatureExtractor(Protein protein, Pocket pocket, PrankFeatureExtractor proteinPrototype) {
        this.protein = protein
        this.pocket = pocket

        this.calculatedFeatureVectorHeader = proteinPrototype.calculatedFeatureVectorHeader
        this.finalFeatureVectorHeader      = proteinPrototype.finalFeatureVectorHeader
        this.pocketPointSampler            = proteinPrototype.pocketPointSampler
        this.forTraining                   = proteinPrototype.forTraining
        this.featureSetup                  = proteinPrototype.featureSetup

        this.deepLayer          = proteinPrototype.deepLayer
        this.surfaceLayerAtoms  = proteinPrototype.surfaceLayerAtoms
        this.surfaceAtomVectors = proteinPrototype.surfaceAtomVectors
    }

    @Override
    FeatureExtractor createInstanceForPocket(Pocket pocket) {
        PrankFeatureExtractor res = new PrankFeatureExtractor(protein, pocket, this)

        if (pocket.surfaceAtoms.count==0) {
            log.error "pocket with no surface atoms! [$protein.name]"
        }
        res.initForPocket()

        return res
    }

    private void initForPocket() {
        log.debug "extractorFactory initForPocket for pocket $pocket.name"
        log.debug "surfaceLayerAtoms: $surfaceLayerAtoms.count (pocketSurfaceAtoms: $pocket.surfaceAtoms.count) "

        sampledPoints = pocketPointSampler.samplePointsForPocket(pocket)

        log.debug "$pocket.name - points sampled - pos:$sampledPoints.sampledPositives.count neg:$sampledPoints.sampledNegatives.count"
    }

    /**
     * Used for predictions (P2RANK)
     * @param sampledPoints optionally provided points, otherwise calculated from protein
     * @return
     */
    FeatureExtractor createInstanceForWholeProtein(Atoms sampledPoints = null) {
        PrankFeatureExtractor res = new PrankFeatureExtractor(protein, null, this)

        // init for whole protein
        protein.calcuateSurfaceAndExposedAtoms()
        res.surfaceLayerAtoms = protein.exposedAtoms

        if (params.point_sampling_strategy != "surface") {
            res.surfaceLayerAtoms = protein.proteinAtoms
        }

        res.preCalculateVectorsForAtoms(res.surfaceLayerAtoms)

        if (sampledPoints == null) {
            res.sampledPoints = SampledPoints.fromProtein(protein, forTraining, params)
        } else {
            res.sampledPoints = new SampledPoints(sampledPoints)
        }

        log.debug "proteinAtoms:$protein.proteinAtoms.count  exposedAtoms:$res.surfaceLayerAtoms.count  deepLayer:$res.deepLayer.count sasPoints:$res.sampledPoints.points.count"

        return res
    }

    /**
     * finalize feature calculators and conditionally clear secondary data
     */
    @Override
    void finalizeProteinPrototype() {
        // finalize features
        for (FeatureSetup.Feature feature : featureSetup.enabledFeatures) {
            try {
                feature.calculator.postProcessProtein(protein)
            } catch (Exception e) {
                log.error("Failed to finalize feature ${feature.name}", e)
            }
        }
        if (params.clear_sec_caches) {
            protein.secondaryData.clear() // clear caches immediately after feature extraction
        }
    }

//===========================================================================================================//

    void preCalculateVectorsForAtoms(Atoms atoms) {
        log.debug "pre-calculating vectors for {} atoms", atoms.count
        
        for (Atom a : atoms.list) {
            FeatureVector p = calcAtomVector(a)
            surfaceAtomVectors.put(a.PDBserial, p)
        }
    }

//===========================================================================================================//

    /**
     *
     * @param point SAS point
     * @param neighbourhoodAtoms neighbourhood protein atoms
     * @param fromVectors feature vectors of neighbouring atoms,  must match atoms
     * @return
     */
    private PrankFeatureVector calcSasFeatVectorFromAtomVectors(Atom point, Atoms neighbourhoodAtoms, Map<Integer, PrankFeatureVector> fromVectors) {
        PrankFeatureVector res = new PrankFeatureVector(calculatedFeatureVectorHeader)

        // aggregate vectors from neighbourhood atoms

        if (neighbourhoodAtoms.isEmpty()) {
            log.warn ("No neighbourhood atoms. Cannot calculate feature vector. (Isn't neighbourhood_radius too small?)")
        }

        int n = neighbourhoodAtoms.count
        double weightSum = 0

        for (Atom a : neighbourhoodAtoms) {
            PrankFeatureVector props = (PrankFeatureVector) fromVectors.get(a.PDBserial)

            if (props == null) {
                throw new PrankException("Feature vector for atom $a.PDBserial was not pre-calculated. This shouldn't happen.")
            }

            double dist = Struct.dist(point, a)
            double weight = calcWeight(dist)
            weightSum += weight

            res.add( props.copy().multiply(weight) )
        }

        if (AVERAGE_FEAT_VECTORS) {
            double base = n
            if (AVG_WEIGHTED) {
                base = weightSum
            }

            double multip = Math.pow(base, AVG_POW)  // for AVG_POW from <0,1> goes from 'no average, just sum' -> 'full average'
            if (Double.isNaN(multip) || Double.isInfinite(multip)) {
                multip = 1.0
            }
            
            res.multiply(1d/multip)                  // avg

            // special cases (TODO: move to ChemFeature)
            if (featureSetup.enabledFeatureNames.contains(ChemFeature.NAME)) {
                res.valueVector.multiply('chem.atomDensity', multip)
                res.valueVector.multiply('chem.hDonorAtoms', multip)
                res.valueVector.multiply('chem.hAcceptorAtoms', multip)
            }

        }
        // special cases (TODO: move to ChemFeature)
        if (featureSetup.enabledFeatureNames.contains(ChemFeature.NAME)) {
            res.valueVector.set('chem.atoms', n)
        }

        // calculate SAS features

        SasFeatureCalculationContext context = new SasFeatureCalculationContext(protein, neighbourhoodAtoms, this)
        for (FeatureSetup.Feature feature : featureSetup.enabledSasFeatures) {
            try {
                double[] values = feature.calculator.calculateForSasPoint(point, context)
                feature.checkCorrectLength(values)
                res.valueVector.setValues(feature.startIndex, values)
            } catch (Exception e) {
                throw new PrankException("Failed to calculate feature " + feature.name, e)
            }
        }

        return res
    }

    /**
     *
     * @param point SAS point
     * @param useSmoothRepresentations or use basic properties for first run
     * @param neighbourhoodAtoms
     * @param store
     * @return
     */
    private PrankFeatureVector calcFeatureVectorForPoint(Atom point, Atoms neighbourhoodAtoms) {
        Map<Integer, PrankFeatureVector> fromVectors

        fromVectors = surfaceAtomVectors

        if (fromVectors==null || fromVectors.isEmpty()) {
            log.error "!!! can't calculate representation from no vectors"
        }

        return calcSasFeatVectorFromAtomVectors(point, neighbourhoodAtoms, fromVectors)
    }

    private double calcWeight(double dist) {
        return weightFun.weight(dist)
    }

//===========================================================================================================//

    /**
     * @return feature vector for individual atom
     */
    private PrankFeatureVector calcAtomVector(Atom atom) {
        return PrankFeatureVector.forAtom(atom, this)
    }

    /**
     * @param point SAS point
     * @return
     */
    @Override
    PrankFeatureVector calcFeatureVector(Atom point) {

        Atoms neighbourhood = surfaceLayerAtoms.cutoutSphere(point, NEIGH_CUTOFF_DIST)

        PrankFeatureVector vector = calcFeatureVectorForPoint(point, neighbourhood)

        if (featureSetup.filteringEnabled) {
            vector = reduceToFilteredVector(vector)
        }

        if (CHECK_VECTORS) {
            checkVector(vector)
        }

        return vector
    }

    private PrankFeatureVector reduceToFilteredVector(PrankFeatureVector vector) {
        PrankFeatureVector res = new PrankFeatureVector(finalFeatureVectorHeader)

        double[] calculated = vector.array
        double[] filtered = res.array

        int i = 0
        for (FeatureSetup.SubFeature subFeature : featureSetup.filteredSubFeatures) {
            filtered[i++] = calculated[subFeature.oldIdx]
        }

        return res
    }

    private checkVector(PrankFeatureVector vector) {
        double[] arr = vector.array
        for (int i=0; i!=arr.length; i++) {
            if (Double.isNaN(arr[i])) {
                String feat = vector.header[i]
                throw new PrankException("Invalid value for feature $feat: NaN")
            }
        }
    }

}
