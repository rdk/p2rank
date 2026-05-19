package cz.siret.prank.program.routines.predict.output;

import cz.siret.prank.program.PrankException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Composition helper for name-keyed registries. Collapses the
 * {@code register/unregister/get/knownNames} boilerplate shared between
 * {@link cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptorRegistry}
 * and
 * {@link cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptorRegistry}.
 *
 * <p>Composition (not inheritance) is deliberate — each registry keeps its
 * public {@code static} API ({@code Registry.get(name)} etc.) and delegates
 * to an instance of this helper. Existing call sites are unchanged.
 *
 * <p>Iteration order is registration order ({@link LinkedHashMap}).
 *
 * @param <T> the registered type (a "named" entity — name is supplied via
 *           {@code nameFn} at construction).
 */
public final class NamedRegistryHelper<T> {

    private final Map<String, T> registry = new LinkedHashMap<>();
    private final String entityLabel;
    private final Function<T, String> nameFn;
    private final Consumer<T> validator;

    /**
     * @param entityLabel  human-readable label for {@code get(unknown)} errors
     *                     (e.g. {@code "pocket descriptor"})
     * @param nameFn       extracts the name used as the registry key
     * @param validator    pre-add invariant check; pass {@code t -> {}} for no extra check
     */
    public NamedRegistryHelper(String entityLabel, Function<T, String> nameFn, Consumer<T> validator) {
        this.entityLabel = entityLabel;
        this.nameFn = nameFn;
        this.validator = validator;
    }

    /**
     * Add or replace an entry. Validator (if any) runs first, then the entry is
     * keyed by {@code nameFn.apply(t)}. Re-registering with an existing name
     * overwrites silently — intentional, used by tests to install fixture
     * implementations.
     */
    public void register(T t) {
        validator.accept(t);
        registry.put(nameFn.apply(t), t);
    }

    public void unregister(String name) {
        registry.remove(name);
    }

    /** @throws PrankException if {@code name} is unknown. */
    public T get(String name) {
        T t = registry.get(name);
        if (t == null) {
            throw new PrankException(
                    "Unknown " + entityLabel + ": '" + name + "'. Known: " + knownNames());
        }
        return t;
    }

    public Set<String> knownNames() {
        return Collections.unmodifiableSet(registry.keySet());
    }

}
