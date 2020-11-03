package cz.siret.prank.features.implementation.external

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

@Slf4j
@CompileStatic
class CsvFileFeature extends AtomFeatureCalculator implements Parametrized {

    private static final String DATA_LOADED_KEY = "CSV_FILE_DATA_LOADED"
    private static final String DATA_KEY = "CSV_FILE_DATA"

    @Override
    String getName() {
        return "csv_file_feature"
    }

    /**
     *
     * Notes:
     * Multi value features must return header.
     * Elements of the header should be alpha-numeric strings without whitespace.
     * Header must have the same length as results of calculateForAtom().
     * calculateForAtom() must return array of the same length for every atom and protein.
     */
    @Override
    List<String> getHeader() {
        // TODO: add implementation so that the method returns static header with sensible sub-feature names, so that either:
        //  (a) header is based on feat_csv_directories
        //  (b) this feature returns result based on feat_csv_header

        return params.feat_csv_header // temporary solution!
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        ensureCsvFileForProteinIsLoaded(protein)
    }

    private void ensureCsvFileForProteinIsLoaded(Protein protein) {
        if (protein.secondaryData.get(DATA_LOADED_KEY) == Boolean.TRUE) {
            return
        }
        loadCsvFileForProtein(protein)
    }

    private void loadCsvFileForProtein(Protein protein) {
        List<String> directories = params.feat_csv_directories
        ExternalAtomFeature feature = new ExternalAtomFeature()
        feature.load(directories, protein.name)
        protein.secondaryData.put(DATA_LOADED_KEY, true)
        protein.secondaryData.put(DATA_KEY, feature)
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        ExternalAtomFeature feature = getFeature(context.protein)
        return feature.getValue(proteinSurfaceAtom)
    }

    private static ExternalAtomFeature getFeature(Protein protein) {
        return (ExternalAtomFeature) protein.secondaryData.get(DATA_KEY)
    }

}
