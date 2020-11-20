package cz.siret.prank.program.routines.results

import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic

/**
 * List of feature importances with associated feature names
 */
@CompileStatic
class FeatureImportances {

    List<NamedImportance> items

    FeatureImportances(List<NamedImportance> items) {
        this.items = items
    }

    /**
     * @return copy sorted descending by importance
     */
    FeatureImportances sorted() {
        List<NamedImportance> newItems = items.toSorted { -it.importance }
        return new FeatureImportances(newItems)
    }

    String toCsv() {
        items.collect { it.name + ", " + fmt_fi(it.importance) }.join("\n") + "\n"
    }

    List<String> getNames() {
        return items*.name
    }

    List<Double> getValues() {
        return items*.importance as List<Double>
    }

    static String fmt_fi(Object x) {
        if (x==null) return ""
        sprintf "%8.6f", x
    }

    static FeatureImportances from(List<Double> importanceValues) {
        if (importanceValues==null) return null

        List<String> names = FeatureExtractor.createFactory().vectorHeader
        List<NamedImportance> namedImportances = new ArrayList<>()

        if (importanceValues.size() != names.size()) {
            throw new PrankException("Feature importance vector has different dimensions than feature vector header")
        }

        for (int i=0; i!=names.size(); ++i) {
            namedImportances.add new NamedImportance( names[i] , importanceValues[i])
        }

        return new FeatureImportances(namedImportances)
    }

    static class NamedImportance {
        String name
        double importance

        NamedImportance(String name, double importance) {
            this.name = name
            this.importance = importance
        }
    }

}
