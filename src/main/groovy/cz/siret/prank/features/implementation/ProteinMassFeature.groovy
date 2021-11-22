package cz.siret.prank.features.implementation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.geom.Struct.dist

/**
 * simple geometric feature based on distances of the point to the centers of the mass of prot. atoms, sas points ...
 */
@CompileStatic
class ProteinMassFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = 'pmass'

    @Override
    String getName() { NAME }

    @Override
    List<String> getHeader() {
//        ['protp', 'protn', 'protc', 'sasp']
        ['protp']
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
          protein.proteinAtoms.withKdTreeConditional()
//        protein.accessibleSurface.points.buildKdTree()
    }

    @Override
    double[] calculateForSasPoint(Atom point, SasFeatureCalculationContext ctx) {

        Atom center1 = ctx.protein.proteinAtoms.cutoutSphere(point, params.feat_pmass_radius).centroid ?: point
//        Atom center2 = ctx.neighbourhoodAtoms.centerOfMass ?: point

        double protp = dist(point, center1)
//        double protn = dist(point, center2)
//        double protc = dist(point, ctx.protein.proteinAtoms.kdTree.findNearestNAtoms(point, params.feat_pmass_natoms, false).centerOfMass)
//        double sasp  = dist(point, ctx.protein.accessibleSurface.points.kdTree.findNearestNDifferentAtoms(point, params.feat_pmass_nsasp, false).centerOfMass)

//        return [protp, protn, protc, sasp] as double[]
        return [protp] as double[]
    }

}