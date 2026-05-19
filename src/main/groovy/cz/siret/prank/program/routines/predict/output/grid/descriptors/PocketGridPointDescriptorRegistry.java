package cz.siret.prank.program.routines.predict.output.grid.descriptors;

import cz.siret.prank.program.PrankException;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static registry of pocket-grid-point descriptors. Java mirror of
 * {@code PocketDescriptorRegistry} — same pluggable pattern; selection at
 * runtime is name-driven via the {@code -pocket_grid_point_descriptors} list
 * param.
 *
 * <p>Adding a new descriptor = drop a new {@link PocketGridPointDescriptor}
 * implementation in this package and register it here.
 */
public final class PocketGridPointDescriptorRegistry {

    private static final Map<String, PocketGridPointDescriptor> REGISTRY = new LinkedHashMap<>();

    static {
        register(new VolsiteGridPointDescriptor());
        register(new VolsiteSmoothGridPointDescriptor());
    }

    private PocketGridPointDescriptorRegistry() {}

    /**
     * Add a descriptor to the registry. Called from the static initializer for
     * the shipped descriptors; also exposed for tests that need to register a
     * fixture descriptor and for future external descriptor plugins.
     */
    public static void register(PocketGridPointDescriptor d) {
        List<String> cols = d.columnNames();
        List<?> types = d.columnTypes();
        if (cols.size() != types.size()) {
            throw new IllegalStateException(
                    "Descriptor '" + d.name() + "' has columnNames.size()=" + cols.size()
                    + " but columnTypes.size()=" + types.size() + "; they must be parallel.");
        }
        if (cols.size() > 1 && new HashSet<>(cols).size() != cols.size()) {
            throw new IllegalStateException(
                    "Descriptor '" + d.name() + "' declares duplicate columnNames: " + cols);
        }
        REGISTRY.put(d.name(), d);
    }

    /**
     * Remove a descriptor by name. Intended for tests that register a fixture
     * descriptor via {@link #register} and need to undo the side effect in an
     * {@code @AfterAll} hook so the registry's known-names set doesn't leak
     * across test classes. No-op if {@code name} is not registered.
     */
    public static void unregister(String name) {
        REGISTRY.remove(name);
    }

    /** @throws PrankException if {@code name} is unknown. */
    public static PocketGridPointDescriptor get(String name) {
        PocketGridPointDescriptor d = REGISTRY.get(name);
        if (d == null) {
            throw new PrankException(
                    "Unknown pocket-grid-point descriptor: '" + name + "'. Known: " + knownNames());
        }
        return d;
    }

    /** @return names in registration order ({@link LinkedHashMap}). */
    public static Set<String> knownNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

}
