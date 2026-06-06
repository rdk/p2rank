package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.PrankException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Static registry of {@link PocketShapeFiller} strategies. Populated at class-load
 * with the two built-in fillers. Selection at runtime is name-driven via
 * {@code -pocket_grid_fill}.
 *
 * <p>Mirrors {@code PocketAssignerRegistry} on purpose so the two parallel
 * decisions (assign + fill) have the same shape in code and in error messages.
 *
 * <p>Adding a new filler = drop a new {@link PocketShapeFiller} implementation in
 * this package and register it here. Each entry stores a fresh-instance supplier
 * because fillers are cheap and the builder calls them per-protein.
 */
public final class PocketShapeFillerRegistry {

    private static final Map<String, Supplier<PocketShapeFiller>> REGISTRY = new LinkedHashMap<>();

    static {
        register("morph_closing", MorphologicalCloser::new);
        register("closing",       ErodeDilateCloser::new);   // default: true dilate-then-erode closing
        register("none",          NoOpFiller::new);
    }

    private PocketShapeFillerRegistry() {}

    private static void register(String name, Supplier<PocketShapeFiller> factory) {
        REGISTRY.put(name, factory);
    }

    /** @throws PrankException if {@code name} is unknown. */
    public static PocketShapeFiller get(String name) {
        Supplier<PocketShapeFiller> f = REGISTRY.get(name);
        if (f == null) {
            throw new PrankException(
                    "Unknown pocket_grid_fill strategy: '" + name + "'. Known: " + knownNames());
        }
        return f.get();
    }

    /** @return names in registration order. */
    public static Set<String> knownNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

}
