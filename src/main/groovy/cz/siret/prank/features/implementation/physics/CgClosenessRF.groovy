package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * Closeness centrality on the residue contact graph (unweighted, normalized
 * within each connected component).
 *
 * Closeness centrality in protein contact networks:
 *   Amitai, G. et al. (2004). Network Analysis of Protein Structures Identifies
 *   Functional Residues. J. Mol. Biol. 344(4), 1135-1146.
 *   https://doi.org/10.1016/j.jmb.2004.10.055
 *
 * Polarity-weighted variant in allosteric residue characterization:
 *   Yan, W. et al. (2018). Node-Weighted Amino Acid Network Strategy for
 *   Characterization and Identification of Protein Functional Residues.
 *   J. Chem. Inf. Model. 58(9), 2024-2032.
 *   https://doi.org/10.1021/acs.jcim.8b00146
 *
 * Adaptation: unweighted closeness on a heavy-atom contact graph, normalized
 * within component (CC_i = (n_comp − 1) / Σ d(i,j)) so multi-chain structures
 * with disconnected fragments are not penalized with artificial zeros.
 * Used as a per-residue feature in P2Rank — binding sites tend to be
 * topologically central, well-reachable from the entire structure.
 *
 * Shares the cached ContactGraph with cg_betweenness and cg_degree.
 */
@CompileStatic
class CgClosenessRF extends ResidueFeatureCalculator implements Parametrized {

    static final String NAME = "cg_closeness"

    @Override String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        ContactGraph.getOrCompute(protein, params)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        ContactGraph g = ContactGraph.getOrCompute(context.protein, params)
        return [g.closenessFor(residue)] as double[]
    }
}
