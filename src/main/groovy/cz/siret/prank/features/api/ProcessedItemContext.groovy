package cz.siret.prank.features.api

/**
 *  Context for processing a dataset item
 */
class ProcessedItemContext {

    /**
     * Column falues from dataset item
     */
    Map<String, String> datsetColumnValues

    /**
     * Generic store for passing any custom attributes or data
     */
    Map<String, Object> auxData = new HashMap<>()

    ProcessedItemContext(Map<String, String> datsetColumnValues) {
        this.datsetColumnValues = datsetColumnValues
    }
    
}
