package cz.siret.prank.program.routines.traineval

import cz.siret.prank.program.ml.Model
import groovy.transform.CompileStatic

/**
 * Manages pre-trained models for different seeds.
 * 
 * For now just holds models in memory.
 */
@CompileStatic
class ModelCache {

    private Map<String, Model> cache = new HashMap<>()

    boolean contains(String key) {
        return cache.containsKey(key)
    }

    Model get(String key) {
        return cache.get(key)
    }

    void put(String key, Model model) {
        cache.put(key, model)
    }

    static ModelCache create() {
        return new ModelCache()
    }
    
}
