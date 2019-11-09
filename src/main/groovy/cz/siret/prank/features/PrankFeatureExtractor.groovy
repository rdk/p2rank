package cz.siret.prank.features

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.generic.GenericHeader
import cz.siret.prank.features.implementation.chem.ChemFeature
import cz.siret.prank.features.weight.WeightFun
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.geom.samplers.PointSampler
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static java.lang.Math.max

/**
 * Handles the process of calculating prank feature vectors.
 * At first it calculates features for solvent exposed atoms of the protein.
 * Then it projects them onto ASA points and calculates additional features for ASA point vector.
 */
@Slf4j
@CompileStatic
class PrankFeatureExtractor extends FeatureExtractor<PrankFeatureVector> implements Parametrized {

    /** properties of atom itself */
    private Map<Integer, FeatureVector> properties = new HashMap<>()
    /** properties calculated from neighbourhood */
    private Map<Integer, FeatureVector> smoothRepresentations = new HashMap<>()

    GenericHeader headerAdditionalFeatures // header of additional generic vector
    List<String> extraFeaturesHeader
    List<String> atomTableFeatures
    List<String> residueTableFeatures

    // tied to a protein
    private PointSampler pocketPointSampler

    private double NEIGH_CUTOFF_DIST = params.neighbourhood_radius
    private boolean DO_SMOOTH_REPRESENTATION = params.smooth_representation
    private double SMOOTHING_CUTOFF_DIST = params.smoothing_radius
    private final boolean AVERAGE_FEAT_VECTORS = params.average_feat_vectors
    private final boolean AVG_WEIGHTED = params.avg_weighted
    private final boolean CHECK_VECTORS = params.check_vectors

    private final WeightFun weightFun = WeightFun.create(params.weight_function)

    // pocket related
    Pocket pocket
    Atoms surfaceLayerAtoms

    /**
     * deep layer of atums unter the protein surface
     * serves as cache for speeding up calculation of protrusion and other features
     */
    Atoms deepLayer
    Atoms sampledPoints

    FeatureSetup featureSetup

//===========================================================================================================//

    public PrankFeatureExtractor() {
        initHeader()
    }

    private PrankFeatureExtractor(Protein protein) {
        super(protein)

        initHeader()
    }

    private void initHeader() {
        featureSetup = new FeatureSetup(params.extra_features)

        extraFeaturesHeader = featureSetup.jointHeader
        atomTableFeatures = params.atom_table_features // ,"apRawInvalids","ap5sasaValids","ap5sasaInvalids"
        residueTableFeatures = params.residue_table_features

        headerAdditionalFeatures = new GenericHeader([
                *extraFeaturesHeader,
                *atomTableFeatures,
                *residueTableFeatures,
        ] as List<String>)

    }

    @Override
    FeatureExtractor createPrototypeForProtein(Protein protein, ProcessedItemContext context) {
        PrankFeatureExtractor res = new PrankFeatureExtractor(protein)
        res.trainingExtractor = this.trainingExtractor

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
        pocketPointSampler = PointSampler.create(protein, trainingExtractor)

        if (params.deep_surrounding) {
            surfaceLayerAtoms = deepLayer
        } else {
            // surfaceLayerAtoms = protein.proteinAtoms.cutoutSphere(pocket.surfaceAtoms, 1)  // shallow
            //surfaceLayerAtoms = protein.exposedAtoms.cutoutShell(pocket.surfaceAtoms, 6) //XXX
            surfaceLayerAtoms = protein.exposedAtoms
        }

        log.debug "surfaceLayerAtoms:$surfaceLayerAtoms.count (surfaceAtoms: ${pocket?.surfaceAtoms?.count}) "

        preEvaluateProperties(surfaceLayerAtoms)
        if (DO_SMOOTH_REPRESENTATION) {
            preEvaluateSmoothRepresentations(surfaceLayerAtoms)
        }
    }

    /**
     *
     * @param protein
     * @param pocket nay be null if it is an instance for whole protein
     * @param proteinPrototype
     */
    private PrankFeatureExtractor(Protein protein, Pocket pocket, PrankFeatureExtractor proteinPrototype) {
        this.protein = protein
        this.pocket = pocket

        this.headerAdditionalFeatures = proteinPrototype.headerAdditionalFeatures
        this.pocketPointSampler    = proteinPrototype.pocketPointSampler
        this.extraFeaturesHeader   = proteinPrototype.extraFeaturesHeader
        this.atomTableFeatures     = proteinPrototype.atomTableFeatures
        this.residueTableFeatures  = proteinPrototype.residueTableFeatures
        this.trainingExtractor     = proteinPrototype.trainingExtractor
        this.featureSetup     = proteinPrototype.featureSetup

        this.deepLayer = proteinPrototype.deepLayer
        this.surfaceLayerAtoms = proteinPrototype.surfaceLayerAtoms
        this.properties = proteinPrototype.properties
        this.smoothRepresentations = proteinPrototype.smoothRepresentations
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

        //surfaceLayerAtoms = surfaceLayerAtoms.cutoutShell(pocket.surfaceAtoms, 6) //XXX
        log.debug "surfaceLayerAtoms:$surfaceLayerAtoms.count (surfaceAtoms: $pocket.surfaceAtoms.count) "

        sampledPoints = pocketPointSampler.samplePointsForPocket(pocket)

        log.debug "$pocket.name - points sampled: $sampledPoints.count"
    }

