package cz.siret.prank.program.routines.predict.output.grid.descriptors;

import cz.siret.prank.program.routines.predict.output.NamedRegistryHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static registry of pocket-grid-point descriptors. Java mirror of
 * {@code PocketDescriptorRegistry} — same pluggable pattern; selection at
 * runtime is name-driven via the {@code -pocket_grid_point_descriptors} list
 * param. Common boilerplate is delegated to {@link NamedRegistryHelper}.
 *
 * <p>Adding a new descriptor = drop a new {@link PocketGridPointDescriptor}
 * implementation in this package and register it here.
 */
public final class PocketGridPointDescriptorRegistry {

    private static final NamedRegistryHelper<PocketGridPointDescriptor> REG = new NamedRegistryHelper<>(
            "pocket-grid-point descriptor",
            PocketGridPointDescriptor::name,
            PocketGridPointDescriptorRegistry::validate);

    static {
        register(new VolsiteGridPointDescriptor());
        register(new VolsiteSmoothGridPointDescriptor());
        register(new ElectrostaticsGridPointDescriptor());
    }

    private PocketGridPointDescriptorRegistry() {}

    /**
     * Add a descriptor to the registry. Called from the static initializer for
     * the built-in descriptors; also exposed for tests that need to register a
     * fixture descriptor and for future external descriptor plugins.
     */
    public static void register(PocketGridPointDescriptor d) {
        REG.register(d);
    }

    /**
     * Remove a descriptor by name. Intended for tests that register a fixture
     * descriptor via {@link #register} and need to undo the side effect in an
     * {@code @AfterAll} hook so the registry's known-names set doesn't leak
     * across test classes. No-op if {@code name} is not registered.
     */
    public static void unregister(String name) {
        REG.unregister(name);
    }

    public static PocketGridPointDescriptor get(String name) {
        return REG.get(name);
    }

    /** @return names in registration order. */
    public static Set<String> knownNames() {
        return REG.knownNames();
    }

    private static void validate(PocketGridPointDescriptor d) {
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
    }

}
