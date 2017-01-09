package cz.siret.prank.features.api

import cz.siret.prank.features.implementation.BfactorFeature
import cz.siret.prank.features.implementation.ProtrusionFeature
import cz.siret.prank.features.implementation.SurfaceProtrusionFeature
import groovy.transform.CompileStatic

/**
 * Registry of feature implementations
 */
@CompileStatic
class FeatureRegistry {

    private static Map<String, FeatureCalculator> features = new HashMap<>()

    /**
     *
     * @param key unique feature key. Add this key to Params.extra_features to enable this feature.
     * @param featureCalculator
     */
    static void registerFeature(FeatureCalculator featureCalculator) {
        features.put(featureCalculator.name, featureCalculator)
    }

    static Map<String, FeatureCalculator> getFeatureImplementations() {
        return features
    }

    static {

        registerFeature(new ProtrusionFeature())
        registerFeature(new SurfaceProtrusionFeature())
        registerFeature(new BfactorFeature())

    }

}
