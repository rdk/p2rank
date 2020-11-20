package cz.siret.prank.features

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.generic.GenericHeader
import cz.siret.prank.features.generic.GenericVector
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Feature vector of Physico-Chemical Properties (and everything else contained in GenericVector valueVector)
 */
@Slf4j
@CompileStatic
class PrankFeatureVector extends FeatureVector implements Cloneable {

    public GenericVector valueVector

    PrankFeatureVector() {
        this(GenericHeader.EMPTY)
    }

    PrankFeatureVector(GenericHeader header) {
        this.valueVector = new GenericVector(header)
    }

    @Override
    double[] getArray() {
        return valueVector.data
    }

    @Override
    final List<Double> getVector() {       

        return valueVector.toList()
    }

    @Override
    List<String> getHeader() {
        valueVector.header.colNames
    }


    /**
     * todo move to PrankFeatureExtractor
     * @param atom
     * @param extractor
     * @return
     */
    static PrankFeatureVector forAtom(Atom atom, PrankFeatureExtractor extractor) {
        String residueCode = PdbUtils.getCorrectedAtomResidueCode(atom)

        PrankFeatureVector p = new PrankFeatureVector()
        p.valueVector = new GenericVector(extractor.calculatedFeatureVectorHeader)
        p.calculateAtomFeatures(atom, residueCode, extractor)

        return p
    }

    /**
     * todo move to PrankFeatureExtractor
     * 
     * Calculates Atom features (AtomFeatureCalculator) for given atom
     *
     * @param residueCode 3 letter residue code (e.g. "ALA")
     */
    private void calculateAtomFeatures(Atom atom, String residueCode, PrankFeatureExtractor extractor) {

        // Calculate atom features

        AtomFeatureCalculationContext context = new AtomFeatureCalculationContext(extractor.protein, residueCode)

        for (FeatureSetup.Feature feature : extractor.featureSetup.enabledAtomFeatures) {
            double[] values = feature.calculator.calculateForAtom(atom, context)

            feature.checkCorrectValuesLength(values)
            valueVector.setValues(feature.startIndex, values)
        }

    }

    /**
     * @return new instance
     */
    public PrankFeatureVector copy() {
        return new PrankFeatureVector().copyFrom(this)
    }

    /**
     * modifies this instance
     */
    public PrankFeatureVector copyFrom(PrankFeatureVector p) {

        valueVector = p.valueVector.copy()

        return this
    }

    /**
     * modifies this instance
     */
    public PrankFeatureVector multiply(double a) {

        valueVector.multiply(a)

        return this
    }

    /**
     * modifies this instance
     */
    public PrankFeatureVector add(PrankFeatureVector p) {

        valueVector.add(p.valueVector)

        return this
    }

    @Override
    public String toString() {
        return getVector().toListString()
    }

}
