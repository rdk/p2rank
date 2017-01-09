package cz.siret.prank.features

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.chemproperties.ChemFeatureExtractor
import cz.siret.prank.geom.Atoms

/**
 * calculates feature vectors for pocket (PRANK) or whole protein (P2RANK)
 */
@CompileStatic
abstract class FeatureExtractor<P extends FeatureVector> {

    Protein protein
    boolean trainingExtractor = false

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

    abstract Atoms getSampledPoints()

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
     * FeatureExtractor extractor = prototype.createInstanceForPocket(pocket)
     * extractor.calcFeatureVector(...)
     */
    abstract FeatureExtractor createPrototypeForProtein(Protein protein)

    /**
     * @see this.createPrototypeForProtein(Protein protein)
     */
    abstract FeatureExtractor createInstanceForPocket(Pocket pocket)

    /**
     * @return abstract factory method to create factory :p
     */
    static FeatureExtractor createFactory() {

        FeatureExtractor res = new ChemFeatureExtractor()
        return res
    }

}
