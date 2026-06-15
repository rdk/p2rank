package cz.siret.prank.program.params

import com.google.common.annotations.Beta
import com.google.common.collect.ImmutableSet
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Sutils
import groovy.transform.AutoClone
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Holds all global parameters of the program.
 *
 * This file is also main source of parameter description/documentation.
 *
 * Parameter annotations:
 * @RuntimeParam            ... Parameters related to program execution.
 * @ModelParam              ... Actual parameters of the algorithm, related to extracting features and calculating results.
 *                              It is important that those parameters stay the same when training a model and then using it for inference.
 * @ModelParam // training  ... Model params used only in training phase but not during inference.
 */
@Slf4j
@AutoClone
@CompileStatic
class Params {

    public static Params INSTANCE = new Params()

    public static Params getInst() {
        return INSTANCE
    }

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    @RuntimeParam
    String dataset_base_dir = null

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     * {version} is replaced by program version
     */
    @RuntimeParam
    String output_base_dir = null

    /**
     * Location of pre-trained serialized model.
     * (set path relative to install_dir/models/)
     */
    @RuntimeParam
    String model = "default"

    /**
     * Random seed
     */
    @RuntimeParam
    int seed = 42

    /**
     * Parallel execution (processing datasets in parallel)
     */
    @RuntimeParam
    boolean parallel = true

    /**
     * Number of computing threads
     */
    @RuntimeParam
    int threads = Runtime.getRuntime().availableProcessors() + 1

    /**
     * Number for threads used for generating R plots
     */
    @RuntimeParam
    int r_threads = 2

    /**
     * Generate plots for each metric with R when doing grid optimization (ploop command) on 1 or 2 variables
     */
    @RuntimeParam
    boolean r_generate_plots = true

    /**
     * Generate standard deviation plot for each statistic when generating R plots
     */
    @RuntimeParam
    boolean r_plot_stddevs = false

    /**
     * Number of cross-validation folds to work on simultaneously.
     * (Multiplies required memory)
     */
    @RuntimeParam
    int crossval_threads = 1

    /**
     * defines witch atoms around the ligand are considered to be part of the pocket
     * (ligands with longer distance are considered 'distant', i.e. irrelevant floating ligands)
     */
    @ModelParam // training
    double ligand_protein_contact_distance = 4

    /**
     * acceptable distance between ligand center and closest protein atom for relevant ligands
     * (ligands with longer distance are considered 'distant', i.e. irrelevant floating ligands)
     */
    @ModelParam // training
    double ligc_prot_dist = 5.5

    //==[ Features ]=========================================================================================================//

    /**
     * List of general calculated features
     */
    @ModelParam
    List<String> features = ["chem", "protrusion", "bfactor", "atom_table", "residue_table"]

    /**
     * List that is added to the features list on runtime.
     * Useful in grid optimization mode for comparing different new features.
     */
    @ModelParam
    List<String> extra_features = []

    /**
     * List of fixed sub-features that are always included (even if filtered out by feature_filters).
     */
    @ModelParam
    List<String> ploop_fixed_subfeatures = []

    /**
     * List of features that come directly from atom type tables
     * see atomic-properties.csv
     */
    @ModelParam
    List<String> atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"]

    /**
     * List of features that come directly from residue tables
     */
    @ModelParam
    List<String> residue_table_features = []

    /**
     * List of feature filters that are applied to individual features (i.e. sub-features).
     * If empty all individual features are used.
     * Filters are applied sequentially.
     *
     * Examples of individual filters:
     * <ul>
     *   <li> "*" - include all
     *   <li> "chem.*" - include all with prefix "chem."
     *   <li> "-chem.*" - exclude all with prefix "chem."
     *   <li> "chem.hydrophobic" - include particular sub-feature
     *   <li> "-chem.hydrophobic" - exclude particular sub-feature
     * </ul>
     *
     * If the first filter in feature_filters starts with "-", include-all filter ("*") is implicitly applied to the front.
     *
     * Examples of full feature_filters values:
     * <ul>
     *   <li> [] - include all
     *   <li> ["*"] - include all
     *   <li> ["*","-chem.*"] - include all except those with prefix "chem."
     *   <li> ["-chem.*"] - include all except those with prefix "chem."
     *   <li> ["-chem.*","chem.hydrophobic"] - include all except those with prefix "chem.", but include "chem.hydrophobic"
     *   <li> ["chem.hydrophobic"] - include only "chem.hydrophobic"
     *   <li> ["chem.*","-chem.hydrophobic","-chem.atoms"] - include only those with prefix "chem.", except "chem.hydrophobic" and "chem.atoms"
     * </ul>
     */
    @ModelParam
    List<String> feature_filters = []

    /**
     * Exponent applied to all atom table features // TODO change default to 1
     */
    @ModelParam
    double atom_table_feat_pow = 2

    /**
     * Dummy param to preserve behaviour of older versions.
     * Should be set to true for training new models.
     *
     * If true sign of value is reapplied after transformation by atom_table_feat_pow
     */
    @ModelParam
    boolean atom_table_feat_keep_sgn = false

    /**
     * radius for calculation protrusion feature
     */
    @ModelParam
    double protrusion_radius = 10

//===========================================================================================================//

    /**
     * Number of bins for protr_hist feature, must be >=2
     */
    @ModelParam
    int protr_hist_bins = 5

    /**
     * Param of protr_hist feature
     */
    @ModelParam
    boolean protr_hist_cumulative = false

    /**
     * Param of protr_hist feature
     */
    @ModelParam
    boolean protr_hist_relative = false

    /**
     * Number of bins for Atom Pair distance histogram (pair_hist) feature, must be >=2
     */
    @ModelParam
    int pair_hist_bins = 5

    /**
     * Radius capturing atoms considered in pair_hist feature
     */
    @ModelParam
    double pair_hist_radius = 6

    /**
     * smooth vs. sharp binning
     */
    @ModelParam
    boolean pair_hist_smooth = false

    /**
     * apply normalization to histogram
     */
    @ModelParam
    boolean pair_hist_normalize = false

    /**
     * if false only protein exposed atoms are considered
     */
    @ModelParam
    boolean pair_hist_deep = true

    /**
     * size of random subsample of atom pairs, 0 = all
     */
    @ModelParam
    int pair_hist_subsample_limit = 0

//===========================================================================================================//

    /**
     * Load sequence conservation data
     */
    @Deprecated
    @RuntimeParam
    boolean load_conservation = false


    /**
     * Pocket scoring algorithm
     */
    @ModelParam
    String score_pockets_by = "p2rank" // possible values: "p2rank", "conservation", "combi"

    /**
     * Conservation exponent for re-scoring pockets
     */
    @ModelParam
    int conservation_exponent = 1

    /**
     * Radius for calculating conservation cloud related features
     */
    @ModelParam
    double conserv_cloud_radius = 10

    /**
     * Radius for calculating secondary structure cloud related features
     */
    @ModelParam
    double ss_cloud_radius = 10

    /**
     * Cutoff distance (Angstrom) for ANM spring connections between Cα atoms.
     * Used by features anm_sensor, anm_effectiveness, anm_msf.
     */
    @ModelParam
    double feat_anm_cutoff = 10.0

    /**
     * Uniform spring constant (γ) in the ANM Hessian. Standard ANM uses γ=1
     * since absolute scale cancels in PRS ratios and only sets the unit of MSF.
     */
    @ModelParam
    double feat_anm_gamma = 1.0

    /**
     * Number of non-trivial ANM modes kept after discarding 6 zero modes.
     * PRS and MSF are summed over these modes.
     */
    @ModelParam
    int feat_anm_n_modes = 20

    /**
     * Absolute eigenvalue threshold for treating an ANM mode as a rigid-body
     * (zero-frequency) mode. Modes below this are discarded before keeping
     * feat_anm_n_modes vibrational modes.
     */
    @ModelParam
    double feat_anm_zero_mode_threshold = 1e-6

    /**
     * Heavy-atom distance cutoff (Angstrom) defining edges in the residue
     * contact graph used by features cg_betweenness, cg_closeness, cg_degree.
     */
    @ModelParam
    double feat_cgraph_cutoff = 4.5

    /**
     * Directories in which to look for conservation score files.
     * Path is absolute or relative to the dataset directory.
     * If null or empty: look in the same directory as protein file
     */
    @RuntimeParam
    List<String> conservation_dirs = []

    /**
     * Type/format of conservation scores. Determines cache subdirectory.
     * Currently supported: "hmm"
     */
    @RuntimeParam
    String conservation_type = null

    /**
     * Directory for conservation score cache files.
     * When set, all cached files are stored/searched here instead of per-protein .p2rank-cache dirs.
     * Layout: {conservation_cache_dir}/{conservation_type}/{baseName}_{chainId}.hom
     */
    @RuntimeParam
    String conservation_cache_dir = null

    /**
     * When true, conservation cache is neither read nor written.
     * Provider still fetches scores on every call, but results are not persisted to disk.
     */
    @RuntimeParam
    boolean conservation_disable_cache = false

    /**
     * Provider for external conservation scores. Currently supported: "hmm_server".
     * When null, falls back to existing conservation_dirs behavior.
     */
    @RuntimeParam
    String conservation_provider = null

    /**
     * Base URL of the conservation server (without endpoint path).
     * Required when conservation_provider is set.
     */
    @RuntimeParam
    String conservation_provider_url = null

