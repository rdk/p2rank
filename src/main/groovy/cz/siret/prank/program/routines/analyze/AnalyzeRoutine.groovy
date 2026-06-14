
package cz.siret.prank.program.routines.analyze

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.*
import cz.siret.prank.domain.loaders.ExplicitSitesIndex
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.export.FastaExporter
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.features.implementation.table.AtomTableFeature
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.AtomDeduplicator
import cz.siret.prank.geom.Struct
import cz.siret.prank.geom.Surface
import cz.siret.prank.geom.SurfaceStrategy
import cz.siret.prank.geom.kdtree.AtomKdTree
import org.openscience.cdk.interfaces.IAtomContainer

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.results.Evaluation
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import cz.siret.prank.program.routines.predict.output.grid.PocketGridAnalysis
import cz.siret.prank.program.routines.predict.output.grid.PocketGridBuilder
import cz.siret.prank.program.routines.predict.output.grid.PocketGridConfig
import cz.siret.prank.program.ml.Model
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs
import cz.siret.prank.program.routines.predict.output.grid.fill.PocketShapeFillerRegistry
import cz.siret.prank.program.visualization.RenderingModel
import cz.siret.prank.program.visualization.renderers.NewPymolRenderer
import cz.siret.prank.utils.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.ResidueNumber

import cz.siret.prank.program.params.Params

import static cz.siret.prank.domain.Dataset.LigandDefinition
import static cz.siret.prank.geom.Struct.getAuthorId

import javax.annotation.Nullable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import static cz.siret.prank.geom.SecondaryStructureUtils.assignSecondaryStructure
import static cz.siret.prank.utils.Cutils.newSynchronizedList
import static cz.siret.prank.utils.Formatter.format
import static cz.siret.prank.utils.Futils.writeFile
import static java.util.Collections.unmodifiableMap

/**
 * Various tools for analyzing datasets.
 * Routine with sub-commands.
 */
@Slf4j
@CompileStatic
class AnalyzeRoutine extends Routine {

    String subCommand
    String label
    @Nullable Dataset dataset

    AnalyzeRoutine(CmdLineArgs args, Main main) {
        super(null)

        subCommand = args.popFirstUnnamedArg() // next if present should be dataset
        if (!commandRegister.containsKey(subCommand)) {
            write "Invalid analyze sub-command '$subCommand'! Available commands: " + commandRegister.keySet()
            throw new PrankException("Invalid command.")
        }

        if (!args.unnamedArgs.empty || args.get('f') != null) {
            dataset = main.loadDatasetOrFile()
        }

        label = "analyze_" + subCommand + (dataset!=null ? "_"+dataset.label : "")
        outdir = main.findOutdir(label)
        main.configureLoggers(outdir)
    }

    void execute() {
        write "executing analyze $subCommand command"

        writeParams(outdir)
        commandRegister.get(subCommand).call()

        write "results saved to directory [${Futils.absPath(outdir)}]"
    }
    
 //===========================================================================================================//
 // Sub-Commands
 //===========================================================================================================//

    final Map<String, Closure> commandRegister = unmodifiableMap([
        "residues" : { cmdResidues() },
        "binding-residues" : { cmdBindingResidues() },
        "binding-sites" : { cmdBindingSites() },
        "binding-site-centers" : { cmdBindingSiteCenters() },
        "labeled-residues" : { cmdLabeledResidues() },
        "aa-propensities" : { cmdAaPropensities() },
        "atomtype-propensities" : { cmdAtomTypePropensities() },
        "aa-surf-seq-duplets" : { cmdAaSurfSeqDuplets() },
        "aa-surf-seq-triplets" : { cmdAaSurfSeqTriplets() },
        "all-propensities" : { cmdAllPropensities() },
        "conservation" : { cmdConservation() },
        "proteins" : { cmdProteins() },
        "parse-proteins" : { cmdParseProteins() },
        "chains" : { cmdChains() },
        "chains-residues" : { cmdChainsResidues() },
        "fasta-raw" : { cmdFastaRaw() },
        "fasta-masked" : { cmdFastaMasked() },
        "peptides" : { cmdPeptides() },
        "convert-dataset-to-atomid" : { cmdConvertContactresDataset() },
        "print-volsite-table" : { print_volsite_table() },
        "cofactors" : { cmdCofactors() },
        "surface-strategies" : { cmdSurfaceStrategies() },
        "surface-density" : { cmdSurfaceDensity() },
        "pocket-grid-overlap" : { cmdPocketGridOverlap() },
        "pocket-grid-cavity-fit" : { cmdPocketGridCavityFit() },
        "pocket-grid-ligand-fit" : { cmdPocketGridLigandFit() },
        "pocket-grid-rule-compare" : { cmdPocketGridRuleCompare() }
    ])

//===========================================================================================================//

    /**
     * Write out residue details
     *
     * Similar to cmdChainsResidues but add binding info and produces only one csv per protein.
     */
    void cmdResidues() {

        double residueCutoff = params.ligand_protein_contact_distance

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            p.assignSecondaryStructure()

            Atoms bindingAtoms = p.proteinAtoms.cutoutShell(p.allRelevantLigandAtoms, residueCutoff)
            Set<Residue> bindingResidues = p.residues.getDistinctForAtoms(bindingAtoms).toSet()

            StringBuffer csv = new StringBuffer("chain_name, seq_num, ins_code, key, chain_mmcif_id, atoms, sec_struct_type, is_binding\n")
            for (ResidueChain chain : p.residueChains) {
                for (Residue res : chain.residues) {
                    ResidueNumber rn = res.residueNumber
                    int binding = bindingResidues.contains(res) ? 1 : 0
                    String insCode = (rn.insCode != null) ? ""+rn.insCode : "-"
                    csv << "$rn.chainName, $rn.seqNum, $insCode, $res.key, $res.chainMmcifId, $res.atoms.count, $res.secStruct, $binding\n"
                }
            }

            String outf = "$outdir/${p.name}_residues.csv"
            writeFile outf, csv.toString()
        }

