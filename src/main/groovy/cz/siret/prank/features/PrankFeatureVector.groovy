package cz.siret.prank.features

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.generic.GenericHeader
import cz.siret.prank.features.generic.GenericVector
import cz.siret.prank.features.tables.PropertyTable
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Feature vector of Physico-Chemical Properties (and everything else contained in GenericVector valueVector)
 */
@Slf4j
@CompileStatic
public class PrankFeatureVector extends FeatureVector implements Cloneable {

    static final PropertyTable aa5FactorsTable   = PropertyTable.parse(Futils.readResource("/tables/aa-5factors.csv"))
    static final PropertyTable aa5PropensitiesTable   = PropertyTable.parse(Futils.readResource("/tables/aa-propensities.csv"))

    static final PropertyTable aaPropertyTable   = aa5FactorsTable.join(aa5PropensitiesTable)
    static final PropertyTable atomPropertyTable = PropertyTable.parse(Futils.readResource("/tables/atomic-properties.csv"))

    public GenericVector valueVector

    PrankFeatureVector() {
        this(GenericHeader.EMPTY)
    }

    PrankFeatureVector(GenericHeader header) {
        this.valueVector = new GenericVector(header)
    }

    @Override
    double[] getArray() {
        return valueVector.@data
    }

    @Override
    final List<Double> getVector() {       

        return valueVector.toList()
    }

    @Override
    List<String> getHeader() {
        valueVector.header.colNames
    }


    public static PrankFeatureVector forAtom(Atom atom, PrankFeatureExtractor extractor) {
        String residueCode = PdbUtils.normAAcode(PdbUtils.getCorrectedAtomResidueCode(atom))

        PrankFeatureVector p = new PrankFeatureVector()
        p.valueVector = new GenericVector(extractor.headerAdditionalFeatures)
        p.setFromResidueAtom(atom, residueCode, extractor)

        return p
    }

    /**
     *
     * @param residueCode 3 letter residue code (e.g. "Ala")
     */
    private void setFromResidueAtom(Atom atom, String residueCode, PrankFeatureExtractor extractor) {

        double ATOM_POW = Params.INSTANCE.@atom_table_feat_pow
        boolean KEEP_SGN = Params.INSTANCE.@atom_table_feat_keep_sgn

        for (String property : extractor.atomTableFeatures) {
            double val = getAtomTableValue(atom, property)

            double pval = val
            if (ATOM_POW != 1d) {
                if (KEEP_SGN) {
                    pval = Math.signum(val) * Math.abs( Math.pow(val, ATOM_POW) )
                } else {
                    pval = Math.pow(val, ATOM_POW)
                }
            }

            //log.info "$val^$ATOM_POW = $pval"

            valueVector.set(property, pval)
        }

        for (String property : extractor.residueTableFeatures) {
            double val = getResidueTableValue(atom, property)

            valueVector.set(property, val)
        }

        // Calculate extra atom features

        AtomFeatureCalculationContext context = new AtomFeatureCalculationContext(extractor.protein, residueCode)
        for (FeatureSetup.Feature feature : extractor.featureSetup.enabledAtomFeatures) {
            double[] values = feature.calculator.calculateForAtom(atom, context)
            valueVector.setValues(feature.startIndex, values)
        }

    }

    private static Double getAtomTableValue(Atom atom, String property) {
        String atomName = PdbUtils.getCorrectedAtomResidueCode(atom) + "." + atom.name
        Double val = atomPropertyTable.getValue(atomName, property)

        //log.info "atom table value ${atomName}.$property = $val"

        return val==null ? 0d : val
    }

    private static Double getResidueTableValue(Atom atom, String property) {
        Double val = aaPropertyTable.getValue(PdbUtils.getCorrectedAtomResidueCode(atom), property)
        return val==null ? 0d : val
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
