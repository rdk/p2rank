package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

@CompileStatic
class ConservationCloudFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "conservationcloud"

    @Override
    String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        protein.ensureConservationLoaded(itemContext)
    }


    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        ConservationScore score = context.protein.conservationScore
        if (score == null) {
            return [0.0] as double[]
        }

        Atoms surroundingAtoms = context.extractor.deepLayer.cutoutSphere(sasPoint, params.protrusion_radius)
        def groups = surroundingAtoms.getDistinctGroups()

        double value = 0
        if (!groups.empty) {
            value = groups.stream().mapToDouble({ Group group ->
                score.getScoreForResidue(group.getResidueNumber())
            }).average().getAsDouble();
        }
        if (value==Double.NaN) value = 0d

        return [value] as double[]
    }

}
