package cz.siret.prank.features.chemproperties

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.generic.GenericHeader
import cz.siret.prank.features.weight.WeightFun
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.geom.samplers.PointSampler
import cz.siret.prank.program.params.Parametrized

@Slf4j
@CompileStatic
class ChemFeatureExtractor extends FeatureExtractor<ChemVector> implements Parametrized {

    public static final String FEAT_PROTRUSION = "protrusion"
    public static final String FEAT_SURF_PROTRUSION = "surfprot"
    public static final String FEAT_BFACTOR = "bfactor"

    /** properties of atom itself */
    private Map<Integer, FeatureVector> properties = new HashMap<>()
    /** properties calculated from neighbourhood */
    private Map<Integer, FeatureVector> smoothRepresentations = new HashMap<>()

    GenericHeader headerAdditionalFeatures // header of additional generic vector
    List<String> extraFeatures
    List<String> atomTableFeatures
    List<String> residueTableFeatures

    boolean useFeatProtrusion
    boolean useFeatBfactor
    boolean useFeatSurfProtrusion

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
    private final double PROTRUSION_RADIUS = params.protrusion_radius

    private final WeightFun weightFun = WeightFun.create(params.weight_function)

    // pocket related
    Pocket pocket
    Atoms surfaceLayerAtoms
    Atoms deepSurrounding    // for protrusion
    Atoms sampledPoints

//===========================================================================================================//

    public ChemFeatureExtractor() {
        initHeader()
    }

    private ChemFeatureExtractor(Protein protein) {
        super(protein)

        initHeader()
        initProteinPrototypeForPockets()
    }

    private void initHeader() {

        extraFeatures = params.extra_features
        atomTableFeatures = params.atom_table_features // ,"apRawInvalids","ap5sasaValids","ap5sasaInvalids"
        residueTableFeatures = params.residue_table_features

        headerAdditionalFeatures = new GenericHeader([
                *extraFeatures,
                *atomTableFeatures,
                *residueTableFeatures
        ] as List<String>)

    }

    @Override
    FeatureExtractor createPrototypeForProtein(Protein protein) {
        ChemFeatureExtractor res = new ChemFeatureExtractor(protein)
        res.trainingExtractor = this.trainingExtractor
        return res
    }

    private void initProteinPrototypeForPockets() {
        protein.calcuateSurfaceAndExposedAtoms()
        pocketPointSampler = PointSampler.create(protein, trainingExtractor)
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
        this.extraFeatures         = proteinPrototype.extraFeatures
        this.atomTableFeatures     = proteinPrototype.atomTableFeatures
        this.residueTableFeatures  = proteinPrototype.residueTableFeatures
        this.trainingExtractor     = proteinPrototype.trainingExtractor

        useFeatProtrusion = extraFeatures.contains(FEAT_PROTRUSION)
        useFeatSurfProtrusion = extraFeatures.contains(FEAT_SURF_PROTRUSION)
        useFeatBfactor = extraFeatures.contains(FEAT_BFACTOR)

        if (pocket!=null) {
            if (pocket.surfaceAtoms.count==0) {
                log.error "pocket with no surface atoms! [$protein.name]"
            }
            initForPocket()
        }

    }

    @Override
    FeatureExtractor createInstanceForPocket(Pocket pocket) {
        ChemFeatureExtractor res = new ChemFeatureExtractor(protein, pocket, this)

        if (pocket.surfaceAtoms.count==0) {
            log.error "pocket with no surface atoms! [$protein.name]"
        }

        return res
    }

    private void initForPocket() {
        log.debug "extractorFactory initForPocket for pocket $pocket.name"

        deepSurrounding = protein.proteinAtoms.cutoffAtoms(pocket.surfaceAtoms, params.protrusion_radius)

        if (params.deep_surrounding) {
            surfaceLayerAtoms = deepSurrounding
        } else {
            // surfaceLayerAtoms = protein.proteinAtoms.cutoffAtomsAround(pocket.surfaceAtoms, 1)  // shallow
            surfaceLayerAtoms = protein.exposedAtoms.cutoffAtoms(pocket.surfaceAtoms, 6) //XXX
        }

        log.debug "surfaceLayerAtoms:$surfaceLayerAtoms.count (surfaceAtoms: $pocket.surfaceAtoms.count) "

        preEvaluateProperties(surfaceLayerAtoms)
        if (DO_SMOOTH_REPRESENTATION) {
            preEvaluateSmoothRepresentations(surfaceLayerAtoms)
        }

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
        res.deepSurrounding = protein.proteinAtoms.cutoffAtoms(protein.exposedAtoms, params.protrusion_radius).buildKdTree()

        res.preEvaluateProperties(res.surfaceLayerAtoms)
        if (DO_SMOOTH_REPRESENTATION) {
            res.preEvaluateSmoothRepresentations(surfaceLayerAtoms)
        }
        res.sampledPoints = protein.getSurface(trainingExtractor).points

        log.info "P2R protein:$protein.proteinAtoms.count  exposedAtoms:$res.surfaceLayerAtoms.count  deepSurrounding:$res.deepSurrounding.count connollyPoints:$res.sampledPoints.count"

        return res
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
     * @param point
     * @param fromAtoms
     * @param fromVectors  must match atoms
     * @return
     */
    private ChemVector calcFeatVectorFromVectors(Atom point, Atoms fromAtoms, Map<Integer, FeatureVector> fromVectors) {
        ChemVector res = new ChemVector(headerAdditionalFeatures)

        //if (fromAtoms.count==0) {
        //    log.error "!!! can't calc representation from empty list "
        //    return null
        //}

        int n = fromAtoms.count

        //if (n==1) {
        //    Atom a = fromAtoms.get(0)
        //    log.warn "calc from only 1 atom: $a.name $a"
        //}

        for (Atom a : fromAtoms) {
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

        if (useFeatProtrusion) {

            // brute force ... O(N*M) where N is number of atoms and M number of Connolly points
            // deepSurrounding conmtains often nearly all of the protein atoms
            // and this is one of the most expensive part od the algorithm when making predictions
            // (apart from classification and Connolly surface generation)
            // better solution would be to build triangulation over protein atoms or to use KD-tree with range search
            // or at least some space compartmentalization

            // optimization? - we need ~250 for protrusion=10 and in this case it is sower
            //int MAX_PROTRUSION_ATOMS = 250
            //Atoms deepSurrounding = this.deepSurrounding.withKdTree().kdTree.findNearestNAtoms(point, MAX_PROTRUSION_ATOMS, false)

            int protAtoms = deepSurrounding.cutoffAtomsAround(point, PROTRUSION_RADIUS).count
            res.additionalVector.set(FEAT_PROTRUSION, protAtoms)
        }
        if (useFeatSurfProtrusion) {
            int sp = protein.exposedAtoms.cutoffAtomsAround(point, PROTRUSION_RADIUS).count
            res.additionalVector.set(FEAT_SURF_PROTRUSION, sp)
        }

        return res
    }

    /**
     *
     * @param point
     * @param useSmoothRepresentations or use basic properties for first run
     * @param fromAtoms
     * @param store
     * @return
     */
    private ChemVector calcFeatureVectorFromAtoms(Atom point, boolean useSmoothRepresentations, Atoms fromAtoms) {
        Map<Integer, FeatureVector> fromVectors
        if (useSmoothRepresentations) {
            fromVectors = smoothRepresentations
        } else {
            fromVectors = properties
        }

        if (fromVectors==null || fromVectors.isEmpty()) {
            log.error "!!! can't calculate representation from no vectors"
        }

        return calcFeatVectorFromVectors(point, fromAtoms, fromVectors)
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
