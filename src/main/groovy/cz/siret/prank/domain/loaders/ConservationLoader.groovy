package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
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

}
