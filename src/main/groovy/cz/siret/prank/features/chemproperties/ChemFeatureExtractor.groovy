package cz.siret.prank.features.chemproperties

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.api.FeatureCalculator
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.generic.GenericHeader
import cz.siret.prank.features.weight.WeightFun
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.geom.samplers.PointSampler
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Handles the process of calculation of features.
 * At first it calculates features for solvent exposed atoms of the protein.
 * Then it projects them onto ASA points and calculates additional features for ASA point vector.
 */
@Slf4j
@CompileStatic
class ChemFeatureExtractor extends FeatureExtractor<ChemVector> implements Parametrized {

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

    /**
     * if set to true extractorFactory will use zero vectors for unknown residues
     * otherwise throws exception (so the whole pocket can be ignored)
     */
    private boolean MASK_UNKNOWN_RESIDUES = params.mask_unknown_residues
    private double NEIGH_CUTOFF_DIST = params.neighbourhood_radius
    private boolean DO_SMOOTH_REPRESENTATION = params.smooth_representation
    private double SMOOTHING_CUTOFF_DIST = params.smoothing_radius
    private final boolean AVERAGE_FEAT_VECTORS = params.average_feat_vectors

    private final WeightFun weightFun = WeightFun.create(params.weight_function)

    // pocket related
    Pocket pocket
    Atoms surfaceLayerAtoms
    Atoms deepSurrounding    // for protrusion
    Atoms sampledPoints

    ExtraFeatureSetup extraFeatureSetup

//===========================================================================================================//

    public ChemFeatureExtractor() {
        initHeader()
    }

    private ChemFeatureExtractor(Protein protein) {
        super(protein)

        initHeader()
    }

    private void initHeader() {
        extraFeatureSetup = new ExtraFeatureSetup(params.extra_features)

        extraFeaturesHeader = extraFeatureSetup.jointHeader
        atomTableFeatures = params.atom_table_features // ,"apRawInvalids","ap5sasaValids","ap5sasaInvalids"
        residueTableFeatures = params.residue_table_features

        headerAdditionalFeatures = new GenericHeader([
                *extraFeaturesHeader,
                *atomTableFeatures,
                *residueTableFeatures,
        ] as List<String>)

    }

    @Override
    FeatureExtractor createPrototypeForProtein(Protein protein) {
        ChemFeatureExtractor res = new ChemFeatureExtractor(protein)
        res.trainingExtractor = this.trainingExtractor

        protein.calcuateSurfaceAndExposedAtoms()
        res.deepSurrounding = protein.proteinAtoms.cutoffAtoms(protein.exposedAtoms, params.protrusion_radius).buildKdTree()

        // init features
        for (FeatureCalculator feature : extraFeatureSetup.enabledFeatures) {
            feature.preProcessProtein(protein)
        }

        return res
    }


