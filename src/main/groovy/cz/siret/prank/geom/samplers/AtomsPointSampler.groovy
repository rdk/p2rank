package cz.siret.prank.geom.samplers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

@Deprecated
@CompileStatic
class AtomsPointSampler extends PointSampler {

    AtomsPointSampler(Protein protein) {
        super(protein)
    }

    @Override
    SampledPoints samplePointsForPocket(Pocket pocket) {

        if (pocket.surfaceAtoms.isEmpty())
            return new SampledPoints(new Atoms())

        Atoms realExposedAtoms = protein.exposedAtoms.cutoutShell(pocket.surfaceAtoms, 1.5d)

        return new SampledPoints(realExposedAtoms)
    }

}
