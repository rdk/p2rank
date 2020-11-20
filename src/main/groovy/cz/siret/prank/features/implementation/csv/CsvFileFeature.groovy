package cz.siret.prank.features.implementation.csv

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Loads values from csv files (for each protein separately).
 * Csv files define values for either individual protein atom or residues.
 */
@Slf4j
@CompileStatic
class CsvFileFeature extends AtomFeatureCalculator implements Parametrized {

    private static final String DATA_LOADED_KEY = "CSV_FILE_DATA_LOADED"
    private static final String DATA_KEY = "CSV_FILE_DATA"

    @Override
    String getName() {
        return "csv"
    }

    /**
     * List of enabled csv value columns.
     *
     * Notes:
     * Multi value features must return header.
     * Elements of the header should be alpha-numeric strings without whitespace.
     * Header must have the same length as results of calculateForAtom().
     * calculateForAtom() must return array of the same length for every atom and protein.
     */
    @Override
    List<String> getHeader() {
        return params.feat_csv_columns
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        ensureCsvFileForProteinIsLoaded(protein)
    }

    private void ensureCsvFileForProteinIsLoaded(Protein protein) {
        if (protein.secondaryData.get(DATA_LOADED_KEY) == Boolean.TRUE) {
            return
        }
        loadCsvFilesForProtein(protein)
    }

    private void loadCsvFilesForProtein(Protein protein) {
        List<String> directories = params.feat_csv_directories
        CsvFileFeatureValues feature = new CsvFileFeatureValues(params.feat_csv_ignore_missing)
        feature.load(directories, protein.name, header)
        protein.secondaryData.put(DATA_LOADED_KEY, true)
        protein.secondaryData.put(DATA_KEY, feature)
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        CsvFileFeatureValues feature = getValuesForProtein(context.protein)
        return feature.getValues(proteinSurfaceAtom, header)
    }

    private static CsvFileFeatureValues getValuesForProtein(Protein protein) {
        return (CsvFileFeatureValues) protein.secondaryData.get(DATA_KEY)
    }

}
