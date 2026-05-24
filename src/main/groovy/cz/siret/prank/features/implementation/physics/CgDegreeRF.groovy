package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * Local Contact Density — degree of the residue in the contact graph (number
 * of neighboring residues within Params.feat_cgraph_cutoff).
 *
 * Protein structure networks and node degree as a structural descriptor:
 *   Brinda, K.V. & Vishveshwara, S. (2005). A Network Representation of Protein
 *   Structures: Implications for Protein Stability. Biophys. J. 89(6), 4159-4170.
 *   https://doi.org/10.1529/biophysj.105.064485
 *
 * Functional relevance of network connectivity in protein structures:
 *   Amitai, G. et al. (2004). Network Analysis of Protein Structures Identifies
 *   Functional Residues. J. Mol. Biol. 344(4), 1135-1146.
 *   https://doi.org/10.1016/j.jmb.2004.10.055
 *
 * Adaptation: simple degree (number of contacting residues) from the same
 * contact graph used for the centrality features. Brinda & Vishveshwara
 * established the foundational framework for analyzing protein structures as
 * residue interaction networks; Amitai et al. showed that network connectivity
 * properties correlate with functional residues. Here, degree is used as a
 * per-residue packing density feature complementary to P2Rank's existing
 * solvent accessibility descriptors.
 *
 * Shares the cached ContactGraph with cg_betweenness and cg_closeness.
 */
@CompileStatic
class CgDegreeRF extends ResidueFeatureCalculator implements Parametrized {

    static final String NAME = "cg_degree"

    @Override String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        ContactGraph.getOrCompute(protein, params)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        ContactGraph g = ContactGraph.getOrCompute(context.protein, params)
        return [g.degreeFor(residue)] as double[]
    }
}
