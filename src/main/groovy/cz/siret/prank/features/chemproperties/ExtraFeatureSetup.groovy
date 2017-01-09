package cz.siret.prank.features.chemproperties

import cz.siret.prank.features.api.FeatureCalculator
import cz.siret.prank.features.api.FeatureRegistry
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class ExtraFeatureSetup {

    List<FeatureCalculator> enabledFeatures = new ArrayList<>()

    List<FeatureCalculator> enabledAtomFeatures = new ArrayList<>()
    List<FeatureCalculator> enabledSasFeatures = new ArrayList<>()

    List<String> jointHeader = new ArrayList<>()


    ExtraFeatureSetup(List<String> enabledFeaturesNames) {

        for (String name : enabledFeaturesNames) {
            FeatureCalculator calculator = FeatureRegistry.featureImplementations.get(name)

            if (calculator!=null) {
                if (calculator.type == FeatureCalculator.Type.ATOM) {
                    enabledAtomFeatures.add(calculator)
                } else {
                    enabledSasFeatures.add(calculator)
                }
            } else {
                throw new IllegalStateException("Feature implementation not found: " + name)
            }

        }

        enabledFeatures = enabledAtomFeatures + enabledSasFeatures

        for (FeatureCalculator calculator : enabledFeatures) {
            jointHeader.addAll(calculator.header)
        }

    }

}
