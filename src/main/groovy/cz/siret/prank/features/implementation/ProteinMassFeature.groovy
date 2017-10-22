package cz.siret.prank.features.implementation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.geom.Struct.dist

/**
 * simple geometric feature based on distances of the point to the centrs of the mass of prot. atoms, sas points ...
 */
class ProteinMassFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = 'pmass'

    @Override
    String getName() { NAME }

    @Override
    List<String> getHeader() {
        ['protp', 'protn', 'protc', 'sasp']
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.proteinAtoms.buildKdTree()
        protein.accessibleSurface.points.buildKdTree()
    }

    @Override
    double[] calculateForSasPoint(Atom point, SasFeatureCalculationContext ctx) {

        Atoms nearest = ctx.protein.exposedAtoms.kdTree.findNearestNAtoms(point, 9, true)

        double protp = dist(point, ctx.extractor.deepLayer.cutoffAroundAtom(point, params.protrusion_radius).centerOfMass)
        double protn = dist(point, ctx.neighbourhoodAtoms.centerOfMass)
        double protc = dist(point, ctx.protein.proteinAtoms.kdTree.findNearestNAtoms(point, 70, false).centerOfMass)
        double sasp  = dist(point, ctx.protein.accessibleSurface.points.kdTree.findNearestNDifferentAtoms(point, 40, false).centerOfMass)

        return [protp, protn, protc, sasp] as double[]
    }

}