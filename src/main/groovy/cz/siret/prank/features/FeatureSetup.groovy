package cz.siret.prank.features

import cz.siret.prank.features.api.FeatureCalculator
import cz.siret.prank.features.api.FeatureRegistry
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static cz.siret.prank.utils.Cutils.empty
import static cz.siret.prank.utils.Sutils.partBefore
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

    /**
     * Sub-feature names for calculated vector (before filtering)
     */
    List<String> subFeaturesHeader

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
     * @param featureFilters list of filters applied to sub-features, see Params.feature_filters
     */
    FeatureSetup(List<String> enabledFeatureNames, @Nullable List<String> featureFilters) {
        this.enabledFeatureNames = enabledFeatureNames
        this.featureFilters = featureFilters

        initEnabledFeatures(enabledFeatureNames)

        if (!empty(featureFilters)) {
            filteringEnabled = true

            log.info "filtering features"
            log.info "enabled features (before filter): {}", enabledFeatureNames

            filteredSubFeatures = filterSubFeatures(subFeaturesHeader, featureFilters)
            filteredSubFeaturesHeader = filteredSubFeatures*.name
            enabledFeatureNames = collectFeatureNames(filteredSubFeaturesHeader)

            // here we want to disable features that were filtered out completely
            // init again so we don't calculate features needlessly
            initEnabledFeatures(enabledFeatureNames)

            filteredSubFeatures = filterSubFeatures(subFeaturesHeader, featureFilters) // finally apply filters again so we get proper oldIdx in case some features were filtered out
            filteredSubFeaturesHeader = filteredSubFeatures*.name

        } else {
            enabledFeatureNames = collectFeatureNames(subFeaturesHeader)
            initEnabledFeatures(enabledFeatureNames)                     // re-initialize to throw away features that have zero length
        }

        log.info "enabledFeatures: {}", enabledFeatures*.name
    }


    private void initEnabledFeatures(List<String> enabledFeatureNames) {

        enabledFeatures = new ArrayList<>()
        enabledAtomFeatures = new ArrayList<>()
        enabledSasFeatures = new ArrayList<>()

        for (String name : enabledFeatureNames) {
            FeatureCalculator calculator = FeatureRegistry.featureImplementations.get(name)

            if (calculator!=null) {
                Feature entry = new Feature(calculator)
                if (calculator.type == FeatureCalculator.Type.ATOM) {
                    enabledAtomFeatures.add(entry)
                } else if (calculator.type == FeatureCalculator.Type.SAS_POINT) {
                    enabledSasFeatures.add(entry)
                } else {
                    throw new IllegalStateException("Invalid feature: $name. Only ATOM and SAS_POINT features ca be used directly.")
                }
                enabledFeatures.add(entry)
            } else {
                throw new IllegalStateException("Feature implementation not found: " + name)
            }
        }

        subFeaturesHeader = new ArrayList<>(64)
        int start = 0
        for (Feature feat : enabledFeatures) {
            List<String> header = feat.calculator.header

            subFeaturesHeader.addAll header.collect { feat.name + '.' + it  } // prefix with "feature_name."

            feat.startIndex = start
            start += feat.length
        }
    }

    private List<String> collectFeatureNames(List<String> subFeaturesHeader) {
        subFeaturesHeader.collect { partBefore(it, ".") }.unique()
    }

    private List<SubFeature> filterSubFeatures(List<String> subFeaturesHeader, @Nonnull List<String> featureFilters) {

        // add implicit include-all wildcard if first filter starts with "-"
        if (featureFilters[0].startsWith("-")) {
            featureFilters.add(0, "*")
        }

        List<SubFeature> subFeatures = subFeaturesHeader.withIndex().collect { name, idx ->
            new SubFeature(name as String, false, idx as int)
        }

        for (String filter : featureFilters) {
            applyFilter(filter, subFeatures)
        }

        List<SubFeature> filtered = subFeatures.findAll { it.enabled }.toList()

        return filtered
    }

    /**
     *
     * @param filter see {@link cz.siret.prank.program.params.Params#feature_filters}
     * @param filtered
     * @return
     */
    private applyFilter(@Nonnull String filter, @Nonnull List<SubFeature> filtered) {
        log.debug "applying feature filter {}", filter // debug

        if (filter == "*") {
            filtered.each { it.enabled = true }
        }

        boolean enable = true
        if (filter.startsWith("-")) {
            enable = false
            filter = filter.substring(1)
        }

        if (filter.endsWith("*")) {
            filter = removeSuffix(filter, "*")
            filtered.findAll {it.name.startsWith(filter) }.each {it.enabled = enable }
        } else {
            filtered.findAll {it.name == filter }.each {it.enabled = enable }
        }
    }


    static class SubFeature {
        String name
        boolean enabled = false

        /**
         * index of sub-feature in calculated vector (= index in subFeaturesHeader)
         */
        int oldIdx

        SubFeature(String name, boolean enabled, int oldIdx) {
            this.name = name
            this.enabled = enabled
            this.oldIdx = oldIdx
        }
    }

    static class Feature {
        FeatureCalculator calculator
        /**
         * start index in calculated feature vector
         */
        int startIndex
        int length

        Feature(FeatureCalculator calculator) {
            this.calculator = calculator
            this.length = calculator.header.size()
        }

        String getName() {
            return calculator.name
        }

        void checkCorrectLength(double[] calculatedValues) throws PrankException {
            if (calculatedValues.length != length) {
                throw new PrankException("Feature $name returned value array of incorrect length: ${length}."
                        + "Should be ${length} according to the feature header.")
            }
        }
    }

}
