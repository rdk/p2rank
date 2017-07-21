package cz.siret.prank.features.api

/**
 *  Context for processing a dataset item
 */
class ProcessedItemContext {

    Map<String, String> datsetColumnValues

    ProcessedItemContext(Map<String, String> datsetColumnValues) {
        this.datsetColumnValues = datsetColumnValues
    }
    
}