    /**
     * Per-request timeout in seconds for conservation provider HTTP requests.
     */
    @RuntimeParam
    int conservation_provider_timeout = 600

    /**
     * Max concurrent requests to the conservation server. 0 = use 'threads' param.
     * Controls concurrency for both predict (via semaphore throttling) and preload-conservation (thread pool size).
     */
    @RuntimeParam
    int conservation_provider_threads = 0

    @RuntimeParam
    List<String> electrostatics_dirs = []


    /**
     * Log scores for binding and nonbinding scores to file
     */
    @RuntimeParam
    String log_scores_to_file = ""

    /**
     * limits how many pocket SAS points are used for scoring (after sorting), 0=unlimited
     * affects scoring pockets and also residues
     */
    @ModelParam
    int score_point_limit = 0

//==[ Classifiers ]=========================================================================================================//

    /**
     * see ClassifierOption
     */
    @ModelParam
    String classifier = "FastRandomForest"

    /**
     * see ClassifierOption
     */
    @ModelParam
    String inner_classifier = "FastRandomForest"

    /**
     * see ClassifierOption
     */
    @ModelParam
    int meta_classifier_iterations = 5

    /**
     * works only with classifier "CostSensitive_RF"
     */
    @ModelParam // training
    double false_positive_cost = 2

    //=== Random Forests =================

    /**
     * RandomForest trees
     */
    @ModelParam // training
    int rf_trees = 100

    /**
     * RandomForest depth limit, 0=unlimited
     */
    @ModelParam // training
    int rf_depth = 0

    /**
     * RandomForest feature subset size for one tree, 0=default(sqrt)
     */
    @ModelParam // training
    int rf_features = 0

    /**
     * number of threads used in RandomForest training (0=use value of threads param)
     */
    @RuntimeParam // training
    int rf_threads = 0

    /**
     * size of a bag: 1..100% of the dataset
     */
    @ModelParam // training
    int rf_bagsize = 100

    /**
     * Flatten random forest after loading if possible
     */
    @RuntimeParam
    @ModelParam // training
    boolean rf_flatten = false

    /**
     * Flattening target type for random forest. Only relevant if rf_flatten=true.
     *
     * IMPORTANT: targets fall into two families with DIFFERENT prediction semantics
     * (see FasterForest's PREDICTION-SEMANTICS.md):
     *  - "Legacy" / faithful (sum-then-normalize): reproduces the trained forest's
     *    probabilities exactly. Trained leaves generally do NOT sum to 1 (the shipped
     *    default model averages ~1.5), so this distinction is material, not cosmetic.
     *  - "Score-based" (normalize-then-average): each leaf is pre-normalized, giving
     *    every tree an equal vote. Faster/smaller, but per-point probabilities differ
     *    from the trained model. Safe for a fresh train+eval run (the point-score
     *    threshold is calibrated on the same flattened model), but do NOT pair a
     *    score-based re-flatten of an existing model with a threshold calibrated on
     *    the legacy model (e.g. the default pred_point_threshold) — the operating
     *    point shifts.
     *
     * Faithful targets:    LegacyFlatBinaryForest, SoaLegacyFlatBinaryForest, ShortFlatBinaryForest,
     *                      SuperShortLegacyFlatBinaryForest
     * Faithful-approximate: Int16LeafSoaLegacyFlatBinaryForest (int16-quantized leaves — ranking-equivalent
     *                      to LegacyFlat, not bit-exact; smaller footprint. Safe with the default
     *                      threshold; ranking-gated.) The float-split-descent variants (FasterForest 2.13.0,
     *                      experimental) are also faithful-approximate / ranking-gated:
     *                      FloatSplitSoaLegacyFlatBinaryForest (float splits, exact double leaves),
     *                      Int16LeafFloatSplitSoaLegacyFlatBinaryForest (int16 leaves + float splits, the
     *                      fastest faithful variant measured: about -14% vs Int16LeafSoa, -28% vs LegacyFlat
     *                      on GraalVM), and Int16LeafFloatSplitBranchlessSoaLegacyFlatBinaryForest.
     * Score-based targets: FlatBinaryForest, InterleavedBfsForest (and the other Bfs/Dfs/Ilp/Float/Native variants)
     *
     * Recommended ad-hoc re-flatten of the shipped (legacy) default model: SoaLegacyFlatBinaryForest
     * (bit-exact, faster), Int16LeafSoaLegacyFlatBinaryForest (ranking-equivalent, smaller), or the
     * experimental Int16LeafFloatSplitSoaLegacyFlatBinaryForest (ranking-equivalent, fastest measured).
     */
    @RuntimeParam
    @ModelParam // training
    String rf_flatten_target = "LegacyFlatBinaryForest"

    /**
     * DEFUNCT — has no effect. Superseded by rf_flatten_target.
     *
     * Previously intended to preserve the FastRandomForest aggregation (sum-then-
     * normalize over non-normalized leaves) on flatten. ModelConverter no longer
     * reads this flag; faithful output is now obtained by choosing a faithful
     * rf_flatten_target (LegacyFlatBinaryForest / ShortFlatBinaryForest /
     * SuperShortLegacyFlatBinaryForest), which is the default. Kept only so old
     * configs/model params that set it still parse.
     */
    @Deprecated
    @RuntimeParam
    @ModelParam // training
    boolean rf_flatten_as_legacy = true

    /**
     * try predict in batches if possible
     */
    @RuntimeParam
    boolean rf_batch_prediction = true


    /**
     * Old: Fix bug in RF libraries where class probabilities on leaves were not properly normalized.
     * Valid for FasterForest and FasterForest2. FastRandomForest has the bug (but not the fix).
     *
     * Note: this is not a bug but feature of these models! classProbs[] on leaves are weighted
     *       (by a number of instances and also by weights passed from weka)
     */
    @ModelParam // training
    boolean rf_ensure_leaves_normalized = false

    /**
     * cutoff for joining ligand atom groups into one ligand
     */
    @ModelParam // training
    double ligand_clustering_distance = 1.7 // ~= covalent bond length

    /**
     * cutoff around ligand that defines positives
     */
    @ModelParam
    double positive_point_ligand_distance = 2.5

    /**
     * distance around ligand atoms that define ligand induced volume
     * (for evaluation by some criteria, DSO, ligand coverage...)
     */
    @ModelParam
    double ligand_induced_volume_cutoff = 2.5

    /**
     * points between (positive_point_ligand_distance, positive_point_ligand_distance + neutral_point_margin) will not be considered positives or negatives and will be left out form training
     */
    @ModelParam // training
    double neutral_points_margin = 5.5

    /**
     * if true, negative points will be collected also from true pockets (but outside positive_point_ligand_distance)
     * otherwise only points from outside true pockets are considered negatives
     */
    @ModelParam // training
    boolean collect_negatives_from_true_pockets = false

    /**
     * Neighbourhood radius (A) used for calculating most of the features.
     */
    @ModelParam
    double neighbourhood_radius = 8

    /**
     * HETATM groups that are ignored (not marked as relevant ligands, e.g because they are cofactors or part of a substrate)
     */
    @ModelParam // training
    List<String> ignore_het_groups = ["HOH","DOD","WAT","NAG","MAN","UNK","GLC","ABA","MPD","GOL","SO4","PO4"]

    /**
     * Which ligand types define positive SAS points.
     * accepted values: "relevant", "ignored", "small", "distant"
     */
    @ModelParam // training
    List<String> positive_def_ligtypes = ["relevant"]

    /**
     * Cofactor specifiers - HETATM groups to include as part of the protein surface.
     *
     * Matching groups will:
     * - Contribute heavy atoms to SAS point generation and pocket detection
     * - Be EXCLUDED from ligand detection
     * - Have features calculated from nearest protein residue (SAS-level) or
     *   defaults (atom-level - cofactor atoms aren't amino acids)
     *
     * Specifier syntax mirrors the dataset 'ligands' column (Dataset.LigandDefinition):
     *   "FAD"                                     - all FAD groups
     *   "FAD[group_id:A_500]"                     - specific FAD by chain + residue number
     *   "FAD[A_500]"                              - shorthand for group_id
     *   "FAD[atom_id:12345]"                      - by PDB atom serial
     *   "FAD[contact_res_ids:A_D246,A_T259,...]"  - by surrounding polymer residues
     *
     * Group names are normalized to upper-case before matching against
     * group.PDBName, so {@code "fad"} and {@code "FAD"} both match FAD groups.
     * (BioJava returns uppercase PDB names; the case-fold is applied in
     * {@link CofactorHandler#parseAndValidate} for cofactors specifically —
     * the dataset 'ligands' column does NOT case-fold, so use uppercase there.)
     * Validation happens at startup via LigandDefinition.parse().
     *
     * Can be overridden per-structure using the 'cofactors' column in dataset files.
     *
     * Example values: ["FAD", "PLP", "HEM"]
     * Precise specifiers (with [...]) are typically used via the CLI or per-row
     * dataset column, not in static config files.
     * Default: [] (empty - cofactors treated as ligands or ignored per ignore_het_groups)
     *
     * Related: ignore_het_groups (ignored groups are excluded from ligand detection AND
     *          from protein surface; cofactors are excluded from ligand detection but
     *          INCLUDED in surface).
     */
    @RuntimeParam
    List<String> cofactors = []

