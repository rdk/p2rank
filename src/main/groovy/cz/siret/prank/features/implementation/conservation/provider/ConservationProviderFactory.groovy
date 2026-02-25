package cz.siret.prank.features.implementation.conservation.provider

import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

/**
 * Creates and caches ConservationProvider instances based on params.
 */
@Slf4j
@CompileStatic
class ConservationProviderFactory {

    private static volatile ConservationProvider cachedProvider = null
    private static volatile boolean initialized = false

    /**
     * Create a new provider based on current params, or null if no provider is configured.
     */
    @Nullable
    static ConservationProvider createProvider() {
        Params params = Params.INSTANCE
        String providerName = params.conservation_provider

        if (providerName == null || providerName.isEmpty()) {
            return null
        }

        String url = params.conservation_provider_url
        if (url == null || url.isEmpty()) {
            throw new PrankException(
                "conservation_provider_url must be set when conservation_provider='${providerName}'")
        }

        int timeout = params.conservation_provider_timeout
        int maxConcurrent = params.conservation_provider_threads
        if (maxConcurrent <= 0) {
            maxConcurrent = params.threads
        }

        switch (providerName) {
            case "hmm_server":
                log.info "Creating HMM server conservation provider: url={}, timeout={}s, maxConcurrent={}",
                    url, timeout, maxConcurrent
                return new HmmServerConservationProvider(url, timeout, maxConcurrent)
            default:
                throw new PrankException("Unknown conservation_provider: '${providerName}'")
        }
    }

    /**
     * Get or create a cached provider singleton.
     * Thread-safe via double-checked locking.
     */
    @Nullable
    static ConservationProvider getOrCreateProvider() {
        if (!initialized) {
            synchronized (ConservationProviderFactory) {
                if (!initialized) {
                    cachedProvider = createProvider()
                    initialized = true
                }
            }
        }
        return cachedProvider
    }

    /**
     * If a conservation provider is configured, create it and check its health.
     * Throws PrankException with a clear message if the server is not available.
     * Does nothing if no provider is configured.
     */
    static void checkProviderHealthIfConfigured() {
        Params params = Params.INSTANCE
        String providerName = params.conservation_provider
        if (providerName == null || providerName.isEmpty()) {
            return
        }

        ConservationProvider provider = createProvider()
        try {
            provider.checkHealth()
        } catch (ConservationProviderException e) {
            throw new PrankException(e.message, e)
        }
    }

    /**
     * Reset the cached provider. Call between test runs or when params change.
     */
    static void reset() {
        synchronized (ConservationProviderFactory) {
            cachedProvider = null
            initialized = false
        }
    }

}
