package cz.siret.prank.features

import cz.siret.prank.features.api.FeatureCalculator
import cz.siret.prank.features.api.FeatureRegistry
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static cz.siret.prank.features.FeatureSetup.Calculator.*
import static cz.siret.prank.utils.Cutils.empty
import static cz.siret.prank.utils.Cutils.mapWithIndex
import static cz.siret.prank.utils.Sutils.removeSuffix

/**
 * particular setup of features enabled for given run
 */
@Slf4j
@CompileStatic
class FeatureSetup {

    List<String> enabledFeatureNames
    /**
     * preserves order of features from enabledFeatureNames
     */
    List<Feature> enabledFeatures
    List<Feature> enabledAtomFeatures
    List<Feature> enabledSasFeatures
    List<SubFeature> enabledSubFeatures

    /**
     * Sub-feature names for calculated vector (before filtering)
     */
    List<String> subFeaturesHeader

    // Filtering related

    List<String> fixedFeatureNames

    List<String> featureFilters
    boolean filteringEnabled = false

    /**
     * Sub-feature names for final vector (after filtering)
     */
    List<String> filteredSubFeaturesHeader
    List<SubFeature> filteredSubFeatures

    /**
     *
     * @param enabledFeatureNames  names of enabled feature sets, e.g. "chem", "bfactor"
     * @param filterableFeatureNames names of features for which filters will be applied to, others will be fixed
     * @param featureFilters list of filters applied to sub-features, see Params.feature_filters
     */
    FeatureSetup(List<String> enabledFeatureNames, List<String> filterableFeatureNames, @Nullable List<String> featureFilters) {

        boolean doFiltering = !empty(filterableFeatureNames) && !empty(featureFilters)

        enabledFeatureNames = filterOutEmptyFeatures(enabledFeatureNames)

        if (doFiltering) {
            log.info "filtering features using filters: {}", featureFilters

            this.filteringEnabled = true
            this.featureFilters = featureFilters
            this.filteredSubFeatures = calculateEffectiveSubFeatures(enabledFeatureNames, filterableFeatureNames, featureFilters)
            this.filteredSubFeaturesHeader = new ArrayList<>(filteredSubFeatures*.name.toList())

            List<String> effectiveFeatureNames = filteredSubFeatures*.featureName.unique() // feature names left after filtering

            initEnabledFeatures(effectiveFeatureNames, filterableFeatureNames)

            setSubFeatureOffsets(filteredSubFeatures, enabledFeatures) // set offsets in calculated vector

        } else {
            initEnabledFeatures(enabledFeatureNames, filterableFeatureNames)
        }

        log.info "effectively enabled features: {}", enabledFeatures*.name

    }

    boolean hasNonemptyFixedFeature() {
        !fixedFeatureNames.empty
    }

    List<String> getFixedSubFeatureNames() {
        return enabledSubFeatures.findAll { !it.filterable }*.name.toList()
    }

    List<String> getFilterableSubFeatureNames() {
        return enabledSubFeatures.findAll { it.filterable }*.name.toList()
    }

    private void initEnabledFeatures(List<String> enabledFeatureNames, List<String> filterableFeatureNames) {
        this.enabledFeatureNames = enabledFeatureNames
        this.fixedFeatureNames = enabledFeatureNames - filterableFeatureNames

        this.enabledFeatures = toFeatures(enabledFeatureNames)
        this.enabledAtomFeatures = enabledFeatures.findAll { it.calculator.type == FeatureCalculator.Type.ATOM }.toList()
        this.enabledSasFeatures = enabledFeatures.findAll { it.calculator.type == FeatureCalculator.Type.SAS_POINT }.toList()

        this.enabledSubFeatures = collectSubFeatures(enabledFeatures)
        this.subFeaturesHeader = enabledSubFeatures*.name
    }




    static class SubFeature {
        final String name
        final String featureName
        final String subFeatureName


        /**
         * index of this sub-feature in single feature vector (output of single feature calculation)
         */
        final int featureOffset

        /**
         * index of this sub-feature in full calculated feature vector
         */
        int fullFeatureVectorOffset

        boolean filterable = false
        boolean enabled = true

        SubFeature(String featureName, String subFeatureName, int featureOffset) {
            this.featureName = featureName
            this.subFeatureName = subFeatureName
            this.featureOffset = featureOffset
            this.name = featureName + '.' + subFeatureName
        }
    }

    static class Feature {
        FeatureCalculator calculator

        int length

        /**
         * start index (offset) in calculated feature vector
         */
        int startIndex

        Feature(FeatureCalculator calculator, int startIndex) {
            this.calculator = calculator
            this.length = calculator.header.size()
            this.startIndex = startIndex
        }

        String getName() {
            return calculator.name
        }