    /**
     * Maximum distance (Å) from a cofactor's center of mass to the nearest
     * protein atom for the cofactor to be considered associated with the protein.
     *
     * Cofactors beyond this distance are still included in the surface, but
     * an INFO warning is logged - the cofactor may be a crystallization artifact
     * or positioned in the solvent far from the protein.
     *
     * Set to 0 to disable proximity checking.
     * Default: 15.0 Å (covers most covalently/tightly bound cofactors).
     */
    @RuntimeParam
    double cofactor_max_protein_dist = 15.0

    /**
     * Amino acid mapping mode for non-canonical residues.
     *
     * Controls how modified amino acid residue codes (e.g., MSE, LLP, TQP) are mapped
     * to standard 20 amino acids for feature calculation.
     *
     * Available modes:
     * - "minimal": Only MSE→MET, MEN→ASN (default, backward compatible)
     * - "pdbfixer": ~100 mappings from OpenMM pdbfixer
     * - "/path/to/file.csv": Custom mapping file (2-column CSV: FROM,TO)
     *
     * Example: aa_mapping = "pdbfixer"
     */
    @RuntimeParam
    String aa_mapping = "minimal"

    /**
     * Minimal heavy atom count for relevant ligands, other ligands are considered too small and ignored
     */
    @ModelParam // training
    int min_ligand_atoms = 5

    /**
     * If true, load ligands from separate files matching "ligand_*.{pdb,cif}" pattern
     * in the same directory as the main protein file, instead of from the primary structure file.
     * All such ligands are treated as relevant. Ligands from the primary file are moved to ignoredLigands.
     * Compressed variants (.gz, .zst, etc.) are supported.
     */
    @RuntimeParam
    boolean load_ligands_from_separate_files = false

    /**
     * Point sampler for extracting instances for training.
     * P2Rank and PRANK use SurfacePointSampler that produces SAS points.
     * Others like GridPointSampler are experimental, and also deprecated. see point_sampling_strategy
     */
    @ModelParam
    String point_sampler = "SurfacePointSampler"

    /**
     * surface | atoms | grid
     */
    @Beta
    String point_sampling_strategy = "surface"

    /**
     * multiplier for random point sampling
     */
    @ModelParam // training
    int sampling_multiplier = 3

    /**
     * solvent radius for SAS surface
     */
    @ModelParam
    double solvent_radius = 1.6

    /**
     * SAS tessellation (~density) used in prediction step.
     * Higher tessellation = higher density (+1 ~~ x4 points)
     */
    @ModelParam
    int tessellation = 2

    /**
     * SAS tessellation (~density) used in training step
     * 0 = use value of tessellation
     */
    @ModelParam // training
    int train_tessellation = 2

    /**
     * SAS tessellation (~density) used in training step to select negatives.
     * Allows denser positive sampling than negative sampling and thus deal with class imbalance and train faster.
     * 0 = use value of effective train_tessellation
     */
    @ModelParam // training
    int train_tessellation_negatives = 2

    /**
     * for grid and random sampling
     */
    @ModelParam
    double point_min_distfrom_protein = 2.5

    /**
     * for grid and random sampling
     */
    @ModelParam
    double point_max_distfrom_pocket = 4.5

    /**
     * grid cell size for grid sampling strategy (and old GridPointSampler)
     */
    @ModelParam
    double grid_cell_edge = 2

    /**
     * Cutoff radius around protein atoms. Grid points with higher distance to closest protein atom are discarded.
     */
    @ModelParam
    double grid_cutoff_radius = 3.4

    /**
     * Restrict training set size, 0=unlimited
     */
    @RuntimeParam // training
    int max_train_instances = 0

    /**
     * Param of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    double weight_power = 2

    /**
     * Param of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    double weight_sigma = 2.2

    /**
     * Param of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    double weight_dist_param = 4.5

    /**
     * Choice of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    String weight_function = "INV"

    /**
     * If false only single layer of proteins solvent exposed atoms is used for calculating features that are projected from protein atoms to SAS points
     */
    @ModelParam
    boolean deep_surrounding = false

    /** calculate feature vectors from smooth atom feature representation
     * (instead of directly from atom properties)
     */
    @Deprecated
    @ModelParam
    boolean smooth_representation = false

    /**
     * related to smooth_representation
     */
    @Deprecated
    @ModelParam
    double smoothing_radius = 4.5

    /**
     * determines how atom feature vectors are projected on to SAS point feature vector
     * if true, atom feature vectors are averaged
     * else they are only summed up
     */
    @ModelParam
    boolean average_feat_vectors = false

    /**
     * in feature projection from atoms to SAS points:
     * only applicable when average_feat_vectors=true
     * <0,1> goes from 'no average, just sum' -> 'full average'
     */
    @ModelParam
    double avg_pow = 1

    /**
     * regarding feature projection from atoms to SAS points: calculate weighted average
     * (should be true by default, kept false for backward compatibility reasons)
     */
    @ModelParam
    boolean avg_weighted = false

    /**
     * exponent of point ligandability score (before adding it to pocket score)
     */
    @ModelParam
    double point_score_pow = 2

    /**
     * exponent of point ligandability score (before adding it to residue score in residue prediction mode)
     * value less than 0 refers to the value of point_score_pow
     */
    @ModelParam
    double residue_point_score_pow = -1

    /**
     * Binary classifiers produces histogram of scores for class0 and class1
     * if true only score for class1 is considered
     * makes a difference only if histogram produced by classifier doesn't sum up to 1
     */
    @ModelParam
    boolean use_only_positive_score = true

    /**
     * If true trained models will not be saved to disk (good for parameter optimization)
     */
    @RuntimeParam
    boolean delete_models = false

    /**
     * delete files containing training/evaluation feature vectors
     */
    @RuntimeParam
    boolean delete_vectors = true

    /**
     * check all loaded/calculated vectors for invalid (NaN) values
     */
    @RuntimeParam
    boolean check_vectors = false

    /**
     * collect vectors also from eval dataset (only makes sense in combination with delete_vectors=false)
     */
    @RuntimeParam
    boolean collect_eval_vectors = false

    /**
     * collect vectors only at the beginning of seed loop routine
     * if dataset is sub-sampled (using train_protein_limit param) then dataset is sub-sampled only once
     * set to false when calculating learning curve!
     * train_protein_limit>0 should be always paired with collect_only_once=false
     */
    @RuntimeParam
    boolean collect_only_once = true

    /**
     * export vectors describing SAS points used during prediction
     * export is a table file containing: SAS point 3D coordinates, calculated features, predicted raw point ligandability score
     * see export_points_format
     */
    @RuntimeParam
    boolean export_points = false

    /**
     * format of the point export file
     *
     * relevant only if export_points=true
     *
     * Available options: "csv", "csv.gz", "csv.zst", "arrow", "arrow.gz", "arrow.zst", "parquet"
     */
    @RuntimeParam
    String export_points_format = "csv"

    // ---------- Pocket grid + descriptors export (see documentation/export-pocket-grid.md) ----------

    /**
     * Export the per-protein pocket grid: a regular 3D lattice covering empty space
     * around the protein, with each point tagged by the pocket(s) it belongs to.
     * Long format: one row per (point, pocket) pair.
     * Output: {outdir}/{name}_pocket_grid.{pocket_grid_format}
     */
    @RuntimeParam
    boolean export_pocket_grid = false

    /**
     * Export per-pocket descriptors (volume, sphericity, ...) to a separate file.
     * Output: {outdir}/{name}_pocket_descriptors.{pocket_grid_format}
     *
     * <p>If any selected descriptor needs the pocket grid (grid-derived: volume,
     * sphericity, radius_of_gyration, num_grid_points), the grid is built even
     * when {@link #export_pocket_grid} is 0 — only the grid file write is
     * suppressed. With a descriptor list that contains only grid-free entries
     * (num_residues, num_surface_atoms), the grid build itself is skipped.
     */
    @RuntimeParam
    boolean export_pocket_descriptors = false

    /**
     * Render the pocket grid as an overlay script for every renderer in
     * {@code vis_renderers} (PyMOL {@code .pml} and/or ChimeraX {@code .cxc}),
     * with a shared gzipped PDB sidecar. Requires {@code export_pocket_grid=true}
     * (validated at startup) and respects the master {@code -visualizations} switch.
     * Output: {outdir}/visualizations/{name}_pocket_grid.{pml,cxc}
     */
    @RuntimeParam
    boolean vis_pocket_grid = false

    /**
     * Format for the pocket_grid and pocket_descriptors files. Same allowed
     * values as export_points_format: csv, csv.gz, csv.zst, arrow, arrow.gz,
     * arrow.zst, parquet. Default csv.gz: per-protein grid files are O(MB)
     * uncompressed and ~5× smaller gzipped, with no observable write-time hit.
     */
    @RuntimeParam
    String pocket_grid_format = "csv.gz"

    /**
     * Include unassigned grid points (those outside every pocket's
     * {@code pocket_grid_assign_cutoff}) in the tabular grid export, emitted with
     * {@code pocket = 0} and sorted after all assigned rows. Default off: only
     * points assigned to at least one pocket are written.
     *
     * <p><b>Tabular export only.</b> This affects the {@code pocket_grid_format}
     * file (csv / arrow / parquet) exclusively. The PyMOL/ChimeraX PDB sidecar
     * ({@code vis_pocket_grid}) always shows assigned points only — it has no
     * sentinel for "unassigned" and unassigned points carry no per-pocket meaning
     * for visualization. So the tabular file and the visualization sidecar can
     * legitimately differ in row count when this is on; that divergence is by
     * design (the flag was previously removed for being inconsistent across
     * outputs — it is now scoped to the tabular path on purpose).
     *
     * <p>Especially useful with the atom-driven {@code pocket_grid_max_dist}: most
     * kept grid points are unassigned outer shell, and this is the only way to see
     * the full sampled lattice.
     */
    @RuntimeParam
    boolean pocket_grid_include_unassigned = false

