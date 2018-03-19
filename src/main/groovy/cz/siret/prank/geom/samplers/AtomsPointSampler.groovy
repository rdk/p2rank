package cz.siret.prank.geom.samplers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms

class AtomsPointSampler extends PointSampler {

    AtomsPointSampler(Protein protein) {
        super(protein)
    }

    @Override
    Atoms samplePointsForPocket(Pocket pocket) {

        if (pocket.surfaceAtoms.isEmpty())
            return new Atoms()

        Atoms realExposedAtoms = protein.exposedAtoms.cutoutShell(pocket.surfaceAtoms, 1.5d)

        return realExposedAtoms
    }

}