        List<String> getHeader() {
            return calculator.header
        }

        void checkCorrectLength(double[] calculatedValues) throws PrankException {
            if (calculatedValues.length != length) {
                throw new PrankException("Feature $name returned value array of incorrect length: ${length}."
                        + "Should be ${length} according to the feature header.")
            }
        }
    }

    static class Calculator {


        private static List<SubFeature> calculateEffectiveSubFeatures(List<String> enabledFeatureNames, List<String> filterableFeatureNames, @Nonnull List<String> featureFilters) {

            log.info "filtering features"

            List<String> fixedFeatureNames = enabledFeatureNames - filterableFeatureNames
            filterableFeatureNames = enabledFeatureNames - fixedFeatureNames

            log.info "enabled features (before filter): {}", enabledFeatureNames
            log.info "fixed features: {}", fixedFeatureNames
            log.info "filterable features: {}", filterableFeatureNames


            List<SubFeature> fixedSubFeatures = collectSubFeatures(toFeatures(fixedFeatureNames))
            List<SubFeature> filterableSubFeatures = collectSubFeatures(toFeatures(filterableFeatureNames))
            filterableSubFeatures.each { it.filterable = true }

            applyFilters(filterableSubFeatures, featureFilters)

            List<SubFeature> filteredSubFeatures = filterableSubFeatures.findAll { it.enabled }.toList()

            List<SubFeature> effectiveSubFeatures = fixedSubFeatures + filteredSubFeatures

            return new ArrayList<>(effectiveSubFeatures)
        }

        private static List<String> filterOutEmptyFeatures(List<String> featureNames) {
            List<String> filtered =  toFeatures(featureNames).findAll { it.length > 0 }*.name

            if (filtered.size() != featureNames.size()) {
                List<String> removed = featureNames - filtered
                log.warn "features were removed because they have no values (empty header): {}", removed
            }

            return filtered
        }

        private static void setSubFeatureOffsets(List<SubFeature> subFeatures, List<Feature> features) {
            Map<String, Feature> featureMap = mapWithIndex(features, { it.name })

            for (SubFeature subFeature : subFeatures) {
                Feature feature = featureMap.get(subFeature.featureName)
                subFeature.fullFeatureVectorOffset = feature.startIndex + subFeature.featureOffset
            }
        }


        private static List<SubFeature> collectSubFeatures(List<Feature> features) {
            List<SubFeature> res = new ArrayList<>(64)
            for (Feature feat : features) {
                List<String> header = feat.header

                for (int i = 0; i != header.size(); i++) {
                    res.add(new SubFeature(feat.name, header[i], i))
                }
            }

            assignFilterableFlags(res, Params.inst.extra_features)

            return res
        }

        private static void assignFilterableFlags(List<SubFeature> subFeatures, List<String> filterableFeatureNames) {
            Set<String> filterableSet = new HashSet<>(filterableFeatureNames)
            for (SubFeature sf : subFeatures) {
                if (filterableSet.contains(sf.featureName)) {
                    sf.filterable = true
                }
            }
        }

        private static List<Feature> toFeatures(List<String> featureNames) {
            List<Feature> res = new ArrayList<>()

            int startIndex = 0
            for (String name : featureNames) {
                FeatureCalculator calculator = FeatureRegistry.featureImplementations.get(name)
                if (calculator == null) {
                    throw new IllegalStateException("Feature implementation not found: " + name)
                }

                res.add(new Feature(calculator, startIndex))

                startIndex += calculator.header.size()
            }
            return res
        }

        private static void applyFilters(List<SubFeature> subFeatures, @Nonnull List<String> featureFilters) {

            // add implicit include-all wildcard if first filter starts with "-"
            if (featureFilters[0].startsWith("-")) {
                featureFilters.add(0, "*")
            }

            subFeatures.each { it.enabled = false } // start with all disabled

            for (String filter : featureFilters) {
                applyFilter(filter, subFeatures)
            }
        }

        /**
         *
         * @param filter see {@link cz.siret.prank.program.params.Params#feature_filters}
         * @param filtered
         * @return
         */
        private static applyFilter(@Nonnull String filter, @Nonnull List<SubFeature> filtered) {
            log.debug "applying feature filter {}", filter // debug


            if (filter == "*") {
                filtered.each { it.enabled = true }
                return
            }

            boolean enable = true
            if (filter.startsWith("-")) {
                enable = false
                filter = filter.substring(1)
            }

            if (filter.endsWith("*")) {
                String prefix = removeSuffix(filter, "*")
                filtered.findAll {it.name.startsWith(prefix) }.each {it.enabled = enable }
            } else {
                filtered.findAll {it.name == filter }.each {it.enabled = enable }
            }
        }

    }

}