    /** Lattice edge in Å. Volume scales with this³. */
    @RuntimeParam
    double pocket_grid_spacing = 1.2d

    /**
     * Maximum distance (Å) from the nearest protein/cofactor atom to keep a grid
     * point. This is the grid's outer bound: the sampled lattice is a shell around
     * the whole protein (bounding box of the protein atoms expanded by this margin),
     * not just the neighborhood of predicted pockets. Per-pocket membership is then
     * restricted separately via {@code pocket_grid_assign_cutoff} against
     * {@code Pocket.sasPoints}, so most kept points stay unassigned unless exported
     * with {@code pocket_grid_include_unassigned}.
     */
    @RuntimeParam
    double pocket_grid_max_dist = 4.0d

    /**
     * Additive buffer on the per-atom van der Waals exclusion: a grid point is
     * dropped if its nearest-atom distance is less than vdw(atom) + buffer.
     * Operates against protein atoms (cofactor atoms included when configured)
     * — keeps grid points out of physical atom volume.
     */
    @RuntimeParam
    double pocket_grid_atom_buffer = 1.0d

    /**
     * Distance cutoff (Å) for assigning a grid point to a pocket: a point is in
     * pocket P if it is within this distance of any of P.sasPoints.
     */
    @RuntimeParam
    double pocket_grid_assign_cutoff = 2.5d

    /**
     * Range-query strategy for the per-pocket "raw shell" computation:
     *   kdtree     (default) — build a KdTree on the grid, range-query around each pocket SAS point
     *   voxel_hash           — walk the small cube of lattice cells per pocket SAS point directly
     * KdTree is typically faster for fine grids (small pocket_grid_spacing); voxel-hash is
     * typically faster for coarse grids. Validated at startup.
     */
    @RuntimeParam
    String pocket_grid_assigner = "kdtree"

    /**
     * Shape-fill strategy for the per-pocket grid region:
     *   closing        (default) — true dilate-then-erode morphological closing;
     *                              fills enclosed holes/concavities without advancing
     *                              the outer boundary. Radius = pocket_grid_fill_close_radius.
     *   morph_closing            — iterative conditional dilation (no erode). Aggressive;
     *                              keep pocket_grid_fill_min_neighbors high to avoid
     *                              runaway outward growth.
     *   none                     — leave the raw shell as-is
     * Validated at startup. (Closing was chosen as default after a cavity- and
     * ligand-grounded calibration over fptrain/coach420/holo4k: best overlap/coverage
     * balance; see the fix/pocket-grid-overlap-fill analyses.)
     */
    @RuntimeParam
    String pocket_grid_fill = "closing"

    /** [closing only] closing radius: dilate by this many lattice layers, then erode by
     *  the same — closes holes/gaps up to ~2*radius cells wide. Small values (1-2) keep
     *  the fill tight and prevent bridging into neighbouring pockets. */
    @RuntimeParam
    int pocket_grid_fill_close_radius = 1

    /** [morph_closing only] minimum filled-neighbor count (of 26) to promote a candidate cell.
     *  Must exceed the 9 a flat front presents, else dilation runs away outward. 10 is the
     *  tightest runaway-safe value (10 > 9): it fills concavities/holes but not flat/convex
     *  surfaces. (14 was needlessly conservative -- it barely filled at all; 10 gives useful
     *  fill with zero fill-driven engulfment across chen11/joined/coach420/holo4k.)
     *  Ignored for pocket_grid_fill in {closing, none}. */
    @RuntimeParam
    int pocket_grid_fill_min_neighbors = 10

    /** [morph_closing only] iteration cap (guard against runaway dilation). Ignored for pocket_grid_fill in {closing, none}. */
    @RuntimeParam
    int pocket_grid_fill_max_iters = 10

    /**
     * Visualization-only knob (no effect on the exported grid CSV or descriptors).
     * Sphere radius (Å) used by the volumetric surface layer in the grid PML:
     * each grid point is rendered as a sphere of this radius, then PyMOL merges
     * adjacent spheres into a continuous surface where they overlap.
     *
     * <p>Default {@code -1} is a sentinel meaning "auto-scale with spacing"
     * → effective value is {@code 0.85 × pocket_grid_spacing}, which keeps the
     * surface visually consistent across spacings. At default spacing (1.2)
     * this gives ~1.02 Å, comfortably above the 3D-diagonal merge threshold
     * ({@code spacing × √3 / 2 ≈ 0.866 × spacing}). Any positive value
     * overrides with an absolute Å — but going much below {@code spacing/2}
     * leaves the spheres too disconnected for PyMOL's surface algorithm and
     * most of the mesh drops below the rendering threshold.
     *
     * <p><b>Renderer divergence:</b> the value is used as a raw vdW radius in
     * PyMOL ({@code solvent_radius=0}) so the visible radius equals this
     * setting exactly. ChimeraX renders with its non-zero default probe
     * (~0.4 Å), so the visible radius there is roughly this setting + the
     * probe — a few tenths of an Å larger than PyMOL for the same input.
     */
    @RuntimeParam
    double vis_pocket_grid_volume_radius = -1d

    /**
     * Visualization-only knob (no effect on the exported grid CSV or descriptors).
     * Iso-surface threshold for the Gaussian-density layer in the grid PML.
     * Lower values produce a looser surface that extends farther from each grid
     * point (closer to the volumetric layer); higher values give a tighter
     * surface around the densest regions. Default suits the default spacing.
     */
    @RuntimeParam
    double vis_pocket_grid_gaussian_iso = 0.5d

    /**
     * Descriptors to compute and emit per pocket. Each entry must match a name
     * registered in PocketDescriptorRegistry. Validated at startup.
     *
     * <p>Default: every registered descriptor — the grid-derived ones share the
     * pocket-grid build, so adding more is cheap; the electrostatic ones
     * iterate {@code pocket.surfaceAtoms} once via {@code PocketChargeStats},
     * sub-millisecond per pocket. Inert when {@code -export_pocket_descriptors 0}
     * (the default — none of the per-pocket compute fires).
     */
    @RuntimeParam
    List<String> pocket_descriptors = ["num_residues", "num_surface_atoms", "num_grid_points",
                                       "volume", "sphericity", "radius_of_gyration",
                                       "principal_moments",
                                       "pocket_net_charge", "pocket_charge_polarity",
                                       "pocket_dipole_magnitude"]

    /**
     * Per-grid-point descriptors appended as extra columns to the pocket-grid
     * export (one value per descriptor column per (point, pocket) row). Each
     * entry must match a name in PocketGridPointDescriptorRegistry. Multi-column
     * descriptors get the prefix "{name}." — e.g. volsite emits volsite.vsAromatic,
     * volsite.vsCation, etc. Validated at startup.
     *
     * <p>Default: every registered descriptor (volsite, volsite_smooth,
     * electrostatics — 17 columns total). Inert when
     * {@code -export_pocket_grid 0} (the default — no per-(point, pocket) work
     * fires). Users who want only some descriptors override this list with a
     * subset; users who want the base x/y/z/pocket schema only override with
     * an empty list.
     *
     * <p>Note: this knob is only consumed when {@code -export_pocket_grid 1};
     * setting it without enabling the grid export silently does nothing.
     * Same pattern as {@code -pocket_descriptors} requiring
     * {@code -export_pocket_descriptors 1}. Not cross-checked at startup
     * because both knobs are list-typed and an empty/default list is
     * indistinguishable from "user wants nothing" at validation time.
     */
    @RuntimeParam
    List<String> pocket_grid_point_descriptors = ["volsite", "volsite_smooth", "electrostatics"]

    /**
     * Cutoff radius (Å) for the volsite per-grid-point descriptor:
     * a pharmacophore type's indicator column is 1 iff any protein atom
     * carrying that type is within this distance of the grid point.
     * Default 4.0 matches the original VolSite pharmacophore search radius.
     */
    @RuntimeParam
    double pocket_grid_volsite_radius = 4.0d

    /**
     * Gaussian σ (Å) for the volsite_smooth per-grid-point descriptor:
     * each protein atom carrying a pharmacophore type contributes
     * exp(-r²/(2σ²)) to that type's column. The kernel is truncated at
     * 4σ (negligible tail). Default 2.0 gives a smooth analogue of the
     * 4 Å volsite indicator at its default cutoff.
     */
    @RuntimeParam
    double pocket_grid_volsite_sigma = 2.0d

    /**
     * Cutoff radius (Å) for Coulomb-sum electrostatics features (SAS-point
     * feature + pocket-grid descriptor). 6.0 Å is the standard "local
     * electrostatics" range in protein–ligand interaction literature —
     * longer than volsite's 4 Å (VDW-driven pharmacophore contacts) and
     * shorter than the 9 Å used for full LJ + Coulomb energy probes in
     * {@link cz.siret.prank.features.implementation.energy.MethylEnergyCloudSF}
     * (governed by {@code energy_rc}). 6 Å captures first-shell H-bonding
     * and salt-bridge partners while staying short enough that the {@code 1/r}
     * envelope dominates and the truncation doesn't bias the gradient.
     */
    @RuntimeParam
    double electrostatics_radius = 6.0d

