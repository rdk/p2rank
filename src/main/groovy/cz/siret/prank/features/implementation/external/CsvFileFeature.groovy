package cz.siret.prank.features.implementation.external;

import cz.siret.prank.domain.Protein;
import cz.siret.prank.features.api.AtomFeatureCalculationContext;
import cz.siret.prank.features.api.AtomFeatureCalculator;
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.params.Parametrized;
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j;
import org.biojava.nbio.structure.Atom

@Slf4j
@CompileStatic
class CsvFileFeature extends AtomFeatureCalculator implements Parametrized {

    private static final String DATA_LOADED_KEY = "CSV_FILE_DATA_LOADED";

    private static final String DATA_KEY = "CSV_FILE_DATA";

    @Override
    public String getName() {
        return "csv_file_feature";
    }

    @Override
    public void preProcessProtein(
            Protein protein,
            ProcessedItemContext context) {
        super.preProcessProtein(protein, context);
        ensureCsvFileForProteinIsLoaded(protein);
    }

    private void ensureCsvFileForProteinIsLoaded(Protein protein) {
        if (protein.getSecondaryData().get(DATA_LOADED_KEY) == Boolean.TRUE) {
            return;
        }
        loadCsvFileForProtein(protein);
    }

    private void loadCsvFileForProtein(Protein protein) {
        List<String> directories = getParams().feat_csv_directories;
        ExternalAtomFeature feature = new ExternalAtomFeature();
        feature.load(directories, protein.name);
        Map<String, Object> secondaryData = protein.getSecondaryData();
        secondaryData.put(DATA_LOADED_KEY, true);
        secondaryData.put(DATA_KEY, feature);
    }

    @Override
    public double[] calculateForAtom(
            Atom proteinSurfaceAtom,
            AtomFeatureCalculationContext context) {
        ExternalAtomFeature feature = getFeature(context.protein);
        return feature.getValue(proteinSurfaceAtom);
    }


    private static ExternalAtomFeature getFeature(Protein protein) {
        return (ExternalAtomFeature) protein.getSecondaryData().get(DATA_KEY);
    }

}
