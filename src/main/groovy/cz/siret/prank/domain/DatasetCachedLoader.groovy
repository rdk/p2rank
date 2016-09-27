package cz.siret.prank.domain

import groovy.transform.CompileStatic
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params


@CompileStatic
class DatasetCachedLoader implements Parametrized {

    static Map<String, Dataset> cache = new HashMap<>()

    static loadDataset(String datasetFile) {

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
