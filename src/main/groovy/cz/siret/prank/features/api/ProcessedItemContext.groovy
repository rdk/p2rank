package cz.siret.prank.features.api

import cz.siret.prank.domain.Dataset

/**
 *  Context for processing a dataset item
 */
class ProcessedItemContext {

    Dataset.Item item

    /**
     * Column falues from dataset item
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
        item.dataset
    }
    
}
