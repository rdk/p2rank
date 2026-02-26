package cz.siret.prank.program.routines

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.domain.loaders.ConservationLoader
import cz.siret.prank.export.FastaExporter
import cz.siret.prank.features.implementation.conservation.provider.ConservationProvider
import cz.siret.prank.features.implementation.conservation.provider.ConservationProviderException
import cz.siret.prank.features.implementation.conservation.provider.ConservationProviderFactory
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * Routine for preloading conservation scores from an external provider.
 * Iterates over all proteins/chains in the dataset, fetches missing scores
 * from the provider, and stores them in the local cache.
 */
@Slf4j
@CompileStatic
class PreloadConservationRoutine implements Parametrized, Writable {

    private Dataset dataset

    PreloadConservationRoutine(Dataset dataset) {
        this.dataset = dataset
    }

    void execute() {
        ConservationProvider provider = ConservationProviderFactory.createProvider()
        if (provider == null) {
            throw new PrankException("conservation_provider must be set for preload-conservation command")
        }

        if (params.conservation_disable_cache) {
            throw new PrankException("Cannot use preload-conservation with conservation_disable_cache=true")
        }

        String conservationType = params.conservation_type
        if (conservationType == null || conservationType.isEmpty()) {
            throw new PrankException("conservation_type must be set for preload-conservation command")
        }

        int nThreads = params.conservation_provider_threads > 0
            ? params.conservation_provider_threads
            : params.threads

        // Collect chain tasks by streaming through proteins (load one, extract, discard)
        List<ChainTask> chainTasks = collectChainTasks(conservationType)

        AtomicInteger cachedCount = new AtomicInteger(0)
        AtomicInteger fetchedCount = new AtomicInteger(0)
        AtomicInteger failedCount = new AtomicInteger(0)
        List<String> failureReasons = Collections.synchronizedList(new ArrayList<String>())
        ConcurrentLinkedQueue<Long> fetchTimes = new ConcurrentLinkedQueue<>()

        write "Preloading conservation for ${chainTasks.size()} chains using $nThreads threads"

        ExecutorService executor = Executors.newFixedThreadPool(nThreads)
        List<Future<?>> futures = new ArrayList<>(chainTasks.size())

        for (ChainTask ct : chainTasks) {
            final ChainTask task = ct  // capture current value
            futures.add(executor.submit {
                processChain(task, provider,
                    cachedCount, fetchedCount, failedCount, failureReasons, fetchTimes)
            })
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            future.get()
        }
        executor.shutdownNow()

        // Report summary
        write ""
        write "Preload conservation summary:"
        write "  Total chains:    ${chainTasks.size()}"
        write "  Already cached:  ${cachedCount.get()}"
        write "  Newly fetched:   ${fetchedCount.get()}"
        write "  Failed:          ${failedCount.get()}"
        if (!fetchTimes.isEmpty()) {
            List<Long> times = new ArrayList<>(fetchTimes)
            long maxTime = Collections.max(times)
            long totalTime = 0L
            for (Long t : times) { totalTime += t }
            long avgTime = (long) totalTime.intdiv(times.size())
            write "  Fetch time avg:  ${formatTime(avgTime)}"
            write "  Fetch time max:  ${formatTime(maxTime)}"
        }
        if (!failureReasons.isEmpty()) {
            write "  Failure reasons:"
            for (String reason : failureReasons) {
                write "    - $reason"
            }
        }
    }

    private List<ChainTask> collectChainTasks(String conservationType) {
        // Deduplicate by cache file path (same protein+chain = same cache file)
        List<ChainTask> chainTasks = Collections.synchronizedList(new ArrayList<>())

        dataset.processItems { Dataset.Item item ->
            Protein protein = item.protein

            for (ResidueChain chain : protein.residueChains) {
                String chainId = Struct.maskEmptyChainId(chain.authorId)
                String sequence = FastaExporter.maskFastaChain(chain.standardCodeCharString)
                String baseName = Futils.baseName(protein.fileName)
                String label = baseName + "_" + chainId

                File cacheFile = ConservationLoader.getCacheFile(
                    protein.fileName, chainId, conservationType)

                chainTasks.add(
                    new ChainTask(
                        label: label,
                        sequence: sequence,
                        cacheFile: cacheFile
                    )
                )
            }

        }

        // Deduplicate and log stats
        Map<String, ChainTask> uniqueTasks = new LinkedHashMap<>()
        Map<String, Integer> chainCounts = new LinkedHashMap<>()
        for (ChainTask ct : chainTasks) {
            String cacheKey = ct.cacheFile.absolutePath
            chainCounts.put(cacheKey, chainCounts.getOrDefault(cacheKey, 0) + 1)
            if (!uniqueTasks.containsKey(cacheKey)) {
                uniqueTasks.put(cacheKey, ct)
            }
        }

        int duplicates = chainTasks.size() - uniqueTasks.size()
        if (duplicates > 0) {
            write "  (${chainTasks.size()} total chains in dataset, ${duplicates} duplicates removed, ${uniqueTasks.size()} unique)"
            write "  Duplicated chains:"
            for (Map.Entry<String, Integer> entry : chainCounts.entrySet()) {
                if (entry.value > 1) {
                    ChainTask ct = uniqueTasks.get(entry.key)
                    write "    ${ct.label} (${entry.value}x)"
                }
            }
        }

        // log all chain tasks
        log.debug "Unique chains to process:"
        for (ChainTask ct : uniqueTasks.values()) {
            log.debug "  ${ct.label} (cacheFile: ${ct.cacheFile.absolutePath})"
        }

        return new ArrayList<>(uniqueTasks.values())
    }

    private void processChain(ChainTask ct, ConservationProvider provider,
                              AtomicInteger cachedCount,
                              AtomicInteger fetchedCount,
                              AtomicInteger failedCount,
                              List<String> failureReasons,
                              ConcurrentLinkedQueue<Long> fetchTimes) {
        // Check if already cached
        if (ct.cacheFile.exists()) {
            write "  Already cached: [${ct.label}] in ${ct.cacheFile.absolutePath}"
            cachedCount.incrementAndGet()
            return
        }

        // Fetch from provider
        try {
            write "  Fetching: [${ct.label}]"
            long startTime = System.currentTimeMillis()
            String content = provider.fetchScores(ct.sequence, ct.label)
            long elapsed = System.currentTimeMillis() - startTime
            fetchTimes.add(elapsed)
            Futils.writeFile(ct.cacheFile.absolutePath, content)
            write "  Fetched and cached: [${ct.label}] in ${formatTime(elapsed)}"
            fetchedCount.incrementAndGet()
        } catch (ConservationProviderException e) {
            write "  Failed: ${e.message}"
            failedCount.incrementAndGet()
            failureReasons.add(e.message)
            if (params.fail_fast) {
                throw new PrankException("Failed to fetch conservation for [${ct.label}]", e)
            }
        }
    }

    private static String formatTime(long millis) {
        if (millis < 1000) {
            return "${millis}ms"
        } else {
            return String.format("%.1fs", millis / 1000.0d)
        }
    }

    @CompileStatic
    private static class ChainTask {
        String label
        String sequence
        File cacheFile
    }

}
