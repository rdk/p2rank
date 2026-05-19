package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.PrankException;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static registry of pocket descriptors. Populated at class-load with the
 * shipped set. Selection at runtime is name-driven via the
 * {@code -pocket_descriptors} list param.
 *
 * <p>Mirrors {@link cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptorRegistry}
 * — same shape, same register/unregister/get/knownNames surface. Multi-column
 * descriptors are validated at registration time (no duplicate sub-column names
 * within one descriptor).
 *
 * <p>Adding a new descriptor = drop a new {@link PocketDescriptor}
 * implementation in this package and register it here.
 */
public final class PocketDescriptorRegistry {

    private static final Map<String, PocketDescriptor> REGISTRY = new LinkedHashMap<>();

    static {
        register(new VolumeDescriptor());
        register(new SphericityDescriptor());
        register(new RadiusOfGyrationDescriptor());
        register(new NumResiduesDescriptor());
        register(new NumSurfaceAtomsDescriptor());
        register(new NumGridPointsDescriptor());
        register(new PrincipalMomentsDescriptor());
    }

    private PocketDescriptorRegistry() {}

    /**
     * Add a descriptor to the registry. Public so tests can register fixture
     * descriptors and future external descriptor plugins can register without
     * touching the static initializer.
     */
    public static void register(PocketDescriptor d) {
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
     * {@code @AfterAll} hook so the registry doesn't leak across test classes.
     * No-op if {@code name} is not registered.
     */
    public static void unregister(String name) {
        REGISTRY.remove(name);
    }

    /** @throws PrankException if {@code name} is unknown. */
    public static PocketDescriptor get(String name) {
        PocketDescriptor d = REGISTRY.get(name);
        if (d == null) {
            throw new PrankException(
                    "Unknown pocket descriptor: '" + name + "'. Known: " + knownNames());
        }
        return d;
    }

    /** @return names in registration order ({@link LinkedHashMap}). */
    public static Set<String> knownNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

}
