package cz.siret.prank.geom.samplers

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class SampledPoints {

    Atoms sampledPositives
    Atoms sampledNegatives
    boolean posNegDifferent

    SampledPoints(Atoms sampledPositives, Atoms sampledNegatives) {
        this.sampledPositives = sampledPositives
        this.sampledNegatives = sampledNegatives
        this.posNegDifferent = sampledPositives !== sampledNegatives
    }

    SampledPoints(Atoms sampledPoints) {
        this.sampledPositives = sampledPoints
        this.sampledNegatives = sampledPoints
        this.posNegDifferent = false
    }




    static SampledPoints fromProtein(Protein protein, boolean forTraining) {
        if (Params.inst.point_sampling_strategy == "atoms") {
            return new SampledPoints(protein.proteinAtoms)
        } else {
            return fromProteinSurface(protein, forTraining)
        }
    }



    static SampledPoints fromProteinSurface(Protein protein, boolean forTraining) {
        if (forTraining) {
            return new SampledPoints(protein.trainSurface.points, protein.trainNegativesSurface.points)
        } else {
            return new SampledPoints(protein.accessibleSurface.points)
        }
    }


    Atoms getPoints() {
        return sampledPositives
    }

    SampledPoints cutoutShell(Atoms aroundAtoms, double dist) {
        if (aroundAtoms==null || aroundAtoms.isEmpty()) {
            return new SampledPoints(new Atoms())
        }

        if (posNegDifferent) {
            return new SampledPoints(sampledPositives.cutoutShell(aroundAtoms, dist), sampledNegatives.cutoutShell(aroundAtoms, dist))
        } else {
            return new SampledPoints(sampledPositives.cutoutShell(aroundAtoms, dist))
        }
    }
    
}
