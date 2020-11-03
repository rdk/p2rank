package cz.siret.prank.features

import cz.siret.prank.features.api.FeatureCalculator
import cz.siret.prank.features.api.FeatureRegistry
import groovy.transform.CompileStatic

/**
 * particular setup of features enabled for given run
 */
@CompileStatic
class FeatureSetup {

    List<String> enabledFeatureNames
    /**
     * preserves order of features from enabledFeatureNames
     */
    List<Feature> enabledFeatures = new ArrayList<>()
    List<Feature> enabledAtomFeatures = new ArrayList<>()
    List<Feature> enabledSasFeatures = new ArrayList<>()

    List<String> jointHeader = new ArrayList<>()


    FeatureSetup(List<String> enabledFeatureNames) {
        this.enabledFeatureNames = enabledFeatureNames

        for (String name : enabledFeatureNames) {
            FeatureCalculator calculator = FeatureRegistry.featureImplementations.get(name)

            if (calculator!=null) {
                Feature entry = new Feature(calculator)
                if (calculator.type == FeatureCalculator.Type.ATOM) {
                    enabledAtomFeatures.add(entry)
                } else {
                    enabledSasFeatures.add(entry)
                }
                enabledFeatures.add(entry)
            } else {
                throw new IllegalStateException("Feature implementation not found: " + name)
            }
        }

        int start = 0
        for (Feature feat : enabledFeatures) {
            List<String> header = feat.calculator.header
            
            jointHeader.addAll(header.collect { feat.name + '.' + it  }) // prefix with "feat_name."

            feat.startIndex = start
            feat.length = header.size()
            start += feat.length
        }
    }

    static class Feature {
        FeatureCalculator calculator
        /** start index in feature vector */
        int startIndex
        int length

        Feature(FeatureCalculator calculator) {
            this.calculator = calculator
        }

        String getName() {
            return calculator.name
        }
    }

}
