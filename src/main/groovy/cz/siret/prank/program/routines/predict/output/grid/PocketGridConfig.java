package cz.siret.prank.program.routines.predict.output.grid;

import cz.siret.prank.program.params.Params;
import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs;

/**
 * Knobs for {@link PocketGridBuilder#build}. Decoupled from
 * {@link Params} so unit tests don't need a global params fixture — they
 * construct the record directly. Production callers (the routine bridge and
 * the bench harness) use {@link #fromParams} to materialize one from runtime
 * params; defining the factory here keeps the param→config mapping in one place.
 *
 * <p>{@code fillKnobs} is a typed {@link FillKnobs} variant matching {@code fillStrategy}
 * (the strategy string selects the filler from the registry; the knobs carry its params).
 */
public record PocketGridConfig(
        double spacing,
        double maxDist,
        double atomBuffer,
        double assignCutoff,
        String assignerStrategy,
        String fillStrategy,
        FillKnobs fillKnobs) {

    public static PocketGridConfig fromParams(Params p) {
        // Groovy generates getters from each @RuntimeParam field; from Java we go
        // through the get<Name>() accessors (Groovy property syntax `p.foo` won't
        // resolve in javac). One place for the param -> typed-knobs mapping per strategy.
        String fill = p.getPocket_grid_fill();
        FillKnobs knobs;
        switch (fill) {
            case "closing" -> knobs = FillKnobs.Closing.symmetric(p.getPocket_grid_fill_close_radius());
            case "morph_closing" -> knobs = new FillKnobs.Morph(
                    p.getPocket_grid_fill_min_neighbors(), p.getPocket_grid_fill_max_iters());
            default -> knobs = new FillKnobs.None();   // none, or unknown (validated/thrown elsewhere)
        }
        return new PocketGridConfig(
                p.getPocket_grid_spacing(),
                p.getPocket_grid_max_dist(),
                p.getPocket_grid_atom_buffer(),
                p.getPocket_grid_assign_cutoff(),
                p.getPocket_grid_assigner(),
                fill,
                knobs);
    }

}
