package cz.siret.prank.program.routines.predict.output.grid.assign;

import cz.siret.prank.program.PrankException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static registry of {@link PocketAssigner} strategies. Populated at class-load with
 * the two built-in assigners. Selection at runtime is name-driven via
 * {@code -pocket_grid_assigner}.
 *
 * <p>Adding a new assigner = drop a new {@link PocketAssigner} implementation in this
 * package and register it here.
 */
public final class PocketAssignerRegistry {

    private static final Map<String, PocketAssigner> REGISTRY = new LinkedHashMap<>();

    static {
        register(new KdTreeAssigner());
        register(new VoxelHashAssigner());
    }

    private PocketAssignerRegistry() {}

    private static void register(PocketAssigner a) {
        REGISTRY.put(a.name(), a);
    }

    /** @throws PrankException if {@code name} is unknown. */
    public static PocketAssigner get(String name) {
        PocketAssigner a = REGISTRY.get(name);
        if (a == null) {
            throw new PrankException(
                    "Unknown pocket assigner: '" + name + "'. Known: " + knownNames());
        }
        return a;
    }

    /** @return names in registration order. */
    public static Set<String> knownNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

}
