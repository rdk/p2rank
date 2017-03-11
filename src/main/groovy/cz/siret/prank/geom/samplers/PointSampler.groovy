package cz.siret.prank.geom.samplers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic

@CompileStatic
abstract class PointSampler {

    Protein protein
    boolean train = false

    PointSampler(Protein protein) {
        this.protein = protein
    }

    abstract Atoms samplePointsForPocket(Pocket pocket)

    static PointSampler create(Protein protein, boolean train) {

        Class clazz = Class.forName(PointSampler.class.package.name + "." + Params.inst.point_sampler)
        PointSampler res = (PointSampler) clazz.getConstructor(Protein.class).newInstance(protein)

        res.train = true

        return res
    }

}
