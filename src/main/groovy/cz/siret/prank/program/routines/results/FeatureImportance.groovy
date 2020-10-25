package cz.siret.prank.program.routines.results

/**
 * Named feature importance
 */
class FeatureImportance {
    String name
    double importance

    FeatureImportance(String name, double importance) {
        this.name = name
        this.importance = importance
    }
}
