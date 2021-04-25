package cz.siret.prank.features

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.samplers.SampledPoints
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * calculates feature vectors for pocket (PRANK) or whole protein (P2RANK)
 */
@CompileStatic
abstract class FeatureExtractor<P extends FeatureVector> {

    Protein protein
    boolean forTraining = false

    FeatureExtractor() {}

    FeatureExtractor(Protein protein) {
        // constructor for when its used as prototype for protein
        this.protein = protein
    }

    /**
     * @param samplePoints all sample points for the pocket
     * @return
     */
    abstract P calcFeatureVector(Atom point)

    /**
     * SAS point for given protein (or pocket in pocket mode) for which we calculate feature vectors
     */
    abstract SampledPoints getSampledPoints()

    abstract List<String> getVectorHeader();

    /**
     * This is a mess.
     * Each extractor class represents a factory (global), prototype (connected to protein) and an instance (connected to pocket).
     * The reason was the ability to store precalculated representations without the need to write 3 classes for each extractor type.
     * (TODO: It is about time to refactor this spaghetti monster)
     *
     * so it goes like:
     *
     * FeatureExtractor factory = FeatureExtractor.createFactory()
     * FeatureExtractor prototype = factory.createPrototypeForProtein(protein)
     * prototype.prepareProteinPrototypeForPockets()
     * FeatureExtractor extractor = prototype.createInstanceForPocket(pocket)
     * extractor.calcFeatureVector(...)
     */
    abstract FeatureExtractor createPrototypeForProtein(Protein protein, ProcessedItemContext context)

    /**
     * @see this.createPrototypeForProtein(Protein protein)
     */
    abstract FeatureExtractor createInstanceForPocket(Pocket pocket)

    /**
     * Called after all extraction on the protein is done
     */
    void finalizeProteinPrototype() { }

    /**
     * @return abstract factory method to create factory :p
     */
    static FeatureExtractor createFactory() {

        FeatureExtractor res = new PrankFeatureExtractor()
        return res
    }

    /**
     * Precalculates data common for all pockets.
     * Call before calling createInstanceForPocket() method.
     */
    void prepareProteinPrototypeForPockets() {}
}