    /**
     * Used for predictions (P2RANK)
     * @return
     */
    FeatureExtractor createInstanceForWholeProtein(Atoms sampledPoints = null) {
        PrankFeatureExtractor res = new PrankFeatureExtractor(protein, null, this)

        // init for whole protein
        protein.calcuateSurfaceAndExposedAtoms()

        res.surfaceLayerAtoms = protein.exposedAtoms

        res.preEvaluateProperties(res.surfaceLayerAtoms)
        if (DO_SMOOTH_REPRESENTATION) {
            res.preEvaluateSmoothRepresentations(surfaceLayerAtoms)
        }
        
        if (sampledPoints == null) {
            sampledPoints = protein.getSurface(trainingExtractor).points
        }
        res.sampledPoints = sampledPoints

        log.info "P2R protein:$protein.proteinAtoms.count  exposedAtoms:$res.surfaceLayerAtoms.count  deepLayer:$res.deepLayer.count sasPoints:$res.sampledPoints.count"

        return res
    }

    @Override
    void finalizeProteinPrototype() {
        // finalize features
        for (FeatureSetup.Feature feature : featureSetup.enabledFeatures) {
            feature.calculator.postProcessProtein(protein)
        }
    }

//===========================================================================================================//

    @Override
    Atoms getSampledPoints() {
        return sampledPoints
    }

//===========================================================================================================//

    public void preEvaluateProperties(Atoms atoms) {
        for (Atom a : atoms.list) {
            FeatureVector p = calcAtomProperties(a);
            // log.trace "prop $a.PDBserial:\t $p"
            properties.put(a.PDBserial, p);
        }
    }

    public void preEvaluateSmoothRepresentations(Atoms atoms) {
        for (Atom a : atoms.list) {
            FeatureVector p = calcSmoothRepresentation(a)
            // log.trace "repr $a.PDBserial:\t $p"
            smoothRepresentations.put(a.PDBserial, p );
        }
    }

//===========================================================================================================//

    double AVG_POW = params.avg_pow

    /**
     *
     * @param point SAS point
     * @param neighbourhoodAtoms neighbourhood protein atoms
     * @param fromVectors  must match atoms
     * @return
     */
    private PrankFeatureVector calcFeatVectorFromVectors(Atom point, Atoms neighbourhoodAtoms, Map<Integer, FeatureVector> fromVectors) {
        PrankFeatureVector res = new PrankFeatureVector(headerAdditionalFeatures)

        //if (fromAtoms.count==0) {
        //    log.error "!!! can't calc representation from empty list "
        //    return null
        //}

        if (neighbourhoodAtoms.isEmpty()) {
            throw new PrankException("No neighbourhood atoms. Cannot calculate feature vector. (Isn't neighbourhood_radius too small?)")
        }

        int n = neighbourhoodAtoms.count

        //if (n==1) {
        //    Atom a = fromAtoms.get(0)
        //    log.warn "calc from only 1 atom: $a.name $a"
        //}

        double weightSum = 0

        for (Atom a : neighbourhoodAtoms) {
            PrankFeatureVector props = (PrankFeatureVector) fromVectors.get(a.PDBserial)

            //assert props!=null, "!!! properties not precalculated "

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

            res.multiply(1d/multip)               // avg

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


        // calculate extra SAS features
        SasFeatureCalculationContext context = new SasFeatureCalculationContext(protein, neighbourhoodAtoms, this)
        for (FeatureSetup.Feature feature : featureSetup.enabledSasFeatures) {
            double[] values = feature.calculator.calculateForSasPoint(point, context)
            res.valueVector.setValues(feature.startIndex, values)
        }

        return res
    }

    /**
     *
     * @param point
     * @param useSmoothRepresentations or use basic properties for first run
     * @param neighbourhoodAtoms
     * @param store
     * @return
     */
    private PrankFeatureVector calcFeatureVectorFromAtoms(Atom point, boolean useSmoothRepresentations, Atoms neighbourhoodAtoms) {
        Map<Integer, FeatureVector> fromVectors
        if (useSmoothRepresentations) {
            fromVectors = smoothRepresentations
        } else {
            fromVectors = properties
        }

        if (fromVectors==null || fromVectors.isEmpty()) {
            log.error "!!! can't calculate representation from no vectors"
        }

        return calcFeatVectorFromVectors(point, neighbourhoodAtoms, fromVectors)
    }

    private double calcWeight(double dist) {
        return weightFun.weight(dist)
    }

//===========================================================================================================//

    /**
     * initial atom smoothRepresentations that feature vectors are calculated from
     */
    private PrankFeatureVector calcSmoothRepresentation(Atom atom) {

        Atoms neighbourhood = surfaceLayerAtoms.cutoutSphere(atom, SMOOTHING_CUTOFF_DIST)

        return calcFeatureVectorFromAtoms(atom, false, neighbourhood)
    }

    /**
     * @return feature vector for individual atom
     */
    private PrankFeatureVector calcAtomProperties(Atom atom) {
        return PrankFeatureVector.forAtom(atom, this)
    }

//===========================================================================================================//

    @Override
    public PrankFeatureVector calcFeatureVector(Atom point) {

        Atoms neighbourhood = surfaceLayerAtoms.cutoutSphere(point, NEIGH_CUTOFF_DIST)

        def vector = calcFeatureVectorFromAtoms(point, DO_SMOOTH_REPRESENTATION, neighbourhood)

//        if (CHECK_VECTORS) {
//            double[] arr = vector.array
//            for (int i=0; i!=arr.length; i++) {
//                if (arr[i] == Double.NaN) {
//                    String feat = vector.header[i]
//                    String msg = "Invalid value for feature $feat: NaN"
//                    System.out.println(msg)
//                    log.error(msg)
//                    throw new PrankException("Invalid value for feature $feat: NaN")
//                }
//            }
//
//        }

        return vector
    }

    @Override
    public List<String> getVectorHeader() {
        return new PrankFeatureVector(headerAdditionalFeatures).getHeader()
    }

}