    /**
     * Minimum atom-to-probe distance (Å) used in Coulomb 1/r — guards against
     * the 1/0 singularity when a SAS or grid probe point sits inside an atom's
     * vdW radius (rare but possible at the probe-radius boundary). 1.5 Å is
     * just below typical heavy-atom vdW radii (C ≈ 1.7, N ≈ 1.55, O ≈ 1.52)
     * and roughly equal to a polar H–acceptor contact distance — beyond
     * this the Coulomb formula is well-behaved; below it the value would
     * blow up unphysically.
     */
    @RuntimeParam
    double electrostatics_min_r = 1.5d

    /**
     * Benchmark-only: skip the per-SAS-point feature extraction and ML scoring in
     * the rescorer. Each pocket's newScore is just passed through from its
     * existing score. Use this to isolate the cost of grid build / descriptor
     * compute / writers from the (much heavier) ML rescoring work. Has no effect
     * outside the rescore command.
     */
    @RuntimeParam
    boolean bench_skip_rescoring = false

    /**
     * number of random seed iterations
     *
     * Only relevant when training and evaluating new models.
     * Result metrics are then averaged or calculated for sum of runs (where appropriate, like F1 measure).
     * Example: using running  traineval with loop=10 will do ten runs with different random seed and calculate averages.
     */
    @RuntimeParam
    int loop = 1

    /**
     * keep datasets (structures and SAS points) in memory between crossval/seedloop iterations
     */
    @RuntimeParam
    boolean cache_datasets = false


    /**
     * calculate feature importances
     * available only for some classifiers
     */
    @RuntimeParam
    boolean feature_importances = false

    /**
     * produce visualisations
     */
    @RuntimeParam
    boolean visualizations = true

    /**
     * Renderers used to produce visualizations. Available renderers: [pymol, chimerax].
     * Validated at startup — unknown names, duplicates, or empty/null entries throw.
     */
    @RuntimeParam
    List<String> vis_renderers = ["pymol", "chimerax"]

    /**
     * visualize all surface points (not just inner pocket points)
     */
    @RuntimeParam
    boolean vis_all_surface = false

    /**
     * copy all protein pdb files to visualization folder (making visualizations portable)
     */
    @RuntimeParam
    boolean vis_copy_proteins = true

    /**
     * generate new protein pdb files from structures in memory instead of reusing input files
     * (useful when structures were manipulated in memory, e.g. when reducing to specified chains)
     */
    @RuntimeParam
    boolean vis_generate_proteins = true

    /**
     * Highlight ligands by rendering them as enlarged balls (instead of sticks).
     * Necessary to see 1 atom ligands like ions.
     * Affects rendering only in pocket mode.
     */
    @RuntimeParam
    boolean vis_highlight_ligands = false

    /**
     * Highlight cofactor atoms (matched via -cofactors) as teal sticks in PyMOL output,
     * distinct from ligand spheres and the polymer cartoon.
     *
     * If false, cofactor atoms render with PyMOL's default style for HETATMs (sticks in
     * the default colour), which can be visually indistinguishable from ligands or noise.
     * Setting this to false does NOT change pocket prediction - cofactor atoms remain on
     * the protein surface; only the colour highlight is removed.
     */
    @RuntimeParam
    boolean vis_highlight_cofactors = true

    /**
     * Method for computing binding site center of observed pocket for evaluation (used by DCC criterion).
     * Observed pocket can be defined by ligand or can be explicit (set of residues defined in the dataset).
     * Values: explicit, atoms_center_of_mass, sas_points_centroid, ca_atoms_centroid
     *
     * Note: atoms_center_of_mass uses mass-weighted center of ligand/residue atoms for both site types.
     * explicit is only supported for explicitly defined sites (not ligand-defined).
     * ca_atoms_centroid uses geometric centroid of CA atoms of contact residues
     * (for ligand sites: contact residues within ligand_protein_contact_distance;
     *  for explicit sites: the defined residues directly).
     *
     * @see cz.siret.prank.domain.SiteCenterMethod
     */
    @RuntimeParam
    String site_eval_center_method = "atoms_center_of_mass"

    /**
     * Use SAS points instead of atoms for site representation in evaluation criteria (e.g. DCA).
     * Note: intended for exploration of explicit sites only. It will unrealistically inflate DCA for ligand-defined sites.
     */
    @RuntimeParam
    boolean site_eval_sas_pts_as_atoms = false

    /**
     * Render site/pocket centroids as colored balls in visualizations
     */
    @RuntimeParam
    boolean vis_site_centers = false

    /**
     * PyMol color gradient for coloring points by their score
     * see https://pymolwiki.org/index.php/Spectrum
     */
    @RuntimeParam
    String vis_point_gradient_pymol = "green_red"

    /**
     * max score for coloring points by their score
     * (points with higher score will be colored by the max color)
     */
    @RuntimeParam
    double vis_point_gradient_max = 0.7

    /**
     * zip PyMol visualizations to save space
     */
    @Deprecated
    @RuntimeParam
    boolean zip_visualizations = false

    /**
     * use strictly inner pocket points or more wider pocket neighbourhood
     */
    @RuntimeParam
    boolean strict_inner_points = false

    /**
     * cross-validation folds
     */
    @RuntimeParam
    int folds = 5

    /**
     * collect evaluations for top [n+0, n+1,...] pockets (n is true pocket count)
     */
    @RuntimeParam
    List<Integer> eval_tolerances = [0,1,2,4,10,99]

    /**
     * Calculate pocket predictions.
     * This is a main switch between re-scoring of predictions by other methods (PRANK) and pocket prediction (P2Rank)
     */
    @RuntimeParam
    boolean predictions = true

    /**
     * Validate that a loaded model's stored feature header (features.txt) matches the feature header
     * produced by the current configuration before predicting.
     * true  = fail with a clear error on mismatch (recommended; prevents silently wrong predictions).
     * false = only log a warning and continue (use only if deliberately running a model with a non-matching config).
     */
    @RuntimeParam
    boolean fail_on_model_feature_mismatch = true

    /**
     * Declares what this config is intended for: "prediction" or "rescoring" (empty = unrestricted).
     * Used to reject wrong command/config combinations (e.g. `prank rescore -c alphafold`,
     * or a rescoring config used with `predict`). See issue #73.
     */
    @RuntimeParam
    String config_purpose = ""

    /**
     * Validate that the command matches the config's declared purpose (config_purpose).
     * true  = fail with a clear error on mismatch (recommended; prevents silently wrong results).
     * false = only log a warning and continue.
     */
    @RuntimeParam
    boolean fail_on_wrong_config = true

    /**
     * Residue prediction mode (as opposed to full pocket prediction mode)
     */
    @RuntimeParam
    boolean predict_residues = false

    /**
     * If true, assign class to SAS points in training dataset based on proximity to the ligand.
     * If false, assign class based the class of nearest residue.
     * Distinction only makes sense running in residue prediction mode (predict_residues = true).
     */
    @RuntimeParam
    boolean ligand_derived_point_labeling = true

    /**
     * produce residue labeling file (in predict mode)
     *
     * Even in full pocket prediction mode (predict_residues=false) we can label and score residues using transformers.
     */
    @RuntimeParam
    boolean label_residues = true

    /**
     * residue score threshold for calculating predicted binary label
     */
    @ModelParam
    double residue_score_threshold = 1d

    /**
     * in calculation of residue score from neighboring SAS points:
     * <0,1> goes from 'no average, just sum' -> 'full average'
     */
    @ModelParam
    double residue_score_sum_to_avg = 0d

    /**
     * added to the cutoff distance around residue in score aggregation from SAS points
     * full distance cutoff R around residue atoms is calculated as follows:
     * R = solvent_radius + surface_additional_cutoff + residue_score_extra_dist
     */
    @ModelParam
    double residue_score_extra_dist = 0d

    /**
     * Calculate residue scores only for exposed residues (inner will have score 0)
     * => only exposed residues can be predicted as positive.
     * Makes sense only in combination with point_sampling_strategy=surface.
     */
    @ModelParam
    boolean residue_score_only_exposed = false

    /**
     * residue score transform function
     * 
     * NONE: identity .. score will be in range <0,inf)
     * SIGMOID: score will be transformed to range <0,1)
     */
    @ModelParam
    String residue_score_transform = "NONE"

    /**
     * minimum ligandability score for SAS point to be considered ligandable
     */
    @ModelParam
    double pred_point_threshold = 0.4

    /**
     * minimum z-score for SAS point to be considered ligandable (used in clustering)
     */
    @ModelParam
    double xpoint_zscore_threshold = 0.0

    /**
     * clustering strategy for clustering ligandable points into pockets
     * <p>
     * possible values: SingleLinkage, ZScore
     */
    @ModelParam
    String clustering_strategy = "SingleLinkage"


    /**
     * minimum cluster size (of ligandable points) for initial clustering
     */
    @ModelParam
    int pred_min_cluster_size = 3

    /**
     * clustering distance for ligandable clusters for second phase clustering
     */
    @ModelParam
    double pred_clustering_dist = 5

    /**
     * SAS points around ligandable points (an their score) will be included in the pocket
     */
    @ModelParam
    double extended_pocket_cutoff = 3.5

