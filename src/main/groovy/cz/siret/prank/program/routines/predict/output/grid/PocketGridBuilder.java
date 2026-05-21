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
 *   <li>Sample lattice points in the pocket-vicinity shell defined by
 *       {@code Pocket.sasPoints} (union) and the configured bounds — see
 *       {@link GridGenerator#sampleGridPointsBetween}.</li>
 *   <li>Build a lattice index for the kept points (for O(1) neighbor lookup).</li>
 *   <li>For each pocket: compute the raw shell (points within {@code assignCutoff}
 *       of any of the pocket's {@code sasPoints}), then apply the chosen
 *       {@link PocketShapeFiller}.</li>
 * </ol>
 *
 * <p>One grid point may belong to multiple pockets. The result is an immutable
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
        // The union of pocket SAS points drives both the lattice bounding box and the
        // per-cell outer bound. Use Atoms.join (plain concat) since per-pocket SAS sets
        // are disjoint by construction (each SAS point belongs to one cluster).
        List<Atoms> sasPerPocket = new ArrayList<>(pockets.size());
        for (Pocket pocket : pockets) {
            Atoms sas = pocket.getSasPoints();
            if (sas != null && !sas.isEmpty()) sasPerPocket.add(sas);
        }
        Atoms allSasPoints = Atoms.join(sasPerPocket);
        if (allSasPoints.isEmpty()) {
            log.warn("No pocket has sasPoints — pocket grid will be empty.");
        }

        // Sampler returns the kept points plus the origin it picked; consume both to
        // keep lattice-coord math consistent with what the sampler used.
        GridSample sample = GridGenerator.sampleGridPointsBetween(
                protein.getProteinAtoms(), allSasPoints,
                config.spacing(), config.maxDist(), config.atomBuffer());

        Atoms allPoints = sample.points();
        int n = allPoints.getCount();

        // latticeIndex (long-packed (i,j,k) → idx) is the single canonical lookup
        // from "where in lattice space" to "where in allPoints". Primitive long→int
        // map (HPPC) avoids the Long/Integer boxing that dominated GC with HashMap.
        LongIntHashMap latticeIndex = new LongIntHashMap(n);

        // pocketToPointIndices uses BitSet (not Set<Integer>) — zero autoboxing on
        // add/contains/iterate, ~32× smaller memory.
        Map<Integer, BitSet> pocketToPointIndices = new HashMap<>(pockets.size() * 2);

        PocketGrid grid = new PocketGrid(
                allPoints, config.spacing(), sample.originX(), sample.originY(), sample.originZ(),
                latticeIndex, pocketToPointIndices);

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

        for (Pocket pocket : pockets) {
            Atoms inputs = pocket.getSasPoints();
            BitSet raw = (inputs == null || inputs.isEmpty())
                    ? new BitSet()
                    : assigner.computeRawShell(inputs, grid, assignCutoff);
            BitSet filled = filler.fill(raw, grid, config.fillMinNeighbors(), config.fillMaxIters());
            pocketToPointIndices.put(pocket.getRank(), filled);
        }

        log.info("PocketGrid built: {} kept points, {} pockets, fill={}",
                n, pockets.size(), config.fillStrategy());
        return grid;
    }

}
