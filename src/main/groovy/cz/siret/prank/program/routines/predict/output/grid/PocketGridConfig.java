package cz.siret.prank.program.routines.predict.output.grid;

import cz.siret.prank.program.params.Params;

/**
 * Knobs for {@link PocketGridBuilder#build}. Decoupled from
 * {@link Params} so unit tests don't need a global params fixture — they
 * construct the record directly. Production callers (the routine bridge and
 * the bench harness) use {@link #fromParams} to materialize one from runtime
 * params; defining the factory here keeps the param→config mapping in one
 * place — adding a 9th knob means updating this file only.
 */
public record PocketGridConfig(
        double spacing,
        double maxDist,
        double atomBuffer,
        double assignCutoff,
        String assignerStrategy,
        String fillStrategy,
        int fillMinNeighbors,
        int fillMaxIters) {

    public static PocketGridConfig fromParams(Params p) {
        // Groovy generates getters from each @RuntimeParam field; from Java we go
        // through the get<Name>() accessors (Groovy property syntax `p.foo` won't
        // resolve in javac).
        return new PocketGridConfig(
                p.getPocket_grid_spacing(),
                p.getPocket_grid_max_dist(),
                p.getPocket_grid_atom_buffer(),
                p.getPocket_grid_assign_cutoff(),
                p.getPocket_grid_assigner(),
                p.getPocket_grid_fill(),
                p.getPocket_grid_fill_min_neighbors(),
                p.getPocket_grid_fill_max_iters());
    }

}
