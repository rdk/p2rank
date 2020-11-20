package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.StatSample
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 *
 */
@CompileStatic
class ConservCloudSF extends SasFeatureCalculator implements Parametrized {

    final List<String> HEADER = ['exposed']

    @Override
    String getName() {
        return 'conserv_cloud'
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.ensureConservationLoaded(context)
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        double radius = params.conserv_cloud_radius 
        double exp = params.conservation_exponent


        Atoms exp_atoms = context.protein.exposedAtoms.cutoutSphere(sasPoint, radius)
        List<Residue> exp_residues = context.protein.residues.getDistinctForAtoms(exp_atoms)

        List<Double> scores = exp_residues.collect {
            double score = ConservRF.getScoreForResidue(it, context.protein)
            score = Math.pow(score, exp)
            score
        }.asList()
        double average = new StatSample(scores).mean

        return [average] as double[]
    }

}
