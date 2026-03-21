package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.implementation.table.PropertyTable
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element

/**
 * Atom hybridization feature using one-hot encoding (hyb_sp2, hyb_sp3).
 *
 * Only sp2 and sp3 states are encoded. sp (sp1, linear) is omitted because no standard
 * amino acid atom has sp hybridization (it requires triple bonds or allenes, which do not
 * occur in protein residues). If sp encoding is ever needed (e.g. for ligand atoms with
 * triple bonds), a third column can be added to the CSV and header.
 *
 * Values for standard amino acid atoms are loaded from a CSV resource file.
 * For non-standard/unknown residues, a tiered fallback is applied:
 *   1. CSV lookup (exact RES.ATOM match)
 *   2. Backbone atom name match (N, CA, C, O, OXT, OT1, OT2)
 *   3. Element-based heuristic default
 */
@Slf4j
@CompileStatic
class HybridizationFeature extends AtomFeatureCalculator {

    static final String NAME = 'hybridization'

    static final List<String> HEADER = ['hyb_sp2', 'hyb_sp3'].asImmutable()

    static final String COL_SP2 = 'hyb_sp2'
    static final String COL_SP3 = 'hyb_sp3'

    // one-hot vectors
    static final double[] SP2 = [1d, 0d] as double[]
    static final double[] SP3 = [0d, 1d] as double[]

    static final PropertyTable hybTable = PropertyTable.parseResource("/tables/atom-hybridization.csv")

    /** Backbone atoms have the same hybridization in all residues */
    static final Map<String, double[]> BACKBONE_HYB = [
        'N'  : SP2,
        'CA' : SP3,
        'C'  : SP2,
        'O'  : SP2,
        'OXT': SP2,
        'OT1': SP2,
        'OT2': SP2,
    ].asImmutable() as Map<String, double[]>

    /** Element-based defaults for atoms not found in CSV or backbone */
    static final Map<Element, double[]> ELEMENT_DEFAULTS = [
        (Element.C) : SP3,
        (Element.N) : SP2,   // most protein N atoms participate in resonance
        (Element.O) : SP3,
        (Element.S) : SP3,
        (Element.P) : SP3,
        (Element.Se): SP3,
    ].asImmutable() as Map<Element, double[]>

    @Override
    String getName() {
        return NAME
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext ctx) {
        // Tier 1: CSV lookup by RES.ATOM key
        String key = PdbUtils.getAtomTypeInResidueCode(proteinSurfaceAtom)
        Double sp2Val = hybTable.getValue(key, COL_SP2)
        if (sp2Val != null) {
            return [sp2Val,
                    hybTable.getValue(key, COL_SP3)] as double[]
        }

        // Tier 2: backbone atom name
        String atomName = proteinSurfaceAtom.name
        if (atomName != null) {
            double[] bbHyb = BACKBONE_HYB.get(atomName.trim())
            if (bbHyb != null) {
                return bbHyb
            }
        }

        // Tier 3: element-based heuristic
        double[] elemDefault = ELEMENT_DEFAULTS.get(proteinSurfaceAtom.element)
        return elemDefault != null ? elemDefault : SP3
    }

}
