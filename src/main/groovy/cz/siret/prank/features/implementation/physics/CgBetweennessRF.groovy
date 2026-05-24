package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * Betweenness centrality on the residue contact graph (unweighted, computed
 * by Brandes' algorithm).
 *
 * Betweenness centrality in protein residue networks:
 *   Amitai, G. et al. (2004). Network Analysis of Protein Structures Identifies
 *   Functional Residues. J. Mol. Biol. 344(4), 1135-1146.
 *   https://doi.org/10.1016/j.jmb.2004.10.055
 *
 *   del Sol, A. et al. (2006). Residues crucial for maintaining short paths in
 *   network communication mediate signaling in proteins. Mol. Syst. Biol. 2,
 *   2006.0019. https://doi.org/10.1038/msb4100063
 *
 * Adaptation: computed on an unweighted residue contact graph (heavy-atom
 * cutoff Params.feat_cgraph_cutoff) and used as a per-residue feature in
 * P2Rank. Residues with high betweenness act as communication hubs and
 * frequently coincide with functional sites.
 *
 * Shares the cached ContactGraph with cg_closeness and cg_degree.
 */
@CompileStatic
class CgBetweennessRF extends ResidueFeatureCalculator implements Parametrized {

    static final String NAME = "cg_betweenness"

    @Override String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        ContactGraph.getOrCompute(protein, params)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        ContactGraph g = ContactGraph.getOrCompute(context.protein, params)
        return [g.betweennessFor(residue)] as double[]
    }
}
