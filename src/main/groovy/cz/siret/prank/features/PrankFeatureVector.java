package cz.siret.prank.features;

import cz.siret.prank.features.api.AtomFeatureCalculationContext;
import cz.siret.prank.features.generic.GenericHeader;
import cz.siret.prank.features.generic.GenericVector;
import cz.siret.prank.utils.PdbUtils;
import org.biojava.nbio.structure.Atom;

import java.util.List;

/**
 * Feature vector of Physico-Chemical Properties (and everything else contained in GenericVector valueVector)
 */
public class PrankFeatureVector extends FeatureVector implements Cloneable {

    public GenericVector valueVector;

    public PrankFeatureVector() {
        this(GenericHeader.EMPTY);
    }

    public PrankFeatureVector(GenericHeader header) {
        this.valueVector = new GenericVector(header);
    }

    @Override
    public double[] getArray() {
        return valueVector.getData();
    }

    @Override
    public final List<Double> getVector() {
        return valueVector.toList();
    }

    @Override
    public List<String> getHeader() {
        return valueVector.getHeader().getColNames();
    }

    /**
     * todo move to PrankFeatureExtractor
     *
     * @param atom
     * @param extractor
     * @return
     */
    public static PrankFeatureVector forAtom(Atom atom, PrankFeatureExtractor extractor) {
        String residueCode = PdbUtils.getCorrectedAtomResidueCode(atom);

        PrankFeatureVector p = new PrankFeatureVector();
        p.valueVector = new GenericVector(extractor.getCalculatedFeatureVectorHeader());
        p.calculateAtomFeatures(atom, residueCode, extractor);

        return p;
    }

    /**
     * todo move to PrankFeatureExtractor
     * <p>
     * Calculates Atom features (AtomFeatureCalculator) for given atom
     *
     * @param residueCode 3 letter residue code (e.g. "ALA")
     */
    private void calculateAtomFeatures(Atom atom, String residueCode, PrankFeatureExtractor extractor) {

        // Calculate atom features

        AtomFeatureCalculationContext context = new AtomFeatureCalculationContext(extractor.getProtein(), residueCode);

        for (FeatureSetup.Feature feature : extractor.getFeatureSetup().getEnabledAtomFeatures()) {
            double[] values = feature.getCalculator().calculateForAtom(atom, context);

            feature.checkCorrectLength(values);
            valueVector.setValues(feature.getStartIndex(), values);
        }
    }

    /**
     * @return new instance
     */
    public PrankFeatureVector copy() {
        return new PrankFeatureVector().copyFrom(this);
    }

    /**
     * modifies this instance
     */
    public PrankFeatureVector copyFrom(PrankFeatureVector p) {

        valueVector = p.valueVector.copy();

        return this;
    }

    /**
     * modifies this instance
     */
    public PrankFeatureVector multiply(double a) {

        valueVector.multiply(a);

        return this;
    }

    /**
     * modifies this instance
     */
    public PrankFeatureVector add(PrankFeatureVector p) {

        valueVector.add(p.valueVector);

        return this;
    }

}
