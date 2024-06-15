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
    Map<String, String> datasetColumnValues


    ProcessedItemContext(Dataset.Item item, Map<String, String> datasetColumnValues) {
        this.item = item
        this.datasetColumnValues = datasetColumnValues
    }

    Dataset getDataset() {
        item.originDataset
    }
    
}
