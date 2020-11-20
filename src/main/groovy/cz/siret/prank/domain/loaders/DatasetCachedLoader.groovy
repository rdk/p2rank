package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic

@CompileStatic
class DatasetCachedLoader implements Parametrized {

    static Map<String, Dataset> cache = new HashMap<>()

    static Dataset loadDataset(String datasetFile) {

        Dataset res

        if (Params.inst.cache_datasets) {
            res = cache.get(datasetFile)
        }
        if (res==null) {
            res = Dataset.loadFromFile(datasetFile).withCache(Params.inst.cache_datasets)
            if (Params.inst.cache_datasets) {
                cache.put(datasetFile, res)
            }
        }

        return res
    }

}