    /**
     * cutoff distance of protein surface atoms considered as part of the pocket
     */
    @ModelParam
    double pred_protein_surface_cutoff = 3.5

    /**
     * Maximum number of predicted pockets to report. 0 = no limit.
     * Takes precedence over pred_min_pockets when both are set.
     */
    @RuntimeParam
    int pred_max_pockets = 0

    /**
     * Minimum pocket score (newScore) to include in output.
     * NaN = disabled (default). Pockets below this threshold are dropped.
     */
    @RuntimeParam
    double pred_min_pocket_score = Double.NaN

    /**
     * Minimum pocket probability (probaTP) to include in output.
     * NaN = disabled (default). Requires probatp_transformer to be configured;
     * pockets without a probability score (probaTP=0) pass a threshold of 0
     * but are filtered out by any positive threshold (e.g. 0.01).
     */
    @RuntimeParam
    double pred_min_pocket_probability = Double.NaN

    /**
     * Always return at least this many pockets even if they fall below
     * score/probability thresholds. 0 = no guarantee (default).
     * Capped by the actual number of pockets found and by pred_max_pockets.
     */
    @RuntimeParam
    int pred_min_pockets = 0

    /**
     * Prefix output directory with date and time
     */
    @RuntimeParam
    boolean out_prefix_date = false

    /**
     * Place all output files in this sub-directory of the output directory
     */
    @RuntimeParam
    String out_subdir = null

    /**
     * Balance SAS point score weight by density (points in denser areas will have lower weight)
     */
    @ModelParam
    boolean balance_density = false

    /**
     * Radius for balancing of SAS point score weight
     */
    @ModelParam
    double balance_density_radius = 2

    /**
     * output detailed tables for all proteins, ligands and pockets or residues
     */
    @RuntimeParam
    boolean log_cases = true

    /**
     * cutoff for protein exposed atoms calculation (distance from SAS surface is solv.radius. + surf_cutoff)
     */
    @ModelParam
    double surface_additional_cutoff = 1.8

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    @ModelParam // training
    boolean sample_negatives_from_decoys = false

    /**
     * cutoff around ligand atoms to select negatives, 0=all
     * valid if training from whole surface (sample_negatives_from_decoys=false)
     */
    @ModelParam // training
    double train_lig_cutoff = 0

    /**
     * n, use only top-n pockets to select training instances, 0=all
     */
    @ModelParam // training
    int train_pockets = 0

    /**
     * clear primary caches (protein structures) between runs (when iterating params or seed)
     */
    @RuntimeParam // training
    boolean clear_prim_caches = false

    /**
     * clear secondary caches (protein surfaces etc.) between runs (when iterating params or seed)
     */
    @RuntimeParam // training
    boolean clear_sec_caches = false

    /**
     * Select pocket re-scoring algorithm when running in re-scoring mode (predictions=false).
     *
     * Published PRANK (2015) = "ModelBasedRescorer"
     */
    @ModelParam
    String rescorer = "ModelBasedRescorer"

    /**
     * Parameter of the PLBIndexRescorer algorithm.
     */
    @ModelParam
    boolean plb_rescorer_atomic = false

    /**
     * stop processing the dataset on the first unrecoverable error with a dataset item
     */
    @RuntimeParam
    boolean fail_fast = false

    /**
     * Fail when (X-masked) sequences in the structure and in the conservation score file do not match exactly.
     * Has effect only when fail_fast = true.
     */
    @RuntimeParam
    boolean fail_on_conserv_seq_mismatch = false

    /**
     * target class ratio of positives/negatives we train on.
     * relates to subsampling and supersampling
     */
    @RuntimeParam // training
    double target_class_ratio = 0.1

    /**
     * in training use subsampling to deal with class imbalance
     */
    @RuntimeParam // training
    boolean subsample = false

    /**
     * in training use supersampling to deal with class imbalance
     */
    @RuntimeParam // training
    boolean supersample = false

    /**
     * sort negatives desc by protrusion before subsampling
     */
    @RuntimeParam // training
    boolean subsampl_high_protrusion_negatives = false

    /**
     * don't produce prediction files for individual proteins (useful for long repetitive experiments)
     */
    @RuntimeParam
    boolean output_only_stats = false

    /**
     * produce prediction files for individual proteins in eval commands
     */
    @RuntimeParam
    boolean eval_output_prediction_files = false

    /**
     * compress results of individual ploop runs
     */
    @RuntimeParam
    boolean ploop_zip_runs = false

    /**
     * delete results of individual ploop/hopt runs
     */
    @RuntimeParam
    boolean ploop_delete_runs = false

    /**
     * logging level (TRACE/DEBUG/INFO/WARN/ERROR)
     */
    @RuntimeParam
    String log_level = "INFO"

    /**
     * print log messages to console
     */
    @RuntimeParam
    boolean log_to_console = true

    /**
     * print log messages to file (run.log in outdir)
     */
    @RuntimeParam
    boolean log_to_file = true

    /**
     * Timestamp that will be added as a prefix to each message printed to stdout ("" = no timestamp)
     * Example: "yyyy.MM.dd HH:mm:"
     */
    @RuntimeParam
    String stdout_timestamp = ""


    /**
     * compress and delete log file at the end (if log_to_file)
     */
    @RuntimeParam
    boolean zip_log_file = false

    /**
     * limit the number of proteins that used for training. random subset of proteins from the dataset is used each run in seedloop
     * 0 = no limit
     */
    @RuntimeParam // training
    int train_protein_limit = 0

    /**
     * add weights to instances to achieve target_weight_ratio (if classifier algorithm supports it)
     *
     */
    @ModelParam // training
    boolean balance_class_weights = false

    /**
     * target ratio of weighted sums of positive/negative instances when balancing class weights (balance_class_weights=true)
     */
    @ModelParam // training
    double target_class_weight_ratio = 0.1

    /**
     * produce classifier stats also for train dataset
     */
    @RuntimeParam // training
    boolean classifier_train_stats = false

    /**
     * Collect predictions for all points in the dataset.
     * Allows calculation of AUC and AUPRC classifier statistics but consumes a lot of memory.
     * (>1GB for holo4k dataset with tessellation=2)
     */
    @RuntimeParam
    boolean stats_collect_predictions = true

    /**
     * produce ROC and PR curve graphs (not fully implemented yet)
     */
    @RuntimeParam
    boolean stats_curves = false

    /**
     * Contact residues distance cutoff (see ContactResiduesPositionFeature)
     */
    @ModelParam
    double feat_crang_contact_dist = 4.0

    /**
     * probe radius for calculating accessible surface area for asa feature
     */
    @ModelParam
    double feat_asa_probe_radius = 1.4

    /**
     * probe radius for calculating accessible surface area for asa feature
     */
    @ModelParam
    double feat_asa_probe_radius2 = 3

    /**
     * radius of the neighbourhood considered in asa feature
     */
    @ModelParam
    double feat_asa_neigh_radius = 6

    /**
     * radius for calculating of the pmass feature
     */
    @ModelParam
    double feat_pmass_radius = 11

    /**
     * parameter of the pmass feature
     */
    @ModelParam
    int feat_pmass_natoms = 70

    /**
     * parameter of the pmass feature
     */
    @ModelParam
    int feat_pmass_nsasp = 40

    /**
     * selected sub-features in aa index feature
     */
    @ModelParam
    List<String> feat_aa_properties = []

    /**
     * Hyperparameter optimizer implementation ("spearmint" / "pygpgo")
     */
    @RuntimeParam // training
    String hopt_optimizer = "spearmint"

    /**
     * Python command used to run optimization child processes
     */
    @RuntimeParam // training
    String hopt_python_command = "python"

    /**
     * Spearmint home directory (containing main.py)
     */
    @RuntimeParam // training
    String hopt_spearmint_dir = ""

    /**
     * Metric to maximize in hyperparameter optimization.
     * To minimize certain metric use minus sigh prefix, e.g.: "-point_LOG_LOSS"
     */
    @RuntimeParam // training
    String hopt_objective = "DCA_4_0"

    /**
     * max number of iterations in hyperparameter optimization
     */
    @RuntimeParam // training
    int hopt_max_iterations = 1000

    /**
     * randomize seed before every training in experiments
     */
    @RuntimeParam // training
    boolean randomize_seed = false

    /**
     * Most important training/evaluation statistics that will be placed in selected_stats.csv table for easier access.
     * (all stats will be collected anyway)
     */
    @RuntimeParam // training
    List<String> selected_stats = ['DCA_4_0',
                                   'DCA_4_2',
                                   'DCA_4_4',
                                   'DCC_10_0',
                                   'DCC_10_2',
                                   'DSOR_02_0',
                                   'DSOR_02_2',
                                   'DSWO_05_0',
                                   'DSWO_05_2',
                                   'point_MCC',
                                   'point_TPX',
                                   'point_LOG_LOSS',
                                   'AVG_DSO_SUCC',
                                   'AVG_LIGCOV_SUCC',
                                   'AVG_POCKETS',
                                   'AVG_POCKET_SAS_POINTS',
                                   'AVG_POCKET_SAS_POINTS_TRUE_POCKETS',
                                   'TIME_TRAINEVAL_AVG_M']

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of true pockets" (pocket.auxInfo.zScoreTP).
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    @RuntimeParam
    String zscoretp_transformer = null

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that pocket with a given score is true pocket" (pocket.auxInfo.probaTP).
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    @RuntimeParam
    String probatp_transformer = null

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of all residue scores".
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    @RuntimeParam
    String zscoretp_res_transformer = null

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that residue with a given score is true (binding) residue".
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    @RuntimeParam
    String probatp_res_transformer = null

