package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.PrankException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static registry of pocket descriptors. Populated at class-load with the
 * shipped set. Selection at runtime is name-driven via the
 * {@code -pocket_descriptors} list param.
 *
 * <p>Adding a new descriptor = drop a new {@link PocketDescriptor}
 * implementation in this package and register it here.
 *
 * <p>Java mirror of {@code PocketAssignerRegistry} — same pluggable-registry
 * pattern, same package layout.
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
    }

    private PocketDescriptorRegistry() {}

    private static void register(PocketDescriptor d) {
        REGISTRY.put(d.name(), d);
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
