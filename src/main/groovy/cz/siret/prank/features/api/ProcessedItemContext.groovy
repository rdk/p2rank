package cz.siret.prank.features.api

import cz.siret.prank.domain.Dataset
import groovy.transform.CompileStatic

/**
 *  Context for processing a dataset item
 */
@CompileStatic
class ProcessedItemContext {

    Dataset.Item item

    /**
     * Column values from dataset item
     */
    Map<String, String> datsetColumnValues

    /**
     * Generic store for passing any custom attributes or data
     */
    Map<String, Object> auxData = new HashMap<>()

    ProcessedItemContext(Dataset.Item item, Map<String, String> datsetColumnValues) {
        this.item = item
        this.datsetColumnValues = datsetColumnValues
    }

    Dataset getDataset() {
        item.originDataset
    }
    
}
