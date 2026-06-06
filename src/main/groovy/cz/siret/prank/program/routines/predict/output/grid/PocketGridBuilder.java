package cz.siret.prank.program.routines.predict.output.grid;

import com.carrotsearch.hppc.LongIntHashMap;
import cz.siret.prank.domain.Pocket;
import cz.siret.prank.domain.Protein;
import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.samplers.GridGenerator;
import cz.siret.prank.geom.samplers.GridSample;
import cz.siret.prank.program.routines.predict.output.grid.assign.PocketAssigner;
import cz.siret.prank.program.routines.predict.output.grid.assign.PocketAssignerRegistry;
import cz.siret.prank.program.routines.predict.output.grid.fill.PocketShapeFiller;
import cz.siret.prank.program.routines.predict.output.grid.fill.PocketShapeFillerRegistry;
import org.biojava.nbio.structure.Atom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the per-protein pocket grid:
 * <ol>
 *   <li>Sample lattice points in a shell around the protein atoms (within
 *       {@code maxDist}, outside the per-atom vdW + {@code atomBuffer} volume) —
 *       see {@link GridGenerator#sampleGridPointsBetween}. The grid spans the
 *       whole protein; per-pocket assignment below restricts it to pockets.</li>
 *   <li>Build a lattice index for the kept points (for O(1) neighbor lookup).</li>
 *   <li>For each pocket: compute the raw shell (points within {@code assignCutoff}
 *       of any of the pocket's {@code sasPoints}), then apply the chosen
 *       {@link PocketShapeFiller}.</li>
 *   <li>Enforce the cross-pocket fill rule (HARD, always on): a point added by
 *       filling (beyond this pocket's {@code assignCutoff}, so not in its raw shell)
 *       is dropped if it lies in another pocket's raw shell. Fill may expand into
 *       unclaimed space but must not swallow grid points that are within
 *       {@code assignCutoff} of a different pocket.</li>
 * </ol>
 *
 * <p>A grid point may still belong to multiple pockets via genuine within-cutoff
 * sharing (it is in more than one pocket's raw shell) — that is not affected by the
 * rule, which only constrains fill expansion. The result is an immutable
 * {@link PocketGrid}.
 */
public final class PocketGridBuilder {

    private static final Logger log = LoggerFactory.getLogger(PocketGridBuilder.class);

    private PocketGridBuilder() {}

    /**
     * @param protein protein whose {@code proteinAtoms} (including cofactors when
     *                CofactorHandler is enabled) gate the inner VdW exclusion
     * @param pockets predicted pockets. Pockets with empty/null {@code sasPoints}
     *                contribute nothing to the lattice bounding box and receive
     *                an empty assignment.
     */
    public static PocketGrid build(Protein protein, List<? extends Pocket> pockets, PocketGridConfig config) {
        // The grid is a shell around the protein atoms (within maxDist, outside the
        // per-atom vdW + atomBuffer volume). Per-pocket SAS points are NOT used for the
        // grid extent — only for the per-pocket assignment (raw shells) below.
        // Sampler returns the kept points plus the origin it picked; consume both to
        // keep lattice-coord math consistent with what the sampler used.
        GridSample sample = GridGenerator.sampleGridPointsBetween(
                protein.getProteinAtoms(),
                config.spacing(), config.maxDist(), config.atomBuffer());

        Atoms allPoints = sample.points();
        int n = allPoints.getCount();

        // latticeIndex (long-packed (i,j,k) → idx) is the single canonical lookup
        // from "where in lattice space" to "where in allPoints". Primitive long→int
        // map (HPPC) avoids the Long/Integer boxing that dominated GC with HashMap.
        LongIntHashMap latticeIndex = new LongIntHashMap(n);

        // pocketToPointIndices uses BitSet (not Set<Integer>) — zero autoboxing on
        // add/contains/iterate, ~32× smaller memory. pocketToRawShell keeps the pre-fill
        // shells as a first-class build output (analyses read them instead of rebuilding).
        Map<Integer, BitSet> pocketToPointIndices = new HashMap<>(pockets.size() * 2);
        Map<Integer, BitSet> pocketToRawShell = new HashMap<>(pockets.size() * 2);

        PocketGrid grid = new PocketGrid(
                allPoints, config.spacing(), sample.originX(), sample.originY(), sample.originZ(),
                latticeIndex, pocketToPointIndices, pocketToRawShell);

        // Populate latticeIndex via the grid's own packLatticeKey — single source of
        // truth for the world→lattice projection (also used by the morph closer and
        // KdTreeAssigner).
        for (int i = 0; i < n; i++) {
            latticeIndex.put(grid.packLatticeKey(allPoints.list.get(i)), i);
        }

        // Strategy selection happens once per build; the per-pocket loop calls
        // the held locals so each call site is at worst bimorphic in the JIT.
        PocketAssigner assigner = PocketAssignerRegistry.get(config.assignerStrategy());
        PocketShapeFiller filler = PocketShapeFillerRegistry.get(config.fillStrategy());
        double assignCutoff = config.assignCutoff();

        assigner.initialize(grid);

        // Pass 1: raw shells (points within assignCutoff of each pocket's SAS points),
        // plus their union. Computing all raw shells up front makes the cross-pocket
        // fill rule below deterministic (independent of pocket order).
        int np = pockets.size();
        List<BitSet> rawShells = new ArrayList<>(np);
        BitSet unionRaw = new BitSet();
        for (Pocket pocket : pockets) {
            Atoms inputs = pocket.getSasPoints();
            BitSet raw = (inputs == null || inputs.isEmpty())
                    ? new BitSet()
                    : assigner.computeRawShell(inputs, grid, assignCutoff);
            rawShells.add(raw);
            unionRaw.or(raw);
            pocketToRawShell.put(pocket.getRank(), raw);
        }

        // No pocket has any grid point within assignCutoff of its SAS points (e.g. pockets
        // from an external predictor that exposes no sasPoints). The tabular export then
        // emits zero rows unless pocket_grid_include_unassigned is on — warn rather than
        // produce a silently empty grid file.
        if (unionRaw.isEmpty()) {
            log.warn("PocketGrid: no pocket has any assigned grid point (no pocket SAS points"
                    + " within assignCutoff={}); tabular grid export will be empty unless"
                    + " -pocket_grid_include_unassigned is set", assignCutoff);
        }

        // Pass 2: fill each pocket, then enforce the cross-pocket fill rule (HARD,
        // always on). A point ADDED BY FILLING (i.e. beyond this pocket's assignCutoff,
        // so not in its raw shell) is dropped if it lies in ANOTHER pocket's raw shell
        // — fill may expand into unclaimed space, but must not swallow grid points that
        // are within assignCutoff of a different pocket. Points in this pocket's own raw
        // shell are never dropped, so genuine within-cutoff interface sharing is kept.
        for (int i = 0; i < np; i++) {
            BitSet raw = rawShells.get(i);
            BitSet filled = filler.fill(raw, grid, config.fillKnobs());

            // drop fill-added points owned (within assignCutoff) by another pocket
            PocketGridAnalysis.applyCrossPocketRule(filled, raw, unionRaw);

            pocketToPointIndices.put(pockets.get(i).getRank(), filled);
        }

        log.info("PocketGrid built: {} kept points, {} pockets, fill={}",
                n, pockets.size(), config.fillStrategy());
        return grid;
    }

}