    @Override
    void prepareProteinPrototypeForPockets() {
        pocketPointSampler = PointSampler.create(protein, trainingExtractor)

        if (params.deep_surrounding) {
            surfaceLayerAtoms = deepSurrounding
        } else {
            // surfaceLayerAtoms = protein.proteinAtoms.cutoffAtomsAround(pocket.surfaceAtoms, 1)  // shallow
            //surfaceLayerAtoms = protein.exposedAtoms.cutoffAtoms(pocket.surfaceAtoms, 6) //XXX
            surfaceLayerAtoms = protein.exposedAtoms
        }

        log.debug "surfaceLayerAtoms:$surfaceLayerAtoms.count (surfaceAtoms: $pocket.surfaceAtoms.count) "

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
    private ChemFeatureExtractor(Protein protein, Pocket pocket, ChemFeatureExtractor proteinPrototype) {
        this.protein = protein
        this.pocket = pocket

        this.MASK_UNKNOWN_RESIDUES = proteinPrototype.MASK_UNKNOWN_RESIDUES
        this.headerAdditionalFeatures = proteinPrototype.headerAdditionalFeatures
        this.pocketPointSampler    = proteinPrototype.pocketPointSampler
        this.extraFeaturesHeader   = proteinPrototype.extraFeaturesHeader
        this.atomTableFeatures     = proteinPrototype.atomTableFeatures
        this.residueTableFeatures  = proteinPrototype.residueTableFeatures
        this.trainingExtractor     = proteinPrototype.trainingExtractor
        this.extraFeatureSetup     = proteinPrototype.extraFeatureSetup

        this.deepSurrounding = proteinPrototype.deepSurrounding
        this.surfaceLayerAtoms = proteinPrototype.surfaceLayerAtoms
        this.properties = proteinPrototype.properties
        this.smoothRepresentations = proteinPrototype.smoothRepresentations




    }

    @Override
    FeatureExtractor createInstanceForPocket(Pocket pocket) {
        ChemFeatureExtractor res = new ChemFeatureExtractor(protein, pocket, this)

        if (pocket.surfaceAtoms.count==0) {
            log.error "pocket with no surface atoms! [$protein.name]"
        }
        res.initForPocket()

        return res
    }

    private void initForPocket() {
        log.debug "extractorFactory initForPocket for pocket $pocket.name"

        //surfaceLayerAtoms = surfaceLayerAtoms.cutoffAtoms(pocket.surfaceAtoms, 6) //XXX
        log.debug "surfaceLayerAtoms:$surfaceLayerAtoms.count (surfaceAtoms: $pocket.surfaceAtoms.count) "

        sampledPoints = pocketPointSampler.samplePointsForPocket(pocket)

        log.debug "$pocket.name - points sampled: $sampledPoints.count"
    }

    /**
     * Used for predictions (P2RANK)
     * @return
     */
    FeatureExtractor createInstanceForWholeProtein() {

        ChemFeatureExtractor res = new ChemFeatureExtractor(protein, null, this)

        // init for whole protein
        protein.calcuateSurfaceAndExposedAtoms()

        res.surfaceLayerAtoms = protein.exposedAtoms


        res.preEvaluateProperties(res.surfaceLayerAtoms)
        if (DO_SMOOTH_REPRESENTATION) {
            res.preEvaluateSmoothRepresentations(surfaceLayerAtoms)
        }
        res.sampledPoints = protein.getSurface(trainingExtractor).points



        log.info "P2R protein:$protein.proteinAtoms.count  exposedAtoms:$res.surfaceLayerAtoms.count  deepSurrounding:$res.deepSurrounding.count connollyPoints:$res.sampledPoints.count"

        return res
    }

    @Override
    void finalizeProteinPrototype() {
        // finalize features
        for (FeatureCalculator feature : extraFeatureSetup.enabledFeatures) {
            feature.postProcessProtein(protein)
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
    private ChemVector calcFeatVectorFromVectors(Atom point, Atoms neighbourhoodAtoms, Map<Integer, FeatureVector> fromVectors) {
        ChemVector res = new ChemVector(headerAdditionalFeatures)

        //if (fromAtoms.count==0) {
        //    log.error "!!! can't calc representation from empty list "
        //    return null
        //}

        int n = neighbourhoodAtoms.count

        //if (n==1) {
        //    Atom a = fromAtoms.get(0)
        //    log.warn "calc from only 1 atom: $a.name $a"
        //}

        for (Atom a : neighbourhoodAtoms) {
            ChemVector props = (ChemVector) fromVectors.get(a.PDBserial)

            //assert props!=null, "!!! properties not precalculated "

            double dist = Struct.dist(point, a)
            double weight = calcWeight(dist)

            res.add( props.copy().multiply(weight) )
        }

        if (AVERAGE_FEAT_VECTORS) {
            double multip = Math.pow(n, AVG_POW)

            res.multiply(1d/multip)
            res.atomDensity *= multip
            res.hDonorAtoms *= multip
            res.hAcceptorAtoms *= multip
        }

        res.atoms = n


        // calculate extra SAS features

        SasFeatureCalculationContext context = new SasFeatureCalculationContext(protein, neighbourhoodAtoms, this)
        for (FeatureCalculator feature : extraFeatureSetup.enabledSasFeatures) {
            double[] values = feature.calculateForSasPoint(point, context)
            res.additionalVector.setValues(feature.header, values)
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
    private ChemVector calcFeatureVectorFromAtoms(Atom point, boolean useSmoothRepresentations, Atoms neighbourhoodAtoms) {
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
    private ChemVector calcSmoothRepresentation(Atom atom) {

        Atoms neighbourhood = surfaceLayerAtoms.cutoffAtomsAround(atom, SMOOTHING_CUTOFF_DIST)

        return calcFeatureVectorFromAtoms(atom, false, neighbourhood)
    }

    /**
     * @return feature vector for individual atom
     */
    private ChemVector calcAtomProperties(Atom atom) {
        return ChemVector.forAtom(atom, this)
    }

//===========================================================================================================//

    @Override
    public ChemVector calcFeatureVector(Atom point) {

        Atoms neighbourhood = surfaceLayerAtoms.cutoffAtomsAround(point, NEIGH_CUTOFF_DIST)

        return calcFeatureVectorFromAtoms(point, DO_SMOOTH_REPRESENTATION, neighbourhood)
    }

    @Override
    public List<String> getVectorHeader() {
        return new ChemVector(headerAdditionalFeatures).getHeader()
    }

}