    /**
     * List of pocket score transformers that should be trained (i.e. fitted / inferred) during predict-eval.
     * Transformers are tied to the output distribution of the model (and its parametrization) so new transformers should be trained for every released model.
     * Examples: "ZscoreTpTransformer","ProbabilityScoreTransformer"
     */
    @RuntimeParam
    List<String> train_score_transformers = [] 

    /**
     * Train residue score transformers on a dataset during eval-predict.
     * Transformers are tied to the output distribution of the model (and its parametrization) so new transformers should be trained for every released model.
     */
    @RuntimeParam
    boolean train_score_transformers_for_residues = false


    /**
     * Train model(s) only once in the beginning.
     *
     * Respects value of loop parameter.
     * That is: if loop=10, then 10 models will be trained in the beginning
     * and then average results will be calculated for every step of ploop or hopt run.
     *
     * Relevant only for hyper-parameter optimization (ploop and hopt commands).
     * Makes sense only if optimized hyper-parameters don't influence training and feature extraction.
     */
    @RuntimeParam
    boolean hopt_train_only_once = false

    /**
     * Predict SAS point scores in the eval dataset only once.
     * Relevant only for hyper-parameter optimization (ploop and hopt commands).
     * Makes sense only in combination with hopt_train_only_once=true).
     */
    @RuntimeParam
    boolean hopt_cache_labeled_points = false


    /**
     * Identifies set of pre-calculated propensity tables for duplets/triplets features.
     *
     * Value is a directory under program resources to take propensities from
     * (resources/tables/propensities/$var/...).
     * Available: peptides/SprintT1070, peptides/SprintA870, peptides/SprintALL
     * (also ions/* and dna/* subsets used by the respective configs).
     *
     * TODO: move to dist dir on release
     */
    @ModelParam
    String feat_propensity_tables = "peptides/SprintT1070"


    /**
     * When identifying which protein chains are peptides consider provided binary residue labeling (that comes with the dataset).
     */
    @ModelParam // training
    boolean identify_peptides_by_labeling = false

    /**
     * KD-tree implementation: "AtomKdTreeV2" (immutable, SoA) or "AtomKdTreeV1" (Rednaxela, mutable, generic)
     */
    @RuntimeParam
    String kdtree_implementation = "AtomKdTreeV2"

    /**
     * Atoms size threshold for using KD-tree in cutoutSphere routine
     */
    @RuntimeParam
    int use_kdtree_cutout_sphere_thrashold = 150

    /**
     * Directories where to find csv files for csv_file_atom_feature.
     */
    @ModelParam
    List<String> feat_csv_directories = []

    /**
     * Names of enabled value columns from csv files used by csv_file feature. Value columns not listed here are ignored.
     */
    @ModelParam
    List<String> feat_csv_columns = []


    /**
     * If true then csv_file feature ignores:
     * <ul>
     *   <li> missing csv files for proteins
     *   <li> missing value columns
     *   <li> missing rows for atoms or residues
     * <ul>
     */
    @ModelParam
    boolean feat_csv_ignore_missing = false

    /**
     * Structural motifs for stmotif feature.
     * e.g.: C2H2 D1H1 C4 H2
     */
    @ModelParam
    List<String> feat_stmotif_motifs = ["C2H2","C4","C3H1","E1H2","C2H1","H3","D1H2","C3","D1H1","E1H1","C1H3","C2","H2"]

    /**
     * When matching motifs, consider all residues within feat_stmotif_radius around the SAS point.
     * If false, only closest n residues are considered and must match exactly (n = lenght of a motif).
     */
    @ModelParam
    boolean feat_stmotif_useradius = true

    /**
     * Radius related to feat_stmotif_useradius param.
     */
    @ModelParam
    double feat_stmotif_radius = 4d

//===========================================================================================================//
// vdW Methyl Energy Feature Parameters
//===========================================================================================================//

    /**
     * Lennard-Jones cutoff radius for vdW methyl energy feature (Angstrom)
     */
    @ModelParam
    double energy_rc = 9.0

    /**
     * Lennard-Jones switch-on radius for vdW methyl energy feature (Angstrom)
     */
    @ModelParam
    double energy_ron = 7.0

    /**
     * Methyl probe sigma parameter for vdW energy feature (Angstrom)
     */
    @ModelParam
    double energy_probe_sigma = 3.75

    /**
     * Methyl probe epsilon parameter for vdW energy feature (kcal/mol)
     */
    @ModelParam
    double energy_probe_epsilon = 0.12

    /**
     * Minimum allowed distance to avoid singularities in vdW energy calculation (Angstrom)
     */
    @ModelParam
    double energy_min_r = 1.8

    /**
     * Policy for handling missing element parameters: skip | error | fallback
     */
    @ModelParam
    String energy_missing_elem_policy = "skip"

    /**
     * Fallback sigma parameter for missing elements (Angstrom)
     */
    @ModelParam
    double energy_fallback_sigma = 3.5

    /**
     * Fallback epsilon parameter for missing elements (kcal/mol)
     */
    @ModelParam
    double energy_fallback_epsilon = 0.10

    /**
     * Energy cloud radius for averaging energy feature on the surface (Angstrom)
     */
    @ModelParam
    double energy_cloud_radius = 3.5

        /**
     * Energy cloud radius for averaging energy feature on the surface (Angstrom)
     */
    @ModelParam
    double energy_cloud_radius2 = 6

    @ModelParam
    boolean xenergy_cloud2_layered = true

    @ModelParam
    int xenergy_tessellation = 2

    /**
     * 0 - no stdev, 1 - abs stdev, 2 - relative stdev
     */
    @ModelParam
    int xenergy_cloud_stdev_type = 0

    /**
     * solvent radius for energy probes
     */
    @ModelParam
    double xenergy_solvent_radius = 1.6

    /**
     * Effective dielectric for the Coulomb term in the energy2 probe suite
     * (CATION_SP). Higher values damp electrostatics, modelling implicit
     * solvent screening. Default 12 is a midrange protein-interior value.
     * Per-probe σ/ε/charge/HB-r0 remain baked in (see audit follow-ups).
     */
    @ModelParam
    double energy2_dielectric = 12.0

    /**
     * Master switch for the Coulomb term across all energy2/energy3 probes.
     * When false, CATION_SP collapses to pure LJ. Uses AMBER ff14SB partial
     * charges via PartialChargeTable.
     */
    @ModelParam
    boolean energy2_enable_coulomb = true

    /**
     * When true, the aromatic-ring probe (energy2-aromatic-ring) only interacts
     * with atoms belonging to aromatic residues (PHE, TYR, TRP, HIS).
     * When false (default), it interacts with all protein heavy atoms.
     */
    @ModelParam
    boolean energy2_aromatic_only = false

    /**
     * When true, MethylEnergyFeature (energy-ch3) fetches neighbour atoms at
     * energy_rc (matching the calculator's switching-function cutoff) instead
     * of the global neighbourhood_radius. This both widens the radius (8 → 9 Å
     * by default) and switches the atom source from exposed-only surface atoms
     * to all protein heavy atoms (matching the energy-cloud variants).
     */
    @ModelParam
    boolean energy_use_calculator_cutoff = false

//===========================================================================================================//

    /**
     *
     */
    @RuntimeParam
    String chains = "keep"

    @RuntimeParam
    String out_format = "keep"

    @RuntimeParam
    String out_file = null

    /**
     * When using Apo-Holo train dataset enable Apo structures, if false use Holo structures instead.
     */
    @RuntimeParam
    boolean apoholo_use_for_train = false

    /**
     * When using Apo-Holo eval/main dataset enable Apo structures, if false use Holo structures instead.
     */
    @RuntimeParam
    boolean apoholo_use_for_eval = false

    /**
     * limit number of loaded pockets predicted by other methods per protein. 0 = no limit
     */
    @RuntimeParam
    int loaded_pockets_limit = 0

    /**
     * Add random rotations of each protein (from training dataset) to the training dataset
     */
    @RuntimeParam // training
    int train_random_rotated_copies = 0

    /**
     * Remove near-duplicate points from computed solvent accessible surface.
     */
    @RuntimeParam
    boolean surface_sparsify = true

