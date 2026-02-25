package cz.siret.prank.features.implementation.conservation.provider

import groovy.transform.CompileStatic

/**
 * Interface for external conservation score providers.
 * Implementations fetch raw conservation score file content (e.g., .hom TSV)
 * from an external source given a protein chain sequence.
 */
@CompileStatic
interface ConservationProvider {

    /**
     * Fetch conservation scores for a given sequence.
     *
     * @param sequence amino acid sequence string (one-letter codes)
     * @param label identifier for the sequence, used in FASTA header (e.g., "1fbl_A")
     * @return raw score file content (e.g., .hom TSV text)
     * @throws ConservationProviderException on failure
     */
    String fetchScores(String sequence, String label) throws ConservationProviderException

    /**
     * Check if the provider is available and healthy.
     * @throws ConservationProviderException if the provider is not reachable
     */
    void checkHealth() throws ConservationProviderException

}
