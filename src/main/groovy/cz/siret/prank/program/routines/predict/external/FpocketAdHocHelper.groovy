package cz.siret.prank.program.routines.predict.external

import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Shared logic for running fpocket ad-hoc in rescore and eval-rescore commands.
 */
@Slf4j
@CompileStatic
class FpocketAdHocHelper implements Parametrized {

    /**
     * Prepare dataset for ad-hoc fpocket: set prediction method to 'fpocket'.
     */
    static void prepareDataset(Dataset dataset) {
        if (dataset.hasPredictionColumn()) {
            log.info "Dataset $dataset.name already contains prediction column; it will be ignored since ad-hoc fpocket prediction was requested."
        }
        dataset.attributes.put(Dataset.PARAM_PREDICTION_METHOD, 'fpocket')
    }

    /**
     * Run fpocket for a single dataset item and set its prediction file.
     * @return fpocket output directory path
     */
    static String runForItem(Dataset.Item item, String fpocketOutBaseDir, String tmpDir) {
        log.info "Running Fpocket ad-hoc for item [${item.label}]"
        try {
            item.columnValues.put(Dataset.COLUMN_PREDICTION, '') // reset in case fpocket run fails

            String structFileName = Futils.shortName(item.proteinFile)
            String fpocketOutDir = "$fpocketOutBaseDir/${structFileName}_out"
            String fpocketPredFile = "$fpocketOutDir/${predictionFileName(structFileName)}"

            FpocketRunner.runFpocket(item.proteinFile, fpocketOutDir, tmpDir)

            log.info "Fpocket run finished successfully for [$structFileName] - output in [$fpocketOutDir] (${Futils.shortName(fpocketPredFile)})"

            item.setPocketPredictionFile(fpocketPredFile)

            return fpocketOutDir

        } catch (Exception e) {
            throw new PrankException("Fpocket run failed for ${item.label}: ${e.message}", e)
        }
    }

    /**
     * Clean up fpocket output and temp directories after processing.
     */
    static void cleanup(String fpocketOutBaseDir, String fpocketTmpDir, boolean keepOutput) {
        if (!keepOutput) {
            Futils.delete(fpocketOutBaseDir)
        }
        if (Futils.isDirEmpty(fpocketTmpDir)) {
            try {
                Futils.delete(fpocketTmpDir)
            } catch (Exception e) {
                log.warn "Failed to delete tmp fpocket directory [$fpocketTmpDir]: ${e.message}"
            }
        }
    }

    /**
     * Compute fpocket prediction file name from structure file name.
     */
    static String predictionFileName(String structFileName) {
        structFileName = Futils.shortName(structFileName)
        String structBaseName = Futils.baseName(structFileName)
        String structExtension = Futils.realExtension(structFileName) // aaaa.pdb.gz -> pdb
        return "${structBaseName}_out.${structExtension}"
    }

}