    /**
     * Solvent-accessible-surface generation strategy:
     *  "cdk" | "faster" | "packed" | "faster_distinct" | "packed_distinct" | "packed_distinct_v2" | "packed_distinct_v3" | "packed_distinct_v4" | "float_distinct" | "float_distinct_v2".
     *  - cdk:               CDK NumericalSurface (with metal van der Waals fallback)
     *  - faster:            optimized FasterNumericalSurface (current default)
     *  - packed:            flat-store + zero-copy delivery (bit-exact to faster, lower allocation / faster point handling)
     *  - faster_distinct:   faster pipeline, one point per distinct direction (no ~5.7x coincident dups), area bit-exact, needs no sparsification
     *  - packed_distinct:   packed engine producing the same de-duplicated, area-exact distinct surface
     *  - packed_distinct_v2: SIMD weighted dedup + right-sized store; bit-exact to packed_distinct, faster
     *  - packed_distinct_v3: packed_distinct_v2 + SIMD-vectorized neighbor build; bit-exact to v2, ~4-5% faster at tess 2
     *  - packed_distinct_v4: packed_distinct_v3 + fused single-pass weighted scan; bit-exact to v3, ~3% faster at tess 2 / ~5% tess 3 / ~8-10% tess 4 (DEFAULT)
     *  - float_distinct:    v2 pipeline with single-precision occlusion verdict; APPROXIMATE (area within ~1.4e-5).
     *                       Superseded by float_distinct_v2 (which is faster at the same fidelity); kept as a baseline.
     *  - float_distinct_v2: float_distinct PLUS a single-precision SIMD neighbor build; APPROXIMATE, the fastest variant
     *                       (measured ~3% over v3 at tess 2 / 16 threads on holo4k). WARNING: sound ONLY at tess 2 -
     *                       the float occlusion scan collapses ~23-32x at tess >= 3 under threads (Vector-API deopt).
     * Default is "packed_distinct_v4": it is bit-for-bit identical to packed_distinct_v3 (same distinct SAS
     * point set and areas, just a faster fused scan), which in turn yields the same SAS points as the
     * historical default after sparsification, so POCKET predictions are unchanged vs faster/cdk (verified
     * 0/4009 differ on holo4k); per-residue scores can differ at the ~1e-4 level on rare near-duplicate
     * boundary points. It is substantially faster and needs no sparsification pass. Set to empty ("") to
     * fall back to the deprecated {@link #use_optimized_surface} (true -> faster, false -> cdk).
     */
    @RuntimeParam
    String surface_strategy = "packed_distinct_v4"

    /**
     * @deprecated Use {@link #surface_strategy} instead. Honored only when surface_strategy is empty:
     * true -> "faster", false -> "cdk".
     */
    @Deprecated
    @RuntimeParam
    boolean use_optimized_surface = true

    /**
     * Detect and collapse alternate-conformation chains: microheterogeneity deposited as separate whole
     * chains that are each (almost) entirely tagged with a single non-blank altLoc letter and that
     * geometrically superimpose on a primary chain (e.g. PDB 6een, whose chains A/B/C/D are the same
     * polymer in 4 conformations, ~0.002 A apart). Such redundant chains otherwise multiply the surface
     * and feature input (6een: ~4x atoms), inflating SAS point counts and pocket scores. When true, for
     * each cluster of mutually-overlapping uniform-altLoc chains only the primary conformation (lowest
     * altLoc letter, or a blank-altLoc chain) is kept; the others are dropped before residues/atoms are
     * built. Ordinary within-residue altLocs are already collapsed by the parser and are unaffected; only
     * the rare whole-chain-alternate pattern is touched (~3/10000 structures on a PDB-wide sample, 1 of
     * them materially). The geometric overlap test is the safety guard: chains that occupy distinct space
     * (genuine homo-oligomer copies, mislabeled or not) never overlap and are always kept. Off => legacy
     * behavior (all alternate chains loaded). See {@link cz.siret.prank.geom.AlternateChainReducer}.
     */
    @RuntimeParam
    boolean reduce_alternate_conformation_chains = true

    /**
     * Command used to run fpocket when running 'prank fpocket-rescore'. Can contain custom fpocket arguments.
     */
    @RuntimeParam
    String fpocket_command = "fpocket"

    /**
     * Keep fpocket output when running 'prank fpocket-rescore'.
     */
    @RuntimeParam
    boolean fpocket_keep_output = true

    /**
     * Run fpocket ad-hoc before rescoring/evaluation (in 'rescore' and 'eval-rescore' commands).
     * When true, fpocket is executed for each protein and its results are used as input pockets.
     */
    @RuntimeParam
    boolean run_fpocket_ad_hoc = false


    /**
     * accepted values: "sas_pts_getcleft_pdb", "grid_pts_getcleft_pdb"
     */
    @RuntimeParam // training
    List<String> extra_output = []

//===========================================================================================================//
// Derived parameters
//===========================================================================================================//

    /**
     * Should be (slightly above) the distance of solvent exposed atoms to SAS points.
     */
    double getSasCutoffDist() {
        solvent_radius + surface_additional_cutoff
    }

    /**
     * Derive point labeling from ligands or from labeled residues.
     *
     * @see this.ligand_derived_point_labeling
     */
    boolean derivePointLabelingFromLigands() {
        !predict_residues || ligand_derived_point_labeling || identify_peptides_by_labeling
    }

    int getEffectiveTrainTessellation() {
        (train_tessellation == 0) ? tessellation : train_tessellation
    }

    int getEffectiveTrainTessellationNegatives() {
        (train_tessellation_negatives == 0) ? getEffectiveTrainTessellation() : train_tessellation_negatives
    }

    List<String> getSelectedFeatures() {
        return (features + extra_features).unique()
    }

    double getPointScorePow() {
        if (predict_residues) {
            return (residue_point_score_pow > 0) ? residue_point_score_pow : point_score_pow
        } else {
            return point_score_pow 
        }
    }


    /**
     * If we are logging to console, we don't want to write to stdout to avoid duplicate
     * messages (as raw string and [INFO] log line).
     */
    boolean writeToStdOut() {
        return !log_to_console
    }


//===========================================================================================================//

    /**
     * This method is here so the program version is included in toString() for Params object.
     */
    String getVersion() {
        Main.getVersion()
    }

    /**
     * location of P2Rank installation directory (i.e. directory where the binary and configs and models are / unpacked distro directory)
     */
    String installDir // TODO refactor

//===========================================================================================================//

    /**
     * parameter names and aliases that are not fields in Params class
     * TODO move to better invalid param checking logic
     */
    static final Set<String> EXTRA_PARAMS_AND_ALIASES = ImmutableSet.of(
            'f', 'o',
            'help', 'h',
            'model', 'm',
            'config', 'c',
            'train','t',
            'eval', 'e',
            'label', 'l',
            'v'
    )

    /**
     * Apply parameter values from the command line
     */
    public updateFromCommandLine(CmdLineArgs args) {

        checkForInvalidArgs(args)
        applyCmdLineArgs(args)

        // processing of special params
        initDependentParams()
    }

    boolean isVaidParamName(String pname) {
        this.metaClass.getMetaProperty(pname) != null
    }

    boolean isVaidParamNameOrAlias(String pname) {
        EXTRA_PARAMS_AND_ALIASES.contains(pname) || isVaidParamName(pname)
    }

    private checkForInvalidArgs(CmdLineArgs args) throws PrankException {
        List<String> argNames = args.namedArgsAndSwitches

        for (String argName : argNames) {
            if (!isVaidParamNameOrAlias(argName)) {
                throw new PrankException("Invalid parameter name: " + argName)
            }
        }
    }

    /**
     * Some parameters have special values that they inherit or otherwise depend on other parameters.
     * ans should be re-initialized any time parameters are loaded.
     */
    void initDependentParams() {
        if (!parallel) {
            threads = 1
            rf_threads = 1
        } else if (threads==1) {
            parallel = false
        }
    }

    @CompileDynamic
    private void applyCmdLineArgs(CmdLineArgs args) {

        boolean filterRanged = args.hasListParams

        Params me = this
        for (String propName : me.properties.keySet()) {
            if (args.namedArgMap.containsKey(propName)) {
                String val = args.get(propName)

                boolean skip = false
                if (filterRanged && ListParam.isIterativeArgValue(propName, val)) {
                    skip = true
                }

                if (!skip) {
                    trySetParam(propName, val)
                }
            } else if (args.switches.contains(propName)) {
                me."$propName" = true
            }
        }
    }

    @CompileDynamic
    public trySetParam(String propertyName, Object value) {
        try {
            setParam(propertyName, value)
        } catch (Exception e) {
            throw new PrankException("Failed to set parameter value. Name: $propertyName, value: '$value'. Reason: " + e.message, e)
        }
    }

    @CompileDynamic
    public setParam(String propertyName, Object value) {

        log.debug "Setting parameter '$propertyName' to '$value'"

        String pname = propertyName
        Object me = this
        Object pv = me."$pname"

        // TODO assign based on real property type not the type of property value bc. it may be null
        
        if (value == null || pv == null) {
            me."$pname" = value
        } else if (pv instanceof String) {
            String v = (String) value
            if (v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length()-1)
            }
            me."$pname" = v
        } else {
            Class propClass = pv.class

            log.debug "pv class: {}", propClass

            if (pv instanceof List) {
                if (value instanceof List) {
                    me."$pname" = value
                } else {
                    me."$pname" = Sutils.parseList(value)
                }
            } else if (pv instanceof Boolean) {
                me."$pname" = parseBoolean( value )
            } else if (pv instanceof Integer) {
                me."$pname" = new Double(""+value).intValue()
            } else {
                me."$pname" = propClass.valueOf( value )
            }

        }

        log.debug "Property value: '$propertyName' = '${me."$pname"}'"
    }

    private boolean parseBoolean(Object value) {
        if ("false"==value) return false
        if ("0"==value)     return false
        if ("0.0"==value)   return false
        if (0d==value)      return false
        if (0i==value)      return false

        if ("true"==value) return true
        if ("1"==value)    return true
        if ("1.0"==value)  return true
        if (1d==value)     return true
        if (1i==value)     return true

        throw new IllegalArgumentException("Invalid boolean value '$value'")
    }

    @Override
    String toString() {
        return Sutils.toStr(this).replace('=', ' = ') + "\n"
    }

}
