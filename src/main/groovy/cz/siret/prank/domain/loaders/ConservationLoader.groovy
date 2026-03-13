package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.provider.ConservationProvider
import cz.siret.prank.features.implementation.conservation.provider.ConservationProviderException
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable
import java.nio.file.Path
import java.nio.file.Paths

/**
 *
 */
@Slf4j
@CompileStatic
class ConservationLoader implements Parametrized {

    private static ConservationLoader INSTANCE = new ConservationLoader()

    static ConservationLoader getInstance() {
        return INSTANCE
    }

    @Nullable
    private static File doFindConservationFile(List<String> dirs, String proteinFile, String chainId) {
        log.info "Looking for conservation in dirs {}", dirs

        String baseName = Futils.baseName(proteinFile)

        String prefix = baseName + '_' + chainId + '.'  // e.g. "2ed4_A."
        File res = findConservFilePrefixed(dirs, prefix)

        if (res == null) { // try old prefix format without '_'
            prefix = baseName + chainId + '.'           // e.g. "2ed4A."
            res = findConservFilePrefixed(dirs, prefix)
        }
        if (res == null) { // try using only first 4 letters of base name (legacy format)
            prefix = baseName.substring(0, 4) + '_' + chainId + '.'     // e.g. "2ed4_A." for "2ed4A.pdb"
            res = findConservFilePrefixed(dirs, prefix)
        }

        if (res != null) {
            log.info "Conservation file for [baseName:$baseName chain:$chainId] found: [{}]", res?.absolutePath
        } else {
            log.warn "Conservation file for [baseName:$baseName chain:$chainId] not found"
        }

        return res
    }

    private static File findConservFilePrefixed(List<String> dirs, String prefix) {
        return Futils.findFileInDirs(dirs, {File f ->
            f.name.startsWith(prefix) && (Futils.realExtension(f.name) == "hom")
        })
    }

    private static void checkConservationDirsExist(List<String> dirs) {
        for (String dir : dirs) {
            if (!Futils.exists(dir)) {
                throw new PrankException("Directory defined in 'conservation_dirs' param doesn't exist: " + dir)
            }
        }
    }

    private List<String> getConservationLookupDirs(String proteinFile, ProcessedItemContext itemContext) {

        if (!Cutils.empty(params.conservation_dirs)) {
            String datasetDir = itemContext.item.originDataset.dir
            List<String> dirs = params.conservation_dirs.collect {Futils.prependIfNotAbsolute(it, datasetDir) }
            return dirs
        } else {
            String pdbDir = Futils.dir(proteinFile)
            return [pdbDir]
        }
    }

    @Nullable
    File findConservationFile(ProcessedItemContext itemContext, String proteinFile, String chainId) {
        String conservColumn = itemContext.datasetColumnValues.get(Dataset.COLUMN_CONSERVATION_FILES_PATTERN)

        if (conservColumn == null) {
            List<String> conservDirs = getConservationLookupDirs(proteinFile, itemContext)
            log.info "Conservation lookup dirs: " + conservDirs
            checkConservationDirsExist(conservDirs)

            return doFindConservationFile(conservDirs, proteinFile, chainId)
        } else {
            Path parentDir = Paths.get(proteinFile).parent
            String pattern = conservColumn

            return parentDir.resolve(pattern.replaceAll("%chainID%", chainId)).toFile()
        }
    }

    /**
     * Get the cache file path for a conservation score file.
     * When conservation_cache_dir is set: {conservation_cache_dir}/{conservationType}/{baseName}_{chainId}.hom
     * Otherwise: {protein_dir}/.p2rank-cache/conservation/{conservationType}/{baseName}_{chainId}.hom
     */
    static File getCacheFile(String proteinFile, String chainId, String conservationType) {
        String cacheBaseDir
        String conservationCacheDir = Params.inst.conservation_cache_dir
        if (conservationCacheDir != null && !conservationCacheDir.isEmpty()) {
            cacheBaseDir = conservationCacheDir
        } else {
            String normalizedProteinFile = Futils.absPath(proteinFile)
            cacheBaseDir = Futils.dir(normalizedProteinFile) + "/.p2rank-cache/conservation"
        }
        String baseName = Futils.baseName(proteinFile)
        String cachePath = cacheBaseDir + "/" + conservationType + "/" + baseName + "_" + chainId + ".hom"
        return new File(cachePath)
    }

    /**
     * Find conservation file using the standard lookup, then cache, then provider.
     * Returns the file to load, or null if not found and no provider is configured.
     */
    @Nullable
    File findOrFetchConservationFile(ProcessedItemContext itemContext, String proteinFile,
                                      String chainId, @Nullable String sequence,
                                      @Nullable ConservationProvider provider) {
        // 1. Standard file-based lookup (conservation_dirs / conservation_files_pattern)
        File file = findConservationFile(itemContext, proteinFile, chainId)
        if (file != null && file.exists()) {
            return file
        }

        // If no provider configured, return whatever findConservationFile returned (legacy behavior)
        if (provider == null || params.conservation_type == null) {
            return file
        }

        String conservationType = params.conservation_type
        boolean disableCache = params.conservation_disable_cache

        // 2. Check local cache (skip if cache disabled)
        if (!disableCache) {
            File cacheFile = getCacheFile(proteinFile, chainId, conservationType)
            if (cacheFile.exists()) {
                log.info "Found cached conservation file for [{}]: {}",
                    Futils.baseName(proteinFile) + "_" + chainId, cacheFile.absolutePath
                return cacheFile
            }
        }

        // 3. Fetch from provider
        String baseName = Futils.baseName(proteinFile)
        String label = baseName + "_" + chainId
        try {
            String content = provider.fetchScores(sequence, label)
            if (disableCache) {
                File tmpFile = File.createTempFile("conserv_", ".hom")
                tmpFile.deleteOnExit()
                tmpFile.text = content
                log.info "Fetched conservation for [{}] (cache disabled, using temp file)", label
                return tmpFile
            } else {
                File cacheFile = getCacheFile(proteinFile, chainId, conservationType)
                Futils.writeFile(cacheFile.absolutePath, content)
                log.info "Fetched and cached conservation for [{}]: {}", label, cacheFile.absolutePath
                return cacheFile
            }
        } catch (ConservationProviderException e) {
            log.warn "Failed to fetch conservation for [{}]: {}", label, e.message
            return null
        }
    }

}