        write res.writeErrorsAndGetSummary(outdir)
    }

    // ============================================================================ //
    // pocket-grid-overlap
    // ============================================================================ //

    /** One reported overlapping pocket pair within a single protein. */
    @CompileStatic
    private static class OverlapRow {
        String protein
        int nPockets
        int nPoints
        int rankA, rankB
        int sizeA, sizeB
        int overlap
        int union
        double jaccard         // |A∩B| / |A∪B|        (on filled sets)
        double containment     // |A∩B| / min(|A|,|B|)  on FILLED sets; ~1.0 ⇒ smaller ⊆ larger
        double containmentRaw  // same, on the RAW shells (pre-fill) — the geometric baseline
        boolean subset         // containment(filled) >= threshold
        boolean fillDriven     // subset under fill but NOT under raw shells: the fill caused the engulfment
    }

    /**
     * SCAFFOLD. Builds the pocket grid for every protein in the dataset (same
     * pipeline as production export: {@link PocketGridBuilder#build} with
     * {@link PocketGridConfig#fromParams}) and reports, for every pair of
     * pockets, how many grid points they share.
     *
     * <p>Motivation: {@code morph_closing} dilates each pocket's raw shell
     * independently against a shared lattice envelope. SAS points are partitioned
     * one-per-pocket, so the RAW shells can only overlap in a thin interface band.
     * Any large overlap is therefore introduced by the fill stage. This command
     * quantifies that across a dataset so we can pick concrete problematic
     * proteins to anchor regression tests.
     *
     * <p>Run it twice to A/B the fill:
     * <pre>
     *   prank analyze pocket-grid-overlap dataset.ds -pocket_grid_fill morph_closing
     *   prank analyze pocket-grid-overlap dataset.ds -pocket_grid_fill none
     * </pre>
     *
     * <p>Outputs (in outdir):
     * <ul>
     *   <li>{@code pocket_grid_overlap_pairs.csv} -- one row per overlapping
     *       pocket pair, sorted worst-first by containment.</li>
     *   <li>{@code pocket_grid_overlap_worst.csv} -- top {@code TOP_N} subset-like
     *       pairs, the shortlist for choosing unit-test cases.</li>
     * </ul>
     */
    void cmdPocketGridOverlap() {
        if (dataset == null) {
            throw new PrankException("analyze pocket-grid-overlap requires a dataset argument")
        }

        // Flag a pair as "subset-like" when the smaller pocket is almost entirely
        // contained in the larger one. 0.9 is a starting point; tune from the output.
        final double SUBSET_CONTAINMENT = 0.9d
        final int TOP_N = 50

        PocketGridConfig config = PocketGridConfig.fromParams(params)
        log.info "pocket-grid-overlap using config: {}", config

        ConcurrentLinkedQueue<OverlapRow> rows = new ConcurrentLinkedQueue<>()
        // Fill-volume telemetry: lets us tell "low overlap because the fill is tuned
        // well" apart from "low overlap because the fill does nothing". A good fill
        // keeps mean pocket size well above the raw shell while not over-dilating.
        AtomicLong totalPocketsAcc = new AtomicLong()
        AtomicLong totalAssignedAcc = new AtomicLong()

        def res = forEachPrediction(true) { Protein protein, List<? extends Pocket> pockets, Dataset.Item item ->
            if (pockets.size() < 2) return   // nothing to compare

            PocketGrid grid = PocketGridBuilder.build(protein, pockets, config)
            int nPoints = grid.allPoints.count

            // Snapshot each pocket's filled + raw BitSet once (avoid re-fetching in the n² loop).
            // The raw shells are a first-class build output (no second fill=none build needed),
            // so we can tell fill-DRIVEN engulfment apart from pre-existing geometry.
            int np = pockets.size()
            BitSet[] sets = new BitSet[np]
            BitSet[] raws = new BitSet[np]
            int[] ranks = new int[np]
            long assignedHere = 0
            for (int i = 0; i < np; i++) {
                ranks[i] = pockets[i].rank
                sets[i] = grid.indicesForPocket(ranks[i])
                raws[i] = grid.rawShellForPocket(ranks[i])
                assignedHere += sets[i].cardinality()
            }
            totalPocketsAcc.addAndGet(np)
            totalAssignedAcc.addAndGet(assignedHere)

            for (int i = 0; i < np; i++) {
                int sizeA = sets[i].cardinality()
                if (sizeA == 0) continue
                for (int j = i + 1; j < np; j++) {
                    int sizeB = sets[j].cardinality()
                    if (sizeB == 0) continue

                    // BitSet set-algebra in Java (PocketGridAnalysis) — avoids the
                    // Groovy @CompileStatic `.and()` no-op trap; see PocketGrid javadoc.
                    int overlap = PocketGridAnalysis.intersectionCount(sets[i], sets[j])
                    if (overlap == 0) continue   // report only overlapping pairs

                    int union = sizeA + sizeB - overlap
                    double containment = overlap / (double) Math.min(sizeA, sizeB)

                    // raw-shell containment of the same pair (0 if either raw shell is empty)
                    int rawA = raws[i].cardinality(), rawB = raws[j].cardinality()
                    double containmentRaw = (rawA == 0 || rawB == 0) ? 0d :
                            PocketGridAnalysis.intersectionCount(raws[i], raws[j]) / (double) Math.min(rawA, rawB)

                    boolean subset = containment >= SUBSET_CONTAINMENT
                    OverlapRow row = new OverlapRow(
                            protein: protein.name,
                            nPockets: np,
                            nPoints: nPoints,
                            rankA: ranks[i], rankB: ranks[j],
                            sizeA: sizeA, sizeB: sizeB,
                            overlap: overlap, union: union,
                            jaccard: overlap / (double) union,
                            containment: containment,
                            containmentRaw: containmentRaw,
                            subset: subset,
                            // fill-driven: engulfed under fill, but the raw shells were NOT subset-like.
                            fillDriven: subset && containmentRaw < SUBSET_CONTAINMENT)
                    rows.add(row)
                }
            }
        }

        List<OverlapRow> all = new ArrayList<>(rows)
        // Worst first: subset-like and most-contained pairs at the top.
        all.sort { OverlapRow a, OverlapRow b -> Double.compare(b.containment, a.containment) }

        writeFile "$outdir/pocket_grid_overlap_pairs.csv", toCsv(all)

        // Worst cases shortlist = fill-driven engulfment (the actual pathology), worst-first.
        List<OverlapRow> fillDrivenRows = all.findAll { it.fillDriven }
        writeFile "$outdir/pocket_grid_overlap_worst.csv", toCsv(fillDrivenRows.take(TOP_N))

        int subsetPairs = all.count { it.subset } as int
        int fillDrivenPairs = fillDrivenRows.size()
        int rawSubsetPairs = subsetPairs - fillDrivenPairs   // subset already present in the raw shells (benign / upstream)
        int proteinsFillDriven = fillDrivenRows*.protein.toSet().size()
        long totalPockets = totalPocketsAcc.get()
        double meanPocketSize = totalPockets > 0 ? (totalAssignedAcc.get() / (double) totalPockets) : 0d
        write "pocket-grid-overlap: ${all.size()} overlapping pocket pairs; " +
              "${subsetPairs} subset-like (containment >= ${SUBSET_CONTAINMENT}), of which " +
              "${fillDrivenPairs} FILL-DRIVEN (raw shells not subset) across ${proteinsFillDriven} proteins, " +
              "${rawSubsetPairs} pre-existing in raw shells (benign/upstream)"
        write "  fill volume: ${totalPockets} pockets, mean ${String.format(java.util.Locale.ROOT, '%.0f', meanPocketSize)} grid points/pocket"
        write "  fill-driven worst cases -> ${outdir}/pocket_grid_overlap_worst.csv"
        write res.writeErrorsAndGetSummary(outdir)
    }

    private static String toCsv(List<OverlapRow> rows) {
        StringBuffer csv = new StringBuffer(
                "protein, n_pockets, n_grid_points, rank_a, rank_b, size_a, size_b, " +
                "overlap, union, jaccard, containment, containment_raw, subset, fill_driven\n")
        for (OverlapRow r : rows) {
            csv << String.format(java.util.Locale.ROOT,
                    "%s, %d, %d, %d, %d, %d, %d, %d, %d, %.4f, %.4f, %.4f, %d, %d\n",
                    r.protein, r.nPockets, r.nPoints, r.rankA, r.rankB, r.sizeA, r.sizeB,
                    r.overlap, r.union, r.jaccard, r.containment, r.containmentRaw,
                    r.subset ? 1 : 0, r.fillDriven ? 1 : 0)
        }
        return csv.toString()
    }

    // ============================================================================ //
    // pocket-grid-cavity-fit
    // ============================================================================ //

    /** Per (protein, fill, R_large) confusion counts of assigned grid points vs the cavity mask. */
    @CompileStatic
    private static class CavityFitRow {
        String fillLabel
        double rLarge
        long assigned     // |points assigned to any pocket under this fill|
        long buried       // |cavity points (under the large-probe lid)|
        long tp           // |assigned ∩ buried|
    }

    /** One fill strategy to score: display label + registry name + its typed knobs. */
    @CompileStatic
    private static class FillSpec {
        String label, strategy
        FillKnobs knobs
        FillSpec(String label, String strategy, FillKnobs knobs) {
            this.label = label; this.strategy = strategy; this.knobs = knobs
        }
    }

    /** The fill strategies scored by pocket-grid-cavity-fit and pocket-grid-ligand-fit. */
    private static final List<FillSpec> DEFAULT_FILLS = [
            new FillSpec('none',          'none',          new FillKnobs.None()),
            new FillSpec('closing_r1',    'closing',       FillKnobs.Closing.symmetric(1)),
            new FillSpec('closing_r2',    'closing',       FillKnobs.Closing.symmetric(2)),
            new FillSpec('dilate2_erode1','closing',       new FillKnobs.Closing(2, 1)),  // asymmetric: net +1 outward
            new FillSpec('morph_n10',     'morph_closing', new FillKnobs.Morph(10, 10)),  // current morph default (min_neighbors=10)
            new FillSpec('morph_n14',     'morph_closing', new FillKnobs.Morph(14, 10)),  // candidate (stricter, min_neighbors=14)
    ].asImmutable() as List<FillSpec>

    /**
     * Base config with fill=none, so {@code indicesForPocket()} returns the RAW shells;
     * the cavity-fit and ligand-fit analyses then apply each {@link FillSpec} in that same
     * index space. Reads the current {@code pocket_grid_*} params except fill.
     */
    private PocketGridConfig baseNoneConfig() {
        new PocketGridConfig(
                params.pocket_grid_spacing, params.pocket_grid_max_dist, params.pocket_grid_atom_buffer,
                params.pocket_grid_assign_cutoff, params.pocket_grid_assigner, 'none', new FillKnobs.None())
    }

    /**
     * Shared scaffold for the pocket-grid analyses: load the model + feature extractor once,
     * then for each dataset item run the predictor and invoke
     * {@code body(protein, outputPockets, item)}. Items that predict no pockets are skipped.
     *
     * @param ignoreLigands set the global ligand-skip switch (true when the analysis only
     *                      needs predicted pockets, false when it scores against ligands)
     */
    private Dataset.Result forEachPrediction(boolean ignoreLigands, Closure body) {
        LoaderParams.ignoreLigandsSwitch = ignoreLigands
        Model model = Model.load(Main.findModel(params.installDir, params))
        FeatureExtractor extractor = FeatureExtractor.createFactory()
        return dataset.processItems { Dataset.Item item ->
            PredictionPair pair = item.predictionPair
            new ModelBasedRescorer(model, extractor).reorderPockets(pair.prediction, item.context)
            List<? extends Pocket> pockets = pair.prediction.outputPockets
            if (pockets == null || pockets.isEmpty()) return
            body.call(pair.protein, pockets, item)
        }
    }

    /**
     * Calibrate the pocket-grid fill against a structure-derived ground truth.
     *
     * <p>A large rolling probe cannot enter a pocket: its solvent-accessible surface
     * bridges (smooths) over the mouth. So for each candidate grid point we get a
     * binary label, independent of the prediction pipeline:
     * <ul>
     *   <li><b>buried / cavity</b>: no large-probe surface point lies within
     *       {@code R_large} of it (the large probe cannot reach it) -> it legitimately
     *       belongs to the pocket volume.</li>
     *   <li><b>open / solvent</b>: the large probe reaches it -> it is bulk-solvent-side
     *       and should NOT count toward the pocket.</li>
     * </ul>
     *
     * <p>Each fill's assigned points (union over pockets) are then scored against that
     * mask: <b>precision</b> = fraction of assigned points that are truly buried
     * (low precision = over-fill, bleeding into solvent), <b>recall</b> = fraction of
     * buried cavity captured (low recall = under-fill, hollow shell), <b>IoU</b> the
     * balance. Swept over {@code R_large in {3,4,5} Å} (the cavity-depth scale).
     *
     * <p>The candidate point set and the large-probe surface come straight from the
     * existing machinery: {@link PocketGridBuilder} (grid sampling) and
     * {@link Surface#computeAccessibleSurface} at a larger {@code solventRadius}.
     */
    void cmdPocketGridCavityFit() {
        if (dataset == null) {
            throw new PrankException("analyze pocket-grid-cavity-fit requires a dataset argument")
        }

        final double[] R_LARGE = [3.0d, 4.0d, 5.0d] as double[]
        // Score every fill strategy in DEFAULT_FILLS (raw shell, two true-closing radii,
        // an asymmetric closing, and the two morph candidates).
        final List<FillSpec> FILLS = DEFAULT_FILLS

        PocketGridConfig baseConfig = baseNoneConfig()
        log.info "pocket-grid-cavity-fit base config: {}, R_large sweep: {}", baseConfig, R_LARGE

        ConcurrentLinkedQueue<CavityFitRow> rows = new ConcurrentLinkedQueue<>()

        def res = forEachPrediction(true) { Protein protein, List<? extends Pocket> pockets, Dataset.Item item ->
            PocketGrid grid = PocketGridBuilder.build(protein, pockets, baseConfig)
            int n = grid.allPoints.count
            if (n == 0) return

            // assigned[fill] = union over pockets of that fill's per-pocket points (same
            // index space). Set-algebra in Java (PocketGridAnalysis) — see PocketGrid javadoc.
            BitSet[] assigned = new BitSet[FILLS.size()]
            for (int f = 0; f < FILLS.size(); f++) {
                FillSpec spec = FILLS[f]
                assigned[f] = PocketGridAnalysis.unionFilled(grid, pockets,
                        PocketShapeFillerRegistry.get(spec.strategy), spec.knobs)
            }

            // Cavity mask per R_large: a grid point is buried iff the large-probe surface
            // does not come within R_large of it (the per-point loop runs in Java).
            for (double rLarge : R_LARGE) {
                Surface largeSurf = Surface.computeAccessibleSurface(
                        protein.proteinAtoms, rLarge, params.tessellation)
                BitSet buried = PocketGridAnalysis.buriedMask(grid.allPoints, largeSurf.points, rLarge)

                for (int f = 0; f < FILLS.size(); f++) {
                    CavityFitRow row = new CavityFitRow(
                            fillLabel: FILLS[f].label, rLarge: rLarge,
                            assigned: assigned[f].cardinality(),
                            buried: buried.cardinality(),
                            tp: PocketGridAnalysis.intersectionCount(assigned[f], buried))
                    rows.add(row)
                }
            }
        }

        // Micro-average: sum confusion counts across proteins, then derive rates.
        Map<String, long[]> agg = new LinkedHashMap<>()   // key "fill@R" -> [assigned, buried, tp]
        for (CavityFitRow r : rows) {
            String key = "${r.fillLabel}@${r.rLarge}"
            long[] a = agg.get(key)
            if (a == null) { a = new long[3]; agg.put(key, a) }
            a[0] += r.assigned; a[1] += r.buried; a[2] += r.tp
        }

        DataTable dt = new DataTable("fill", "r_large", "total_assigned", "total_buried", "tp", "precision", "recall", "iou")
        StringBuffer tbl = new StringBuffer()
        for (double rLarge : R_LARGE) {
            tbl << String.format(java.util.Locale.ROOT, "%n  R_large = %.1f Å%n", rLarge)
            tbl << String.format("    %-15s %9s %9s %9s%n", "fill", "precision", "recall", "IoU")
            for (FillSpec spec : FILLS) {
                long[] a = agg.get("${spec.label}@${rLarge}".toString())
                if (a == null) continue
                long assignedN = a[0], buriedN = a[1], tp = a[2]
                double precision = assignedN > 0 ? tp / (double) assignedN : 0d
                double recall    = buriedN   > 0 ? tp / (double) buriedN   : 0d
                double iou       = (assignedN + buriedN - tp) > 0 ? tp / (double) (assignedN + buriedN - tp) : 0d
                dt.newRow(spec.label)
                        .put("r_large", String.format(java.util.Locale.ROOT, "%.1f", rLarge))
                        .put("total_assigned", assignedN).put("total_buried", buriedN).put("tp", tp)
                        .put("precision", String.format(java.util.Locale.ROOT, "%.4f", precision))
                        .put("recall", String.format(java.util.Locale.ROOT, "%.4f", recall))
                        .put("iou", String.format(java.util.Locale.ROOT, "%.4f", iou))
                tbl << String.format(java.util.Locale.ROOT, "    %-15s %9.3f %9.3f %9.3f%n",
                        spec.label, precision, recall, iou)
            }
        }

        writeFile "$outdir/pocket_grid_cavity_fit.csv", dt.toCsv()
        write "pocket-grid-cavity-fit: assigned-vs-cavity precision/recall/IoU (micro-averaged over dataset)"
        write tbl.toString()
        write "  full table -> ${outdir}/pocket_grid_cavity_fit.csv"
        write res.writeErrorsAndGetSummary(outdir)
    }

    // ============================================================================ //
    // pocket-grid-ligand-fit
    // ============================================================================ //

    /** Per (protein, fill, d_match) ligand-region coverage counts. */
    @CompileStatic
    private static class LigandFitRow {
        String fillLabel
        double dMatch
        long ligandGrid   // |grid points within d_match of a ligand atom|
        long covered      // |that set captured by some pocket under this fill|
        long assigned     // |points assigned to any pocket under this fill|
    }

    /** Label for a decoupled cavity candidate, e.g. (6.0, 1.5) -> "cav_s6_d1.5". */
    private static String cavityLabel(double rSmooth, double dReach) {
        return String.format(java.util.Locale.ROOT, "cav_s%.0f_d%.1f", rSmooth, dReach)
    }

    /**
     * Ligand-grounded cross-check of the fill. The dataset proteins are liganated,
     * so the bound ligand marks where the binding volume actually is. Grid points
     * within {@code d_match} of any relevant ligand atom are the ground-truth
     * "binding region"; we measure, per fill, how much of it the predicted pockets
     * capture (recall) and the volume cost (mean assigned points per pocket).
     *
     * <p>If filling barely raises ligand recall over {@code none} while inflating the
     * assigned volume, the fill is overfilling (adding non-ligand points). Swept over
     * {@code d_match in {2.5, 4.0} Å}. Complements {@code pocket-grid-cavity-fit}
     * (which supplies the precision side via the large-probe cavity).
     */
    void cmdPocketGridLigandFit() {
        if (dataset == null) {
            throw new PrankException("analyze pocket-grid-ligand-fit requires a dataset argument")
        }

        final double[] D_MATCH = [2.5d, 4.0d] as double[]
        final List<FillSpec> FILLS = DEFAULT_FILLS

        // DECOUPLED cavity probe: R_smooth (probe radius -> where the smoothed lid sits)
        // separated from d_reach (depth below the lid that counts as cavity). The coupled
        // version (R_smooth == d_reach) missed pocket walls; this tests whether a wide
        // smooth + shallow reach captures the walls while staying selective. [R_smooth, d_reach].
        final List<double[]> CAVITY_COMBOS = [
                [4.0d, 1.5d] as double[], [4.0d, 3.0d] as double[], [4.0d, 4.0d] as double[],
                [6.0d, 1.5d] as double[], [6.0d, 3.0d] as double[], [6.0d, 4.5d] as double[],
        ]
        final List<String> ALL_LABELS = new ArrayList<>(FILLS*.label)
        for (double[] c : CAVITY_COMBOS) ALL_LABELS.add(cavityLabel(c[0], c[1]))

        PocketGridConfig baseConfig = baseNoneConfig()
        log.info "pocket-grid-ligand-fit base config: {}, d_match sweep: {}", baseConfig, D_MATCH

        ConcurrentLinkedQueue<LigandFitRow> rows = new ConcurrentLinkedQueue<>()
        AtomicInteger withLigand = new AtomicInteger()

        // NOTE: ignoreLigands=false — ligands are the ground truth here.
        def res = forEachPrediction(false) { Protein protein, List<? extends Pocket> pockets, Dataset.Item item ->
            Atoms ligAtoms = protein.allRelevantLigandAtoms
            if (ligAtoms == null || ligAtoms.isEmpty()) return   // no ligand -> no ground truth

            PocketGrid grid = PocketGridBuilder.build(protein, pockets, baseConfig)
            if (grid.allPoints.count == 0) return

            // Candidate "assignments": the morphological fills + the large-probe cavity mask used directly.
            Map<String, BitSet> assignedByLabel = new LinkedHashMap<>()
            for (FillSpec spec : FILLS) {
                assignedByLabel.put(spec.label, PocketGridAnalysis.unionFilled(grid, pockets,
                        PocketShapeFillerRegistry.get(spec.strategy), spec.knobs))
            }
            // Compute each distinct smoothing surface once, then a buried mask per (R_smooth, d_reach).
            Map<Double, Atoms> smoothSurf = new LinkedHashMap<>()
            for (double[] c : CAVITY_COMBOS) {
                Double rs = c[0]
                if (!smoothSurf.containsKey(rs)) {
                    smoothSurf.put(rs, Surface.computeAccessibleSurface(protein.proteinAtoms, rs, params.tessellation).points)
                }
            }
            for (double[] c : CAVITY_COMBOS) {
                assignedByLabel.put(cavityLabel(c[0], c[1]),
                        PocketGridAnalysis.buriedMask(grid.allPoints, smoothSurf.get((Double) c[0]), c[1]))
            }

            boolean counted = false
            for (double dMatch : D_MATCH) {
                BitSet ligandGrid = PocketGridAnalysis.withinMask(grid.allPoints, ligAtoms, dMatch)
                if (ligandGrid.isEmpty()) continue   // ligand not near any candidate grid point
                counted = true
                for (String label : ALL_LABELS) {
                    BitSet a = assignedByLabel.get(label)
                    rows.add(new LigandFitRow(
                            fillLabel: label, dMatch: dMatch,
                            ligandGrid: ligandGrid.cardinality(),
                            covered: PocketGridAnalysis.intersectionCount(a, ligandGrid),
                            assigned: a.cardinality()))
                }
            }
            if (counted) withLigand.incrementAndGet()
        }

        // Micro-average per (fill, d_match): sum covered / sum ligandGrid.
        Map<String, long[]> agg = new LinkedHashMap<>()   // key "fill@d" -> [ligandGrid, covered, assigned, nProteins]
        for (LigandFitRow r : rows) {
            String key = "${r.fillLabel}@${r.dMatch}"
            long[] a = agg.get(key)
            if (a == null) { a = new long[4]; agg.put(key, a) }
            a[0] += r.ligandGrid; a[1] += r.covered; a[2] += r.assigned; a[3] += 1
        }

        DataTable dt = new DataTable("strategy", "d_match", "total_ligand_grid", "total_covered", "ligand_recall", "mean_assigned")
        StringBuffer tbl = new StringBuffer()
        for (double dMatch : D_MATCH) {
            tbl << String.format(java.util.Locale.ROOT, "%n  d_match = %.1f Å%n", dMatch)
            tbl << String.format("    %-15s %14s %14s%n", "strategy", "ligand_recall", "mean_assigned")
            for (String label : ALL_LABELS) {
                long[] a = agg.get("${label}@${dMatch}".toString())
                if (a == null) continue
                long ligTot = a[0], covered = a[1], assignedTot = a[2], nprot = a[3]
                double recall = ligTot > 0 ? covered / (double) ligTot : 0d
                double meanAssigned = nprot > 0 ? assignedTot / (double) nprot : 0d
                dt.newRow(label)
                        .put("d_match", String.format(java.util.Locale.ROOT, "%.1f", dMatch))
                        .put("total_ligand_grid", ligTot).put("total_covered", covered)
                        .put("ligand_recall", String.format(java.util.Locale.ROOT, "%.4f", recall))
                        .put("mean_assigned", String.format(java.util.Locale.ROOT, "%.1f", meanAssigned))
                tbl << String.format(java.util.Locale.ROOT, "    %-15s %14.4f %14.1f%n",
                        label, recall, meanAssigned)
            }
        }

        writeFile "$outdir/pocket_grid_ligand_fit.csv", dt.toCsv()
        write "pocket-grid-ligand-fit: ligand-region recall by the predicted pockets (${withLigand.get()} proteins with a usable ligand)"
        write tbl.toString()
        write "  mean_assigned = mean union-assigned points per protein (volume cost proxy)"
        write "  full table -> ${outdir}/pocket_grid_ligand_fit.csv"
        write res.writeErrorsAndGetSummary(outdir)
    }

    // ============================================================================ //
    // pocket-grid-rule-compare
    // ============================================================================ //

    /** Count pocket pairs with containment (|A∩B|/min) >= threshold among the given per-pocket sets. */
    private static int countSubsetPairs(List<BitSet> sets, double threshold) {
        int c = 0
        for (int i = 0; i < sets.size(); i++) {
            int sa = sets[i].cardinality(); if (sa == 0) continue
            for (int j = i + 1; j < sets.size(); j++) {
                int sb = sets[j].cardinality(); if (sb == 0) continue
                int ov = PocketGridAnalysis.intersectionCount(sets[i], sets[j])
                if (ov >= threshold * Math.min(sa, sb)) c++
            }
        }
        return c
    }

    /**
     * PROTOTYPE comparison: current rule (closing + cross-pocket fill rule) vs the
     * nearest-pocket (Voronoi) rule, on the same closing fill. For each protein the
     * Voronoi variant restricts each pocket's assigned set to grid points it OWNS
     * (nearest pocket by SAS distance, ties to lower rank). Reports, per dataset,
     * subset pairs / ligand recall (d=4) / mean assigned, for both rules.
     */
    void cmdPocketGridRuleCompare() {
        if (dataset == null) {
            throw new PrankException("analyze pocket-grid-rule-compare requires a dataset argument")
        }
        final double SUBSET = 0.9d, DMATCH = 4.0d
        PocketGridConfig config = PocketGridConfig.fromParams(params)   // default closing r=1
        log.info "pocket-grid-rule-compare config: {}", config

        AtomicLong curSubset = new AtomicLong(), vorSubset = new AtomicLong()
        AtomicLong ligTot = new AtomicLong(), curLigCov = new AtomicLong(), vorLigCov = new AtomicLong()
        AtomicLong curAssigned = new AtomicLong(), vorAssigned = new AtomicLong(), nPockets = new AtomicLong()
        AtomicInteger nProteins = new AtomicInteger(), nWithLig = new AtomicInteger()

        def res = forEachPrediction(false) { Protein protein, List<? extends Pocket> pockets, Dataset.Item item ->

            PocketGrid grid = PocketGridBuilder.build(protein, pockets, config)
            if (grid.allPoints.count == 0) return
            nProteins.incrementAndGet()

            // current sets (closing + cross-pocket rule, as built)
            List<BitSet> cur = new ArrayList<>(pockets.size())
            for (Pocket p : pockets) cur.add(grid.indicesForPocket(p.rank))
            BitSet curUnion = PocketGridAnalysis.unionOf(cur)

            // voronoi variant: restrict each set to points it owns (nearest pocket)
            int[] owner = PocketGridAnalysis.nearestPocketOwners(grid, pockets, curUnion)
            List<BitSet> vor = new ArrayList<>(pockets.size())
            for (Pocket p : pockets) vor.add(PocketGridAnalysis.restrictToOwner(grid.indicesForPocket(p.rank), owner, p.rank))
            BitSet vorUnion = PocketGridAnalysis.unionOf(vor)

            curSubset.addAndGet(countSubsetPairs(cur, SUBSET))
            vorSubset.addAndGet(countSubsetPairs(vor, SUBSET))
            for (BitSet b : cur) curAssigned.addAndGet(b.cardinality())
            for (BitSet b : vor) vorAssigned.addAndGet(b.cardinality())
            nPockets.addAndGet(pockets.size())

            Atoms ligAtoms = protein.allRelevantLigandAtoms
            if (ligAtoms != null && !ligAtoms.isEmpty()) {
                BitSet ligandGrid = PocketGridAnalysis.withinMask(grid.allPoints, ligAtoms, DMATCH)
                if (!ligandGrid.isEmpty()) {
                    nWithLig.incrementAndGet()
                    ligTot.addAndGet(ligandGrid.cardinality())
                    curLigCov.addAndGet(PocketGridAnalysis.intersectionCount(curUnion, ligandGrid))
                    vorLigCov.addAndGet(PocketGridAnalysis.intersectionCount(vorUnion, ligandGrid))
                }
            }
        }

        long np = Math.max(nPockets.get(), 1L), lt = Math.max(ligTot.get(), 1L)
        StringBuffer t = new StringBuffer()
        t << String.format(java.util.Locale.ROOT, "%n  %-10s %12s %14s %14s%n", "rule", "subset_pairs", "ligand_recall", "mean_assigned")
        t << String.format(java.util.Locale.ROOT, "  %-10s %12d %14.4f %14.1f%n",
                "current", curSubset.get(), curLigCov.get() / (double) lt, curAssigned.get() / (double) np)
        t << String.format(java.util.Locale.ROOT, "  %-10s %12d %14.4f %14.1f%n",
                "voronoi", vorSubset.get(), vorLigCov.get() / (double) lt, vorAssigned.get() / (double) np)
        write "pocket-grid-rule-compare: current vs nearest-pocket(voronoi), ${nProteins.get()} proteins (${nWithLig.get()} with ligand)"
        write t.toString()
        write res.writeErrorsAndGetSummary(outdir)
    }


    /**
     * Write out binding residue keys
     */
    void cmdBindingResidues() {

        double bindingCutoff = params.ligand_protein_contact_distance

        StringBuffer summary = new StringBuffer()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            Atoms bindingAtoms = p.proteinAtoms.cutoutShell(p.allRelevantLigandAtoms, bindingCutoff)
            List<String> bindingResidueCodes = bindingAtoms.distinctGroups.collect { it.residueNumber.printFull() }.toSet().toSorted()

            String msg = "Protein [$p.name]  ligands: $p.ligandCount  bindingAtoms: $bindingAtoms.count  bindingResidues: ${bindingResidueCodes.size()}"
            log.info msg
            summary << msg + "\n"

            String outf = "$outdir/${p.name}_binding-residues.txt"
            writeFile outf, bindingResidueCodes.join("\n")
        }

        write res.writeErrorsAndGetSummary(outdir)
        write "\n" + summary.toString()

    }

    /**
     * Binding site statistics - works for both ligand-based and explicit site datasets.
     * Produces a unified CSV with the same header regardless of site source.
     */
    void cmdBindingSites() {
        DataTable dt = new DataTable("protein",
                "site_label", "site_type",
                "n_atoms", "n_residues", "site_radius", "residue_ids",
                "center_x", "center_y", "center_z",
                "lig_name", "lig_code", "lig_chain",
                "contact_dist", "center_to_prot_dist"
        )

        boolean hasExplicitSites = dataset.hasExplicitSites()

        // Ligand-specific counters
        AtomicInteger totalIgnored = new AtomicInteger()
        AtomicInteger totalSmall = new AtomicInteger()
        AtomicInteger totalDistant = new AtomicInteger()

        // Explicit-site-specific counters
        AtomicInteger totalSkippedSites = new AtomicInteger()
        AtomicInteger totalUnresolvedResidues = new AtomicInteger()

        Queue<String> itemsWithoutSites = new ConcurrentLinkedQueue<>()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            if (hasExplicitSites) {
                ExplicitSitesIndex index = dataset.explicitSitesIndex
                List<ExplicitSitesIndex.SiteDef> defs = index.getDefsForProtein(item.proteinFile)
                List<ResidueSite> sites = (p.sites ?: []) as List<ResidueSite>

                if (sites.isEmpty()) {
                    itemsWithoutSites.add(item.row)
                }

                // Track unresolved: compare defs vs resolved sites
                Map<String, ResidueSite> resolvedByName = new HashMap<>()
                for (ResidueSite site : sites) {
                    resolvedByName.put(site.name, site)
                }
                for (ExplicitSitesIndex.SiteDef sd : defs) {
                    ResidueSite resolved = resolvedByName.get(sd.siteId)
                    if (resolved == null) {
                        totalSkippedSites.incrementAndGet()
                        totalUnresolvedResidues.addAndGet(sd.residueIds.size())
                    } else {
                        int unresolved = sd.residueIds.size() - resolved.residues.size()
                        if (unresolved > 0) {
                            totalUnresolvedResidues.addAndGet(unresolved)
                        }
                    }
                }

                for (ResidueSite site : sites) {
                    Atom c = site.centroid

                    dt.newRow(item.label)
                            .put("site_label", site.label)
                            .put("site_type", "explicit")
                            .put("n_atoms", site.atoms.count)
                            .put("n_residues", site.residues.size())
                            .put("site_radius", siteRadius(c, site.atoms))
                            .put("residue_ids", formatResidueIds(site.residues))
                            .put("center_x", c.x)
                            .put("center_y", c.y)
                            .put("center_z", c.z)
                }
            } else {
                if (p.relevantLigands.isEmpty()) {
                    itemsWithoutSites.add(item.row)
                }
                double cutoff = params.ligand_protein_contact_distance
                SiteCenterMethod centerMethod = SiteCenterMethod.parse(params.site_eval_center_method)
                if (!centerMethod.supportedForLigandSites) {
                    throw new PrankException("analyze binding-sites: site_eval_center_method='${centerMethod}' is not " +
                            "supported for ligand-defined sites (it is only valid for explicitly defined sites). Use a " +
                            "ligand-compatible method such as atoms_center_of_mass, sas_points_centroid, ca_atoms_centroid " +
                            "or contact_atoms_centroid.")
                }
                for (Ligand lig : p.relevantLigands) {
                    Atom c = lig.centroid
                    if (c == null) {
                        throw new PrankException("analyze binding-sites: could not compute a binding-site center for " +
                                "ligand '${lig.label}' in protein '${p.name}' using site_eval_center_method='${centerMethod}' " +
                                "(method produced no center, e.g. no contact CA atoms). Use a different site_eval_center_method.")
                    }
                    Atoms contactAtoms = p.proteinAtoms.cutoutShell(lig.atoms, cutoff)
                    List<Residue> contactResidues = p.residues.getDistinctForAtoms(contactAtoms)

                    dt.newRow(item.label)
                            .put("site_label", lig.label)
                            .put("site_type", "ligand")
                            .put("n_atoms", lig.size)
                            .put("n_residues", contactResidues.size())
                            .put("site_radius", siteRadius(c, lig.atoms))
                            .put("residue_ids", formatResidueIds(contactResidues))
                            .put("center_x", c.x)
                            .put("center_y", c.y)
                            .put("center_z", c.z)
                            .put("lig_name", lig.name)
                            .put("lig_code", lig.code as String)
                            .put("lig_chain", lig.chain)
                            .put("contact_dist", lig.contactDistance)
                            .put("center_to_prot_dist", lig.centerToProteinDist)
                }

                totalIgnored.addAndGet(p.ligands.ignoredLigandCount)
                totalSmall.addAndGet(p.ligands.smallLigandCount)
                totalDistant.addAndGet(p.ligands.distantLigandCount)
            }

            if (params.visualizations) {
                BinaryLabeling labeling
                List<Atom> centroids = new ArrayList<>()
                if (hasExplicitSites) {
                    // Build labeling from resolved site residues
                    Set<Residue> siteResidues = new HashSet<>()
                    for (ResidueSite site : ((p.sites ?: []) as List<ResidueSite>)) {
                        siteResidues.addAll(site.residues)
                        centroids.add(site.centroid)
                    }
                    labeling = new BinaryLabeling(p.residues.count)
                    for (Residue r : p.residues) {
                        labeling.add(r, siteResidues.contains(r))
                    }
                } else {
                    labeling = item.binaryLabeling
                    for (Ligand lig : p.relevantLigands) {
                        centroids.add(lig.centroid)
                    }
                }
                if (labeling != null) {
                    new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                            proteinFile: item.proteinFile,
                            label: item.label,
                            protein: p,
                            observedLabeling: labeling,
                            siteCentroids: centroids,
                            cofactorResult: p.cofactorExtractionResult
                    )).render()
                }
            }
        }

        writeFile "$outdir/binding_sites.csv", dt.toCsv()

        Map<String, Object> extraInfo = new LinkedHashMap<>()
        int noSiteCount = itemsWithoutSites.size()
        if (hasExplicitSites) {
            extraInfo.put("Site source:", "explicit")
            extraInfo.put("Sites format:", dataset.attributes.get(Dataset.PARAM_EXPLICIT_SITES_FORMAT))
            extraInfo.put("Sites file:", dataset.attributes.get(Dataset.PARAM_EXPLICIT_SITES_FILE))
            extraInfo.put("Proteins with sites:", dataset.size - noSiteCount - res.errorCount)
            extraInfo.put("Proteins without sites:", noSiteCount)
            extraInfo.put("Sites skipped (no residues):", totalSkippedSites.get())
            extraInfo.put("Unresolved residues:", totalUnresolvedResidues.get())
        } else {
            extraInfo.put("Site source:", "ligands")
            extraInfo.put("Proteins without ligands:", noSiteCount)
            extraInfo.put("Ignored ligands:", totalIgnored.get())
            extraInfo.put("Small ligands:", totalSmall.get())
            extraInfo.put("Distant ligands:", totalDistant.get())
        }
        extraInfo.put("Errors:", res.errorCount)

        Set<String> noSummary = ["center_x", "center_y", "center_z"] as Set
        String summary = dt.formatSummaryTable("Binding Sites Summary", extraInfo, noSummary)
        write summary
        writeFile "$outdir/binding_sites_summary.txt", summary

        if (!itemsWithoutSites.isEmpty()) {
            String noSitesFile = "$outdir/items_without_sites.txt"
            writeFile noSitesFile, itemsWithoutSites.toSorted().join("\n") + "\n"
            write "NOTE: $noSiteCount of ${dataset.size} items have no binding sites. List written to [$noSitesFile]"
        }

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    private static String formatResidueIds(List<Residue> residues) {
        residues.collect { Residue r ->
            r.chain.authorId + "_" + r.residueNumber.seqNum + (r.residueNumber.insCode ?: "")
        }.join(" ")
    }

    private static double siteRadius(Atom centroid, Atoms atoms) {
        return Evaluation.siteRadius(centroid, atoms)
    }

    /**
     * Analyzes binding site centers by computing each valid SiteCenterMethod for every site
     * and reporting distances between methods, to SAS surface, and to protein atoms.
     *
     * Produces:
     *  - binding_site_centers.csv - all results in one table
     *  - binding_site_centers_{method}.csv - per-method tables
     *  - binding_site_centers_summary.txt - overall + per-method distance statistics
     */
    void cmdBindingSiteCenters() {
        List<String> distColumns = ["dist_to_atom_com", "dist_to_sas", "dist_to_protein"]

        DataTable dt = new DataTable("protein",
                "site_label", "site_type", "method",
                "center_x", "center_y", "center_z",
                "dist_to_atom_com", "dist_to_sas", "dist_to_protein"
        )

        boolean hasExplicitSites = dataset.hasExplicitSites()

        AtomicInteger totalIgnored = new AtomicInteger()
        AtomicInteger totalSmall = new AtomicInteger()
        AtomicInteger totalDistant = new AtomicInteger()
        AtomicInteger totalSkippedSites = new AtomicInteger()

        Queue<String> itemsWithoutSites = new ConcurrentLinkedQueue<>()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            p.calcuateSurfaceAndExposedAtoms()

            List<BindingSite> sites = p.sites

            if (sites.isEmpty()) {
                itemsWithoutSites.add(item.row)
            }
            if (!hasExplicitSites) {
                totalIgnored.addAndGet(p.ligands.ignoredLigandCount)
                totalSmall.addAndGet(p.ligands.smallLigandCount)
                totalDistant.addAndGet(p.ligands.distantLigandCount)
            }

            for (BindingSite site : sites) {
                boolean isLigand = site instanceof Ligand
                String siteType = isLigand ? "ligand" : "explicit"

                Atom baselineCenter = site.getCenterForMethod(SiteCenterMethod.atoms_center_of_mass)

                for (SiteCenterMethod method : SiteCenterMethod.values()) {
                    if (isLigand && !method.supportedForLigandSites) continue
                    if (!isLigand && !method.supportedForExplicitSites) continue

                    Atom center = site.getCenterForMethod(method)
                    if (center == null) continue

                    double distToAtomCom = baselineCenter != null ? Struct.dist(center, baselineCenter) : Double.NaN
                    double distToSas = p.accessibleSurface.points.dist(center)
                    double distToProtein = p.proteinAtoms.dist(center)

                    dt.newRow(item.label)
                            .put("site_label", site.label)
                            .put("site_type", siteType)
                            .put("method", method.name())
                            .put("center_x", center.x)
                            .put("center_y", center.y)
                            .put("center_z", center.z)
                            .put("dist_to_atom_com", distToAtomCom)
                            .put("dist_to_sas", distToSas)
                            .put("dist_to_protein", distToProtein)
                }
            }
        }

        writeFile "$outdir/binding_site_centers.csv", dt.toCsv()

        // Write per-method CSVs
        for (String method : dt.distinctValues("method")) {
            DataTable methodDt = dt.filter("method", method)
            writeFile "$outdir/binding_site_centers_${method}.csv", methodDt.toCsv()
        }

        // Build text summary
        Map<String, Object> extraInfo = new LinkedHashMap<>()
        int noSiteCount = itemsWithoutSites.size()
        if (hasExplicitSites) {
            extraInfo.put("Site source:", "explicit")
            extraInfo.put("Proteins without sites:", noSiteCount)
            extraInfo.put("Sites skipped:", totalSkippedSites.get())
        } else {
            extraInfo.put("Site source:", "ligands")
            extraInfo.put("Proteins without ligands:", noSiteCount)
            extraInfo.put("Ignored ligands:", totalIgnored.get())
            extraInfo.put("Small ligands:", totalSmall.get())
            extraInfo.put("Distant ligands:", totalDistant.get())
        }
        extraInfo.put("Errors:", res.errorCount)

        Set<String> noSummary = ["center_x", "center_y", "center_z"] as Set
        String summary = dt.formatSummaryTable("Binding Site Centers Summary", extraInfo, noSummary)
        summary += dt.formatGroupedSummaryTable("method", distColumns, "Distance Statistics by Center Method")

        write summary
        writeFile "$outdir/binding_site_centers_summary.txt", summary

        if (!itemsWithoutSites.isEmpty()) {
            String noSitesFile = "$outdir/items_without_sites.txt"
            writeFile noSitesFile, itemsWithoutSites.toSorted().join("\n") + "\n"
            write "NOTE: $noSiteCount of ${dataset.size} items have no binding sites. List written to [$noSitesFile]"
        }

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    void cmdPeptides() {
        LoaderParams.ignoreLigandsSwitch = true

        StringBuffer csv = new StringBuffer("protein, pept_count, peptides\n")
        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            String ps = p.peptides.collect { "($it.authorId,$it.length)" }.join(" ")
            csv << "$p.name, ${p.peptides.size()}, $ps\n"
        }
        writeFile "$outdir/peptides.csv", csv
        write res.writeErrorsAndGetSummary(outdir)
        write csv.toString()
    }

    /**
     * Protein-level statistics
     */
    void cmdProteins() {
        DataTable dt = new DataTable("protein",
                "n_chains_total", "n_poly_chains", "n_protein_chains",
                "n_residues", "n_protein_atoms", "n_all_atoms",
                "n_relevant_ligands", "n_other_ligands", "n_peptides",
                "protein_chain_ids"
        )

        Queue<String> withProteinChains = new ConcurrentLinkedQueue<>()
        Queue<String> withoutProteinChains = new ConcurrentLinkedQueue<>()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            dt.newRow(item.label)
                .put("n_chains_total",     p.structure.chains.size())
                .put("n_poly_chains",      p.structure.polyChains.size())
                .put("n_protein_chains",   p.residueChains.size())
                .put("n_residues",         p.residues.size())
                .put("n_protein_atoms",    p.proteinAtoms.count)
                .put("n_all_atoms",        p.allAtoms.count)
                .put("n_relevant_ligands", p.relevantLigands.size())
                .put("n_other_ligands",    p.allIgnoredLigands.size())
                .put("n_peptides",         p.peptides.size())
                .put("protein_chain_ids",  p.residueChains.collect { it.authorId }.join(" "))

            if (p.residueChains.empty) {
                withoutProteinChains.add(item.row)
            } else {
                withProteinChains.add(item.row)
            }
        }

        writeFile "$outdir/proteins.csv", dt.toCsv()

        // Write split dataset files if some structures have no protein chains
        if (!withoutProteinChains.empty) {
            String headerLine = dataset.header.size() > 1 ? "HEADER: " + dataset.header.join(" ") + "\n\n" : ""

            String withFile = "$outdir/${dataset.label}_with_protein_chains.ds"
            writeFile withFile,
                    "# Structures from ${dataset.name} that contain protein chains\n\n" +
                    headerLine +
                    withProteinChains.toSorted().join("\n") + "\n"

            String withoutFile = "$outdir/${dataset.label}_without_protein_chains.ds"
            writeFile withoutFile,
                    "# Structures from ${dataset.name} that have no protein chains\n\n" +
                    headerLine +
                    withoutProteinChains.toSorted().join("\n") + "\n"

            write ""
            write "NOTE: ${withoutProteinChains.size()} of ${dataset.size} structures have no protein chains. Split dataset files have been written to:\n"
            write "  Structures with protein chains:    $withFile"
            write "  Structures without protein chains: $withoutFile"
        }

        String summary = dt.formatSummaryTable("Protein Dataset Summary",
                ["No protein chains:": dt.countWhere("n_protein_chains", 0),
                 "Errors:": res.errorCount] as Map<String, Object>)
        write summary
        writeFile "$outdir/proteins_summary.txt", summary

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Parse all proteins in the dataset and report errors.
     */
    void cmdParseProteins() {
        LoaderParams.ignoreLigandsSwitch = true  // no need to load ligands for this

        def res = dataset.processItems { Dataset.Item item ->
            item.protein
        }

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Chain statistics
     */
    void cmdChains() {
        LoaderParams.ignoreLigandsSwitch = true

        DataTable dt = new DataTable("protein",
                "n_chains", "chain_id", "mmcif_id", "n_residues",
                "residue_string"
        )

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            int nchains = p.residueChains.size()
            for (ResidueChain chain : p.residueChains) {
                dt.newRow(item.label)
                        .put("n_chains", nchains)
                        .put("chain_id", chain.authorId)
                        .put("mmcif_id", chain.mmcifId)
                        .put("n_residues", chain.length)
                        .put("residue_string", chain.biojavaCodeCharString)
            }
        }

        writeFile "$outdir/chains.csv", dt.toCsv()

        Set<String> noSummary = ["residue_string"] as Set
        String summary = dt.formatSummaryTable("Chains Summary",
                ["Errors:": res.errorCount] as Map<String, Object>, noSummary)
        write summary
        writeFile "$outdir/chains_summary.txt", summary

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Chain statistics
     */
    void cmdChainsResidues() {
        cmdChains()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            int idx = 1
            for (ResidueChain chain : p.residueChains) {

                List<String> csvRows = []
                for (Residue res : chain.residues) {
                    ResidueNumber rn = res.residueNumber
                    csvRows.add("$rn.chainName, $rn.seqNum, $rn.insCode, $res.key, $res.chainMmcifId, $res.atoms.count, $res.secStruct " as String)
                }

                String csv = "chain_name, seq_num, ins_code, key, chain_mmcif_id, atoms, sec_struct_type\n" +
                        csvRows.toSorted().collect { it + "\n" }.join("")
                String strIdx = String.format("%02d", idx++)
                writeFile "$outdir/${item.label}_${strIdx}_${chain.authorId}_${chain.mmcifId}_residues.csv", csv
            }
        }

        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Export chains to fasta in raw chain format (as P2Rank sees it).
     * Considers only protein AA residue chains.
     */
    void cmdFastaRaw() {
        doCmdFasta(false)
    }

    /**
     * Export chains to fasta where some residue codes are transformed:
     * 
     * 1. non-letter characters -> X
     *
     * Considers only protein AA residue chains.
     */
    void cmdFastaMasked() {
        doCmdFasta(true)
    }

    private doCmdFasta(boolean masked) {
        LoaderParams.ignoreLigandsSwitch = true
        FastaExporter exporter = FastaExporter.getInstance()

        write "exporting fasta (masked: $masked)"

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            for (ResidueChain chain : p.residueChains) {
                String chainCode = Struct.maskEmptyChainId(chain.authorId)
                String protFileBaseName = Futils.baseName(item.proteinFile)
                String fname = "${protFileBaseName}_${chainCode}.fasta"

                String header = exporter.makeFastaHeader(chain, p.structure)
                String codes = exporter.getFastaChain(chain, masked)
                String fasta = exporter.formatFastaFile(header, codes)

                fname = "$outdir/$fname"

                write "$p.name: exporting chain $chain.authorId to $fname"

                writeFile(fname, fasta)
            }
        }

        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Statistics about binary residue labeling + visualizations
     */
    void cmdLabeledResidues() {
        // assert dataset.hasExplicitResidueLabeling()
        LoaderParams.ignoreLigandsSwitch = true

        def labeler = dataset.binaryResidueLabeler

        if (labeler instanceof SprintLabelingLoader) {
            printSprintChains((SprintLabelingLoader)labeler)
        }

        List<String> csvRows = newSynchronizedList()
        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            BinaryLabeling labeling = labeler.getBinaryLabeling(p.residues, p)
            def s = BinaryLabelings.getStats(labeling)

            int nchains = p.residueChains.size()
            String chainIds = p.residueChains.collect { it.authorId }.join(" ")
            int nres = p.residues.size()
            int nlabres = s.total
            csvRows.add("${item.label}, $nchains, $chainIds, $nres, $nlabres, ${s.positives}, ${s.negatives}, ${s.unlabeled}" as String)

            if (params.visualizations) {
                new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: item.protein,
                        observedLabeling: labeling,
                        cofactorResult: item.protein.cofactorExtractionResult
                )).render()
            }
        }

        String csv = "protein, n_chains, chain_ids, n_residues, n_residues_in_labeling, positives, negatives, unlabeled\n" +
                csvRows.toSorted().collect { it + "\n" }.join("")
        writeFile "$outdir/residue_stats.csv", csv
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Analyze conservation scores per chain.
     * Produces a CSV with per-chain conservation loading info: whether conservation was loaded,
     * the conservation file path, and how many residues were matched.
     */
    void cmdConservation() {
        LoaderParams.ignoreLigandsSwitch = true

        DataTable dt = new DataTable("protein",
                "chain_id", "mmcif_id", "n_residues",
                "conserv_loaded", "conserv_file", "conserv_matched_residues",
                "residue_string"
        )

        AtomicInteger fullyMatchedChains = new AtomicInteger()
        AtomicInteger fullyMatchedChainItems = new AtomicInteger()
        AtomicInteger failedChains = new AtomicInteger()
        AtomicInteger failedChainItems = new AtomicInteger()
        AtomicInteger partialChains = new AtomicInteger()
        AtomicInteger partialChainItems = new AtomicInteger()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            // Load conservation scores (graceful - null on failure)
            ConservationScore conservScore = null
            try {
                conservScore = p.loadConservationScores(item.context)
            } catch (Exception e) {
                log.warn "Failed to load conservation for [{}]: {}", item.label, e.message
            }

            boolean itemHasFullyMatchedChain = false
            boolean itemHasFailedChain = false
            boolean itemHasPartialChain = false

            for (ResidueChain chain : p.residueChains) {
                def row = dt.newRow(item.label)
                        .put("chain_id", chain.authorId)
                        .put("mmcif_id", chain.mmcifId)
                        .put("n_residues", chain.length)
                        .put("residue_string", chain.biojavaCodeCharString)

                ConservationScore.ChainConservationInfo ci = conservScore?.chainInfoMap?.get(chain.authorId)
                if (ci != null) {
                    row.put("conserv_loaded", ci.loaded ? 1 : 0)
                            .put("conserv_file", ci.scoreFile?.absolutePath ?: "")
                            .put("conserv_matched_residues", ci.matchedResidues)

                    if (!ci.loaded) {
                        failedChains.incrementAndGet()
                        itemHasFailedChain = true
                    } else if (ci.matchedResidues < ci.chainResidues) {
                        partialChains.incrementAndGet()
                        itemHasPartialChain = true
                    } else {
                        fullyMatchedChains.incrementAndGet()
                        itemHasFullyMatchedChain = true
                    }
                } else {
                    row.put("conserv_loaded", 0)
                            .put("conserv_file", "")
                            .put("conserv_matched_residues", 0)
                    failedChains.incrementAndGet()
                    itemHasFailedChain = true
                }
            }

            if (itemHasFullyMatchedChain) fullyMatchedChainItems.incrementAndGet()
            if (itemHasFailedChain) failedChainItems.incrementAndGet()
            if (itemHasPartialChain) partialChainItems.incrementAndGet()

            if (params.visualizations && conservScore != null) {
                ResidueLabeling<Double> labeling = conservScore.toDoubleLabeling(p)
                new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: p,
                        doubleLabeling: labeling,
                        cofactorResult: p.cofactorExtractionResult
                )).render()
            }
        }

        writeFile "$outdir/conservation.csv", dt.toCsv()

        Map<String, Object> extraInfo = new LinkedHashMap<>()
        extraInfo.put("Fully matched chains:", "${fullyMatchedChains.get()} in ${fullyMatchedChainItems.get()} dataset items")
        extraInfo.put("Partially matched chains:", "${partialChains.get()} in ${partialChainItems.get()} dataset items")
        extraInfo.put("Failed chains:", "${failedChains.get()} in ${failedChainItems.get()} dataset items")
        extraInfo.put("Failed to load dataset items:", res.errorCount)

        Set<String> noSummary = ["residue_string", "conserv_file"] as Set
        String summary = dt.formatSummaryTable("Conservation Summary", extraInfo, noSummary, "Total chains:")
        write summary
        writeFile "$outdir/conservation_summary.txt", summary

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Compare chain strings in structure with those defined in sprint labeling file
     */
    @CompileStatic
    private void printSprintChains(SprintLabelingLoader loader) {
        StringBuffer csv = new StringBuffer(
                "# status: 'MATCH' | '!:LEN' = labeling/structure chain lengths don't match | '!:RES' = labeling/structure chain residues don't match\n" +
                "chain_code, source, status, length, chain_data\n")

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            assignSecondaryStructure(p.structure)

            log.info("processing protein [$p.name] with residue chains ${p.residueChains*.authorId}")

            for (ResidueChain chain : p.residueChains) {
                String chainCode = loader.toElementCode(p, chain)
                if (loader.elementsByCode.containsKey(chainCode)) {
                    log.info "writing sprint chain [{}]", chainCode

                    def strStruct = chain.biojavaCodeCharString
                    def strLabeler = loader.elementsByCode.get(chainCode)?.chain
                    def strLabels = loader.elementsByCode.get(chainCode)?.labels

                    def secStruct = chain.secStructString

                    String status = "MATCH"
                    if (strStruct.length() != strLabeler.length()) {
                        status = "!:LEN"
                    } else if (strStruct != strLabeler) {
                        status = "!:RES"
                    }

                    StringBuilder sb = new StringBuilder()
                    sb << String.format("%s, structure, %-6s, %6s, %s \n", chainCode, status, strStruct .length(), strStruct )
                    sb << String.format("%s,   labeler, %-6s, %6s, %s \n", chainCode, "", strLabeler.length(), strLabeler)
                    sb << String.format("%s,    labels, %-6s, %6s, %s \n", chainCode, "", strLabels .length(), strLabels )
                    sb << String.format("%s, sec.struc, %-6s, %6s, %s \n", chainCode, "", secStruct .length(), secStruct )
                    csv << sb.toString()
                } else {
                    log.warn "labeling for chain [{}] not found", chainCode
                }
            }
        }

        writeFile "$outdir/labeled_chains.csv", csv
    }

    /**
     * calculate AA propensities of exposed residues
     * i.e. propensity of being labeled as 1 by binary labeling
     * which is either explicitly defined by dataset or derived from ligands
     */
    private void cmdAaPropensities() {
        List<BinCounter<AA>> counters = newSynchronizedList()

        def res = dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)   // TODO not always only exposed!

            def counter = new BinCounter<AA>()

            labeling.labeledResidues.each { LabeledResidue<Boolean> lres ->
                AA aa = lres.residue.aa
                if (aa != null && lres.label != null) {
                    counter.add(aa, lres.label)
                }
            }

            counters.add(counter)
        }

        write res.writeErrorsAndGetSummary(outdir)
        BinCounter<AA> counter = BinCounter.join(counters)
        savePropensities("$outdir/aa-propensity.csv", counter)
    }

    private void cmdAtomTypePropensities() {
        List<BinCounter<String>> counters = newSynchronizedList()

        boolean exposedOnly = true // TODO make a configurable param for ions

        def res = dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler

            Atoms atoms = exposedOnly ? prot.exposedAtoms : prot.proteinAtoms
            Atoms ligandAtoms = prot.allRelevantLigandAtoms.withKdTree()

            def counter = new BinCounter<String>()

            atoms.each { Atom atom ->
                String atomCode = PdbUtils.getAtomTypeInResidueCode(atom)
                boolean isBinding = ligandAtoms.areWithinDistance(atom, params.ligand_protein_contact_distance)
                counter.add(atomCode, isBinding)
            }

            counters.add(counter)
        }

        write res.writeErrorsAndGetSummary(outdir)
        BinCounter<String> counter = BinCounter.join(counters)
        savePropensities("$outdir/atomtype-propensity.csv", counter)
    }


    /**
     * ordering dependent sequence duplets (only starting from exposed residues)
     */
    private void cmdAaSurfSeqDuplets() {
        List<BinCounter<String>> counters = newSynchronizedList()

        def res = dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)    // TODO not always only exposed!

            def counter = new BinCounter<String>()

            labeling.labeledResidues.each { LabeledResidue<Boolean> lres ->
                def r = lres.residue
                def prev = r.previousInChain
                def next = r.nextInChain

                // in each direction
                counter.add(Residue.safeOrderedCode2(r, prev), lres.label)
                counter.add(Residue.safeOrderedCode2(r, next), lres.label)
            }

            counters.add(counter)
        }

        write res.writeErrorsAndGetSummary(outdir)
        savePropensities("$outdir/duplets.csv", BinCounter.join(counters))
    }

    /**
     * sequence triplets (only from exposed residues)
     */
    private void cmdAaSurfSeqTriplets() {
        List<BinCounter<String>> counters = newSynchronizedList()

        def res = dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)       // TODO not always only exposed!

            def counter = new BinCounter<String>()

            labeling.labeledResidues.each { lres ->
                String code = Residue.safeSorted3CodeFor(lres.residue)
                counter.add(code, lres.label)
            }

            counters.add(counter)
        }

        write res.writeErrorsAndGetSummary(outdir)
        savePropensities("$outdir/triplets.csv", BinCounter.join(counters))
    }

    /**
     * Runs all propensity calculations
     *   - aa-propensities
     *   - atomtype-propensities
     *   - aa-surf-seq-duplets
     *   - aa-surf-seq-triplets
     */
    private void cmdAllPropensities() {
        cmdAaPropensities()
        cmdAtomTypePropensities()
        cmdAaSurfSeqDuplets()
        cmdAaSurfSeqTriplets()
    }

    private void savePropensities(String fname, BinCounter counter) {
        StringBuilder csv = new StringBuilder("key, propensity, propensity^2, count, pos, neg\n")
        counter.table.keySet().toSorted().each {
            def bin = counter.get(it)
            double r = bin.posRatio
            csv << String.format("%s, %-7s, %-7s, %8s, %8s, %8s\n", it, format(r, 5), format(r*r, 5), bin.count, bin.positives, bin.negatives)
        }
        writeFile fname, csv
        write "Calculated propensities saved to [$fname]"
    }

    /**
     * Convert dataset with ligand definitions based on contact residue ids to
     * one with definitions based on ligand atom_id.
     */
    private void cmdConvertContactresDataset() {
        String headerLine = "HEADER: " + dataset.header.join(" ")

        List<String> newItems = Cutils.newSynchronizedList(dataset.size)
        List<String> nonMatchingItems = Cutils.newSynchronizedList(dataset.size)

        def res = dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein

            List<String> ligDefs = new ArrayList<>()
            for (Ligand lig : prot.relevantLigands) {
                String name = lig.groups[0].PDBName
                int atomId = lig.atoms[0].PDBserial
                String ligDef = name + "[atom_id:" + atomId + "]"
                ligDefs.add(ligDef)
            }

            String newLigDefsStr = ligDefs.toSorted().join(",")
            Map<String, String> newColVals = new HashMap<>(item.columnValues)
            newColVals.put(Dataset.COLUMN_LIGANDS, newLigDefsStr)
            String newLine = dataset.header.collect {newColVals.get(it) }.join("  ")

            if (item.ligandDefinitions.size() == ligDefs.size()) {
                newItems.add(newLine)
            } else {
                String oldLine = dataset.header.collect {item.columnValues.get(it) }.join("  ")
                String ne = "${item.ligandDefinitions.size()} != ${ligDefs.size()}"
                nonMatchingItems.add(ne + "  |OLD:|  " + oldLine + "  |NEW:|  " + newLigDefsStr)
            }
        }

        newItems = newItems.toSorted()

        String newDsText = headerLine + "\n" + newItems.join("\n") + "\n"
        String nonMatchingText = nonMatchingItems.join("\n") + "\n"

        log.info("Matching items: {}", newItems.size())
        log.info("Non matching items: {}", nonMatchingItems.size())
        log.info("Non matching items were ignored.")

        writeFile "$outdir/${dataset.label}_converted.ds", newDsText
        writeFile "$outdir/non_matching_items.txt", nonMatchingText
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Survey HETATM groups and dry-run cofactor specifiers.
     *
     * <p>Without {@code -cofactors}: lists every distinct HETATM group instance with
     * chain/residue/atoms for discovery (which names exist? which to use as cofactor
     * specifiers?).
     *
     * <p>With {@code -cofactors} (or a {@code cofactors} column in the dataset): additionally
     * reports which specifiers matched which groups, using the same per-item resolution as
     * pocket prediction would (column overrides global). Lets users verify precise specifiers
     * before committing to a long-running run.
     */
    void cmdCofactors() {
        DataTable dt = new DataTable("protein",
                "het_name", "chain", "res_num", "group_id",
                "n_heavy_atoms", "dist_to_protein",
                "currently_classified_as", "would_be_cofactor"
        )

        // Aggregates across the dataset
        Map<String, Set<String>> nameToStructures = new java.util.concurrent.ConcurrentHashMap<>()
        Map<String, java.util.concurrent.atomic.AtomicInteger> nameToGroupCount = new java.util.concurrent.ConcurrentHashMap<>()
        Map<String, Set<String>> specToStructures = new java.util.concurrent.ConcurrentHashMap<>()
        Map<String, java.util.concurrent.atomic.AtomicInteger> specToGroupCount = new java.util.concurrent.ConcurrentHashMap<>()
        java.util.concurrent.atomic.AtomicInteger itemsWithSpecifiers = new java.util.concurrent.atomic.AtomicInteger()

        DataTable mt = new DataTable("protein",
                "specifier", "matched_count", "matched_group_ids", "unmatched_reason")

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            // Per-item resolved specifiers - same resolution as protein loading does.
            // Either the dataset's `cofactors` column (if present) or the global Params.inst.cofactors.
            List<LigandDefinition> itemSpecifiers = dataset.resolveCofactorDefinitions(item)
            if (!itemSpecifiers.isEmpty()) itemsWithSpecifiers.incrementAndGet()

            // Index cofactor-matched groups for fast classification
            Set<Group> cofactorMatched = Collections.newSetFromMap(new IdentityHashMap<>())
            if (p.cofactorExtractionResult != null) {
                for (List<Group> gs : p.cofactorExtractionResult.foundGroups.values()) {
                    cofactorMatched.addAll(gs)
                }
            }

            // Index relevant + ignored ligand groups
            Set<Group> relevantLigGroups = Collections.newSetFromMap(new IdentityHashMap<>())
            Set<Group> ignoredLigGroups = Collections.newSetFromMap(new IdentityHashMap<>())
            for (Ligand lig : p.relevantLigands ?: []) {
                relevantLigGroups.addAll(lig.atoms.distinctGroups)
            }
            for (Ligand lig : p.allIgnoredLigands ?: []) {
                ignoredLigGroups.addAll(lig.atoms.distinctGroups)
            }

            // Per-specifier per-structure match tracking
            Map<String, List<String>> matchedGroupIdsBySpec = new LinkedHashMap<>()
            for (LigandDefinition d : itemSpecifiers) matchedGroupIdsBySpec.put(d.originalString, new ArrayList<>())

            // Use the same candidate set as cofactor extraction (Ligands.loadForProtein,
            // CofactorHandler.extractCofactorAtoms). getHetGroups misses GDP/GTP/ATP
            // (BioJava GroupType.NUCLEOTIDE) and SHR-style AA derivatives in non-polymer
            // chains, which would silently drop them from het_groups.csv / cofactor_matches.csv.
            for (Group g : Struct.getLigandGroups(p)) {
                String name = g.PDBName?.toUpperCase()
                if (name == null) continue

                nameToGroupCount.computeIfAbsent(name, { new java.util.concurrent.atomic.AtomicInteger() } as java.util.function.Function).incrementAndGet()
                nameToStructures.computeIfAbsent(name, { (Set<String>) (java.util.concurrent.ConcurrentHashMap.newKeySet()) } as java.util.function.Function).add(item.label)

                String chain = getAuthorId(g.chain)
                String resNum = g.residueNumber?.printFull() ?: "?"
                String groupId = "${chain}_${resNum}"
                Atoms ga = Atoms.allFromGroup(g).withoutHydrogens()

                String cls
                if (cofactorMatched.contains(g)) cls = "cofactor"
                else if (relevantLigGroups.contains(g)) cls = "relevant_ligand"
                else if (ignoredLigGroups.contains(g)) cls = "ignored"
                else cls = "other"

                int wouldBeCofactor = 0
                for (LigandDefinition d : itemSpecifiers) {
                    if (d.matchesGroup(g, p)) {
                        wouldBeCofactor = 1
                        specToGroupCount.computeIfAbsent(d.originalString, { new java.util.concurrent.atomic.AtomicInteger() } as java.util.function.Function).incrementAndGet()
                        specToStructures.computeIfAbsent(d.originalString, { (Set<String>) (java.util.concurrent.ConcurrentHashMap.newKeySet()) } as java.util.function.Function).add(item.label)
                        matchedGroupIdsBySpec.get(d.originalString).add(groupId)
                    }
                }

                DataTable.Row r = dt.newRow(item.label)
                r.put("het_name", name)
                r.put("chain", chain)
                r.put("res_num", resNum)
                r.put("group_id", groupId)
                r.put("n_heavy_atoms", ga.count)
                if (ga.empty) {
                    r.put("dist_to_protein", "")
                } else {
                    r.put("dist_to_protein", p.proteinAtoms.dist(ga))
                }
                r.put("currently_classified_as", cls)
                r.put("would_be_cofactor", itemSpecifiers.isEmpty() ? "" : String.valueOf(wouldBeCofactor))
            }

            // cofactor_matches.csv row(s) for this structure
            for (LigandDefinition d : itemSpecifiers) {
                List<String> matched = matchedGroupIdsBySpec.get(d.originalString)
                String reason = ""
                if (matched.isEmpty()) {
                    // Use getLigandGroups (matches extraction path) so GDP/GTP/ATP-style
                    // codes aren't falsely reported as "name not in structure".
                    boolean nameInStructure = Struct.getLigandGroups(p)
                            .any { ((Group) it).PDBName?.toUpperCase() == d.groupName?.toUpperCase() }
                    reason = nameInStructure ? "name present but specifier filter excluded all instances"
                                             : "name not in structure"
                }
                DataTable.Row mr = mt.newRow(item.label)
                mr.put("specifier", d.originalString)
                mr.put("matched_count", matched.size())
                mr.put("matched_group_ids", matched.join(" "))
                mr.put("unmatched_reason", reason)
            }

            // Visualizations - renderer reads cofactorResult and emits per-name selections.
            if (params.visualizations) {
                new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: p,
                        cofactorResult: p.cofactorExtractionResult
                )).render()
            }
        }

        writeFile "$outdir/het_groups.csv", dt.toCsv()

        boolean anySpecifiers = itemsWithSpecifiers.get() > 0

        StringBuilder summary = new StringBuilder()
        summary << "HETATM Survey for ${dataset.name} (${dataset.size} structures)\n\n"
        summary << "Most frequent HETATM groups:\n"
        nameToStructures.entrySet()
                .toSorted { -it.value.size() }
                .each { e ->
                    int nStruct = e.value.size()
                    int nGroups = nameToGroupCount.get(e.key).get()
                    double pct = (100.0d * nStruct) / Math.max(1, dataset.size)
                    summary << String.format("  %-8s %4d structures (%5.1f%%) - %d groups total\n",
                            e.key, nStruct, pct, nGroups)
                }

        if (anySpecifiers) {
            summary << "\nCofactor specifier match (per-item resolution, column overrides global):\n"
            specToStructures.entrySet()
                    .toSorted { -it.value.size() }
                    .each { e ->
                        int nStruct = e.value.size()
                        int nGroups = specToGroupCount.get(e.key).get()
                        String marker = nStruct == 0 ? "   ← matched no structures" : ""
                        summary << String.format("  %-30s %d/%d structures, %d groups total%s\n",
                                e.key, nStruct, dataset.size, nGroups, marker)
                    }
            writeFile "$outdir/cofactor_matches.csv", mt.toCsv()
        }

        String summaryStr = summary.toString()
        writeFile "$outdir/het_groups_summary.txt", summaryStr
        write summaryStr

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }


    void print_volsite_table() {
        List<String> atomTypes = AtomTableFeature.atomPropertyTable.itemNames.toSorted()

        StringBuilder sb = new StringBuilder()
        sb << "atomName, vsAromatic, vsCation, vsAnion, vsHydrophobic, vsAcceptor, vsDonor\n"
        for (String atomType : atomTypes) {
            def ss = Sutils.split(atomType, ".")
            String resName = ss[0]
            String atomName = ss[1]

            VolSitePharmacophore.AtomProps props = VolSitePharmacophore.getAtomProperties(atomName, resName)

            sb << atomType + ", "
            sb << (props.aromatic?"1":"0"   ) + ", "
            sb << (props.cation?"1":"0"     ) + ", "
            sb << (props.anion?"1":"0"      ) + ", "
            sb << (props.hydrophobic?"1":"0") + ", "
            sb << (props.acceptor?"1":"0"   ) + ", "
            sb << (props.donor?"1":"0"      )
            sb << "\n"
        }

        String ss = sb.toString()
        write ss
        writeFile "$outdir/volsite_atom_table.csv", ss

    }

    //===========================================================================================================//
    // surface-strategies
    //===========================================================================================================//

    /** One pre-loaded protein: the CDK container + its atom count (structure I/O excluded from timing). */
    @CompileStatic
    private static class ProteinInput {
        final String name
        final IAtomContainer container
        final int atomCount
        ProteinInput(String name, IAtomContainer container, int atomCount) {
            this.name = name; this.container = container; this.atomCount = atomCount
        }
    }

    /** Per-surface measurement (nanoseconds for the two phases + point counts). */
    @CompileStatic
    private static class Sample {
        final long surfaceNs, sparsifyNs
        final int points, sparsePoints, atoms
        Sample(long surfaceNs, long sparsifyNs, int points, int sparsePoints, int atoms) {
            this.surfaceNs = surfaceNs; this.sparsifyNs = sparsifyNs
            this.points = points; this.sparsePoints = sparsePoints; this.atoms = atoms
        }
    }

    /**
     * Benchmark every {@link SurfaceStrategy} on the dataset: for each strategy, build the surface for
     * every protein at the configured parallelization (-threads), timing the surface generation and the
     * subsequent sparsification per surface, and report aggregate stats.
     *
     * <p>Structure I/O is excluded: proteins are loaded and converted to CDK containers ONCE up front,
     * then each strategy is timed over the in-memory containers. A short warm-up per strategy keeps the
     * cross-strategy comparison fair (JIT + process-wide caches are hot before measuring).
     */
    void cmdSurfaceStrategies() {
        double solventRadius = params.solvent_radius
        int tess = params.tessellation
        double sparsifyDist = Surface.SPARSIFY_DIST
        int threads = Math.max(1, params.threads)
        List<SurfaceStrategy> strategies = SurfaceStrategy.values().toList()

        // 1) pre-load proteins -> CDK containers ONCE (I/O excluded from the surface timing)
        write "preloading dataset proteins (structure I/O excluded from timing) ..."
        Queue<ProteinInput> inputsQ = new ConcurrentLinkedQueue<>()
        def pre = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            inputsQ.add(new ProteinInput(p.name, CdkUtils.toAtomContainer(p.proteinAtoms), p.proteinAtoms.count))
        }
        write pre.writeErrorsAndGetSummary(outdir)
        List<ProteinInput> proteins = new ArrayList<>(inputsQ)
        if (proteins.empty) throw new PrankException("no proteins loaded")
        long totalAtoms = 0
        for (ProteinInput it : proteins) totalAtoms += it.atomCount
        write "loaded ${proteins.size()} proteins, ${totalAtoms} atoms total; solventRadius=$solventRadius tessellation=$tess threads=$threads"

        // 2) warm-up: an untimed PARALLEL pass over up to warmN proteins per strategy, so the JIT
        // (C2/Graal) fully compiles the parallel hot paths and the process caches are populated before
        // timing. Done in parallel (not single-threaded) and over a substantial sample so the timed pass
        // is not skewed by first-surface compile/GC tail (which otherwise dominates small datasets).
        int warmN = Math.min(proteins.size(), 500)
        List<ProteinInput> warmSet = new ArrayList<>(proteins.subList(0, warmN))
        Queue<String> warmErrors = new ConcurrentLinkedQueue<>()
        for (SurfaceStrategy s : strategies) {
            runParallel(warmSet, threads, warmErrors) { ProteinInput input ->
                SurfaceStrategy.RawSurface r = s.compute(input.container, solventRadius, tess)
                AtomDeduplicator.sparsify(r.points, sparsifyDist)
            }
        }

        // 3) measured pass per strategy
        StringBuilder csv = new StringBuilder("strategy,proteins,threads,wall_s,sum_surface_s,par_speedup," +
                "surf_mean_ms,surf_median_ms,surf_p95_ms,surf_max_ms,sparsify_mean_ms," +
                "avg_points,avg_sparse_points,sparsify_reduction_pct,Matoms_per_s\n")
        StringBuilder console = new StringBuilder()
        console << String.format("%n%-9s %8s %8s %9s %12s %11s %11s %10s %12s %9s %12s%n",
                "strategy", "proteins", "wall_s", "speedup", "surf_med_ms", "surf_p95_ms", "sparse_ms",
                "avg_pts", "avg_sparse", "reduce_%", "Matoms/s")
        console << ("-" * 128) << "\n"

        for (SurfaceStrategy s : strategies) {
            ConcurrentLinkedQueue<Sample> samplesQ = new ConcurrentLinkedQueue<>()
            Queue<String> errors = new ConcurrentLinkedQueue<>()

            long wall0 = System.nanoTime()
            runParallel(proteins, threads, errors) { ProteinInput input ->
                long t0 = System.nanoTime()
                SurfaceStrategy.RawSurface raw = s.compute(input.container, solventRadius, tess)
                long t1 = System.nanoTime()
                Atoms sparse = AtomDeduplicator.sparsify(raw.points, sparsifyDist)
                long t2 = System.nanoTime()
                samplesQ.add(new Sample(t1 - t0, t2 - t1, raw.points.count, sparse.count, input.atomCount))
            }
            long wallNs = System.nanoTime() - wall0

            List<Sample> samples = new ArrayList<>(samplesQ)
            if (!errors.empty) write "strategy ${s.id}: ${errors.size()} failures (first: ${errors.peek()})"
            if (samples.empty) { write "strategy ${s.id}: no successful surfaces"; continue }

            int n = samples.size()
            double[] surfMs = new double[n]
            double[] sparseMs = new double[n]
            long sumSurfNs = 0, sumAtoms = 0, sumPts = 0, sumSparse = 0
            for (int i = 0; i < n; i++) {
                Sample sm = samples.get(i)
                surfMs[i] = sm.surfaceNs / 1e6d
                sparseMs[i] = sm.sparsifyNs / 1e6d
                sumSurfNs += sm.surfaceNs
                sumAtoms += sm.atoms
                sumPts += sm.points
                sumSparse += sm.sparsePoints
            }
            Arrays.sort(surfMs)
            double avgPts = sumPts / (double) n
            double avgSparse = sumSparse / (double) n
            double wallS = wallNs / 1e9d
            double speedup = sumSurfNs / (double) wallNs
            double matomsPerS = sumAtoms / wallS / 1e6d
            double reductionPct = avgPts > 0 ? (1 - avgSparse / avgPts) * 100 : 0

            csv << "${s.id},${n},${threads},${f3(wallS)},${f3(sumSurfNs / 1e9d)},${f3(speedup)}," +
                    "${f3(mean(surfMs))},${f3(median(surfMs))},${f3(pctl(surfMs, 95))},${f3(surfMs[n - 1])}," +
                    "${f3(mean(sparseMs))},${Math.round(avgPts)},${Math.round(avgSparse)},${f1(reductionPct)},${f3(matomsPerS)}\n"

            console << String.format("%-9s %8d %8.2f %9.2f %12.3f %11.3f %11.3f %10d %12d %9.1f %12.3f%n",
                    s.id, n, wallS, speedup, median(surfMs), pctl(surfMs, 95), mean(sparseMs),
                    Math.round(avgPts), Math.round(avgSparse), reductionPct, matomsPerS)
        }

        write console.toString()
        String csvPath = "$outdir/surface_strategies.csv"
        writeFile csvPath, csv.toString()
        write "per-strategy stats written to [$csvPath]"

        // 4) equality verification: compare each strategy against the reference OF ITS OWN FAMILY.
        // SurfaceStrategy has two families keyed on requiresSparsification: "full" (cdk/faster/packed,
        // identical point count + atom-major order) and "distinct" (faster_distinct/packed_distinct/_v2/_v3/
        // float_distinct, ~5.7x fewer points). comparePoints is index-aligned, so it is only meaningful
        // WITHIN a family; comparing a distinct strategy against a full reference is a guaranteed count
        // mismatch that proves nothing (and makes the default packed_distinct_v4 look broken). Reference
        // per family: faster / faster_distinct when present, else the first strategy in the family.
        double eps = 1e-6d
        List<SurfaceStrategy> fullFamily = new ArrayList<>()
        List<SurfaceStrategy> distinctFamily = new ArrayList<>()
        for (SurfaceStrategy s : strategies) (s.requiresSparsification ? fullFamily : distinctFamily).add(s)
        SurfaceStrategy fullRef = pickFamilyRef(fullFamily, "faster")
        SurfaceStrategy distinctRef = pickFamilyRef(distinctFamily, "faster_distinct")

        ConcurrentLinkedQueue<Object[]> eqQ = new ConcurrentLinkedQueue<>()
        Queue<String> eqErrors = new ConcurrentLinkedQueue<>()
        runParallel(proteins, threads, eqErrors) { ProteinInput input ->
            compareWithinFamily(input, fullFamily, fullRef, solventRadius, tess, eps, eqQ)
            compareWithinFamily(input, distinctFamily, distinctRef, solventRadius, tess, eps, eqQ)
        }
        if (!eqErrors.empty) write "equality pass: ${eqErrors.size()} failures (first: ${eqErrors.peek()})"

        StringBuilder eqCsv = new StringBuilder("strategy,reference,compared,binary_equal,within_eps,count_mismatch,max_abs_diff_A,epsilon_A\n")
        StringBuilder eqOut = new StringBuilder()
        eqOut << String.format("%n=== surface equality vs per-family reference  (epsilon = %.0e A) ===%n", eps)
        eqOut << String.format("%-18s %-15s %9s %13s %11s %15s %16s%n",
                "strategy", "reference", "compared", "binary_equal", "within_eps", "count_mismatch", "max_abs_diff_A")
        eqOut << ("-" * 100) << "\n"
        for (SurfaceStrategy s : strategies) {
            SurfaceStrategy famRef = s.requiresSparsification ? fullRef : distinctRef
            if (famRef == null || s.is(famRef)) continue
            int compared = 0, binEq = 0, withinEps = 0, mism = 0
            double maxd = 0d
            for (Object[] r : eqQ) {
                if (!((String) r[0]).equals(s.id)) continue
                compared++
                if (((double) r[2]) > 0) mism++
                if (((double) r[3]) > 0) binEq++
                if (((double) r[4]) > 0) withinEps++
                double d = (double) r[5]
                if (!Double.isNaN(d) && d > maxd) maxd = d
            }
            eqOut << String.format("%-18s %-15s %9d %13d %11d %15d %16.3e%n", s.id, famRef.id, compared, binEq, withinEps, mism, maxd)
            eqCsv << "${s.id},${famRef.id},${compared},${binEq},${withinEps},${mism},${String.format('%.6e', maxd)},${String.format('%.0e', eps)}\n"
        }
        write eqOut.toString()
        String eqPath = "$outdir/surface_equality.csv"
        writeFile eqPath, eqCsv.toString()
        write "equality stats written to [$eqPath]"
    }

    /**
     * Compare two equally-ordered surface point lists. Returns
     * {@code [countMismatch(1/0), binaryExact(1/0), withinEpsilon(1/0), maxAbsCoordDiff]}. On a size
     * mismatch returns {@code [1,0,0,NaN]} (no coordinate comparison possible).
     */
    private static double[] comparePoints(List<Atom> a, List<Atom> b, double eps) {
        int n = a.size()
        if (n != b.size()) return [1d, 0d, 0d, Double.NaN] as double[]
        double maxd = 0d
        for (int i = 0; i < n; i++) {
            Atom pa = a.get(i), pb = b.get(i)
            double dx = Math.abs(pa.x - pb.x)
            double dy = Math.abs(pa.y - pb.y)
            double dz = Math.abs(pa.z - pb.z)
            if (dx > maxd) maxd = dx
            if (dy > maxd) maxd = dy
            if (dz > maxd) maxd = dz
        }
        return [0d, (maxd == 0d ? 1d : 0d), (maxd <= eps ? 1d : 0d), maxd] as double[]
    }

    /** Per-family equality reference: the {@code preferredId} strategy if the family contains it, else the first (null if empty). */
    private static SurfaceStrategy pickFamilyRef(List<SurfaceStrategy> family, String preferredId) {
        for (SurfaceStrategy s : family) if (s.id == preferredId) return s
        return family.isEmpty() ? null : family.get(0)
    }

    /**
     * Compare every non-reference member of {@code family} against {@code famRef} for one protein, appending
     * {@code [strategyId, refId, mismatch, exact, withinEps, maxAbsDiff]} rows to {@code out}. No-op if the
     * family has no reference (empty family).
     */
    private static void compareWithinFamily(ProteinInput input, List<SurfaceStrategy> family, SurfaceStrategy famRef,
                                            double solventRadius, int tess, double eps, Queue<Object[]> out) {
        if (famRef == null) return
        List<Atom> refPts = famRef.compute(input.container, solventRadius, tess).points.list
        for (SurfaceStrategy s : family) {
            if (s.is(famRef)) continue
            List<Atom> pts = s.compute(input.container, solventRadius, tess).points.list
            double[] c = comparePoints(refPts, pts, eps)   // [mismatch, exact, withinEps, maxAbsDiff]
            out.add([s.id, famRef.id, c[0], c[1], c[2], c[3]] as Object[])
        }
    }

    /** Run {@code task} over {@code items} on {@code threads} (serial if 1); per-item failures are collected. */
    private static void runParallel(List<ProteinInput> items, int threads, Queue<String> errors, Closure task) {
        if (threads <= 1) {
            for (ProteinInput it : items) {
                try { task.call(it) } catch (Throwable e) { errors.add("${it.name}: ${e.message}".toString()) }
            }
            return
        }
        ExecutorService ex = Executors.newFixedThreadPool(threads)
        try {
            List<Future> futures = new ArrayList<>(items.size())
            for (ProteinInput it : items) {
                final ProteinInput item = it   // per-iteration capture: closures must NOT share the loop var
                futures.add(ex.submit({ ->
                    try { task.call(item) } catch (Throwable e) { errors.add("${item.name}: ${e.message}".toString()) }
                } as Runnable))
            }
            for (Future fut : futures) fut.get()
        } finally {
            ex.shutdownNow()
        }
    }

    private static double mean(double[] a) { if (a.length == 0) return 0; double s = 0; for (double v : a) s += v; return s / a.length }
    private static double median(double[] sorted) { sorted.length == 0 ? 0 : sorted[(int) (sorted.length / 2)] }
    private static double pctl(double[] sorted, double p) {
        if (sorted.length == 0) return 0
        int idx = (int) Math.ceil(p / 100.0d * sorted.length) - 1
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))]
    }
    private static String f3(double v) { String.format("%.3f", v) }
    private static String f1(double v) { String.format("%.1f", v) }

    //===========================================================================================================//
    // surface-density
    //===========================================================================================================//

    /** Mesh nearest-neighbour distance thresholds (A) - the spacing sweep over DISTINCT surface points. */
    private static final double[] DENSITY_EPS = [0.001d, 0.01d, 0.05d, 0.1d, 0.25d, 0.5d, 1.0d] as double[]
    /** Distance under which two points are treated as the same location (collapses exact tessellation dups). */
    private static final double EXACT_EPS = 1e-4d

    /** Per-protein surface density measurement. */
    @CompileStatic
    private static class DensityResult {
        long points, atoms, uniqueLocs, keptSparsified
        double area, sumNn, maxNn   // NN stats over the DISTINCT (unique-location) points
        long[] leCounts             // # distinct points whose nearest other distinct point is within DENSITY_EPS[k]
    }

    /**
     * Analyze the point density of the selected surface strategy ({@code surface_strategy}) over a
     * dataset, and quantify how many surface points are (near-)identical - i.e. why p2rank sparsifies.
     *
     * <p>For each protein it builds the RAW (pre-sparsification) surface and reports:
     * <ul>
     *   <li>raw density: points per atom and per A^2;</li>
     *   <li>exact duplication: distinct locations vs raw points, and the mean multiplicity. The CDK /
     *       Faster / Packed icosahedral tessellation emits every direction with multiplicity >=5
     *       (shared triangle vertices), so essentially every raw point has exact coincident twins;</li>
     *   <li>mesh spacing: nearest-neighbour distance among the DISTINCT points (a meaningful density,
     *       unlike NN over raw points which is ~0 due to the coincident twins), as a cumulative sweep
     *       of the fraction of distinct points whose nearest other distinct point is within each eps;</li>
     *   <li>the actual reduction from sparsification at the production 0.05 A threshold.</li>
     * </ul>
     */
    void cmdSurfaceDensity() {
        SurfaceStrategy strategy = SurfaceStrategy.resolve(params)
        double solventRadius = params.solvent_radius
        int tess = params.tessellation
        double sparsifyDist = Surface.SPARSIFY_DIST
        write "surface-density: strategy=${strategy.id} solventRadius=$solventRadius tessellation=$tess sparsifyDist=$sparsifyDist threads=${params.threads}"

        ConcurrentLinkedQueue<DensityResult> resultsQ = new ConcurrentLinkedQueue<>()
        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            IAtomContainer c = CdkUtils.toAtomContainer(p.proteinAtoms)
            SurfaceStrategy.RawSurface raw = strategy.compute(c, solventRadius, tess)
            resultsQ.add(analyzeDensity(raw, p.proteinAtoms.count, sparsifyDist))
        }
        write res.writeErrorsAndGetSummary(outdir)

        List<DensityResult> results = new ArrayList<>(resultsQ)
        if (results.empty) { write "no surfaces analyzed"; return }

        long totProteins = results.size()
        long totPoints = 0, totUnique = 0, totKept = 0
        double totSumNn = 0, totMaxNn = 0, sumPtsPerAtom = 0, sumPtsPerA2 = 0
        long[] totLe = new long[DENSITY_EPS.length]
        for (DensityResult r : results) {
            totPoints += r.points; totUnique += r.uniqueLocs; totKept += r.keptSparsified
            totSumNn += r.sumNn
            if (r.maxNn > totMaxNn) totMaxNn = r.maxNn
            sumPtsPerAtom += r.atoms > 0 ? (double) r.points / r.atoms : 0
            sumPtsPerA2 += r.area > 0 ? r.points / r.area : 0
            for (int k = 0; k < totLe.length; k++) totLe[k] += r.leCounts[k]
        }
        double multiplicity = totUnique > 0 ? (double) totPoints / totUnique : 0
        double exactDupPct = 100.0 * (1 - (double) totUnique / totPoints)
        double removedPct = 100.0 * (1 - (double) totKept / totPoints)
        double nnMean = totUnique > 0 ? totSumNn / totUnique : 0

        StringBuilder out = new StringBuilder()
        out << String.format("%n=== surface density (strategy=%s) over %d proteins ===%n", strategy.id, totProteins)
        out << String.format("raw surface points : %d total, %.0f / protein, %.2f / atom, %.3f / A^2%n",
                totPoints, (double) totPoints / totProteins, sumPtsPerAtom / totProteins, sumPtsPerA2 / totProteins)
        out << String.format("exact duplication  : %d distinct locations -> mean multiplicity %.2fx (%.1f%% of raw points are exact coincident duplicates)%n",
                totUnique, multiplicity, exactDupPct)
        out << String.format("sparsify @ %.2f A   : %d -> %d points (%.1f%% removed)%n", sparsifyDist, totPoints, totKept, removedPct)
        out << String.format("mesh spacing (NN between distinct points): mean %.4f A, max %.3f A%n", nnMean, totMaxNn)
        out << String.format("%n%-12s %18s%n", "NN <= eps(A)", "% of distinct pts")
        out << ("-" * 34) << "\n"
        for (int k = 0; k < DENSITY_EPS.length; k++) {
            out << String.format("%-12s %17.2f%%%n", f3(DENSITY_EPS[k]), totUnique > 0 ? 100.0 * totLe[k] / totUnique : 0)
        }
        write out.toString()

        StringBuilder csv = new StringBuilder("strategy,proteins,total_points,distinct_locations,multiplicity,exact_dup_pct,points_per_protein,points_per_atom,points_per_A2,sparsify_dist_A,sparsify_kept,sparsify_removed_pct,mesh_nn_mean_A,mesh_nn_max_A")
        for (double e : DENSITY_EPS) csv << ",mesh_nn_within_${f3(e)}_pct"
        csv << "\n"
        csv << "${strategy.id},${totProteins},${totPoints},${totUnique},${f3(multiplicity)},${f1(exactDupPct)},${f1((double) totPoints / totProteins)},${f3(sumPtsPerAtom / totProteins)},${f3(sumPtsPerA2 / totProteins)},${f3(sparsifyDist)},${totKept},${f1(removedPct)},${f3(nnMean)},${f3(totMaxNn)}"
        for (int k = 0; k < DENSITY_EPS.length; k++) {
            double pct = 0d
            if (totUnique > 0) pct = 100.0d * totLe[k] / (double) totUnique
            csv << ("," + f1(pct))
        }
        csv << "\n"
        String csvPath = "$outdir/surface_density.csv"
        writeFile csvPath, csv.toString()
        write "density stats written to [$csvPath]"
    }

    /** Build the per-protein density measurement for one raw surface. */
    private static DensityResult analyzeDensity(SurfaceStrategy.RawSurface raw, int atomCount, double sparsifyDist) {
        Atoms pts = raw.points
        int n = pts.count
        DensityResult r = new DensityResult()
        r.points = n
        r.atoms = atomCount
        r.area = raw.totalSurfaceArea
        r.leCounts = new long[DENSITY_EPS.length]
        if (n == 0) return r

        // distinct surface locations: collapse exact coincident duplicates (the tessellation multiplicity)
        Atoms distinct = AtomDeduplicator.sparsify(pts, EXACT_EPS)
        r.uniqueLocs = distinct.count
        // actual production sparsification reduction (greedy, 0.05 A)
        r.keptSparsified = AtomDeduplicator.sparsify(pts, sparsifyDist).count

        // mesh spacing: nearest-other-distinct-point distance over the DISTINCT set (NN over raw points
        // is ~0 because every raw point has exact coincident twins, so we measure distinct-point spacing)
        distinct.buildKdTree()
        AtomKdTree tree = distinct.getKdTree()
        double sum = 0, max = 0
        for (Atom a : distinct.list) {
            double d = tree.nearestDifferentDist(a)
            sum += d
            if (d > max) max = d
            for (int k = 0; k < DENSITY_EPS.length; k++) {
                if (d <= DENSITY_EPS[k]) r.leCounts[k]++
            }
        }
        r.sumNn = sum
        r.maxNn = max
        return r
    }

}
