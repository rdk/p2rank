package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.NamedRegistryHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static registry of pocket descriptors. Populated at class-load with the
 * built-in set. Selection at runtime is name-driven via the
 * {@code -pocket_descriptors} list param.
 *
 * <p>Mirrors {@link cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptorRegistry}
 * — same shape, same {@code register/unregister/get/knownNames} surface.
 * Common boilerplate is delegated to {@link NamedRegistryHelper}; the
 * descriptor-specific invariant (matching {@code columnNames}/{@code columnTypes}
 * sizes, no duplicate sub-column names) lives in {@link #validate}.
 *
 * <p>Adding a new descriptor = drop a new {@link PocketDescriptor}
 * implementation in this package and register it here.
 */
public final class PocketDescriptorRegistry {

    private static final NamedRegistryHelper<PocketDescriptor> REG = new NamedRegistryHelper<>(
            "pocket descriptor", PocketDescriptor::name, PocketDescriptorRegistry::validate);

    static {
        register(new VolumeDescriptor());
        register(new SphericityDescriptor());
        register(new RadiusOfGyrationDescriptor());
        register(new NumResiduesDescriptor());
        register(new NumSurfaceAtomsDescriptor());
        register(new NumGridPointsDescriptor());
        register(new PrincipalMomentsDescriptor());
        register(new PocketNetChargeDescriptor());
        register(new PocketChargePolarityDescriptor());
        register(new PocketDipoleMagnitudeDescriptor());
    }

    private PocketDescriptorRegistry() {}

    /**
     * Add a descriptor to the registry. Public so tests can register fixture
     * descriptors and future external descriptor plugins can register without
     * touching the static initializer.
     */
    public static void register(PocketDescriptor d) {
        REG.register(d);
    }

    /**
     * Remove a descriptor by name. Intended for tests that register a fixture
     * descriptor via {@link #register} and need to undo the side effect in an
     * {@code @AfterAll} hook so the registry doesn't leak across test classes.
     * No-op if {@code name} is not registered.
     */
    public static void unregister(String name) {
        REG.unregister(name);
    }

    public static PocketDescriptor get(String name) {
        return REG.get(name);
    }

    /** @return names in registration order. */
    public static Set<String> knownNames() {
        return REG.knownNames();
    }

    private static void validate(PocketDescriptor d) {
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
