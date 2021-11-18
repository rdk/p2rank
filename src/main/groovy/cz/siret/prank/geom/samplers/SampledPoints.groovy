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

 //===========================================================================================================//

    static SampledPoints of(Atoms sampledPoints) {
        return new SampledPoints(sampledPoints)
    }

    private static Atoms sampleGridAround(Atoms atoms, Params params) {
        GridGenerator.sampleGridPointsAroundAtoms(atoms, params.grid_cell_edge, params.grid_cutoff_radius)
    }

    static SampledPoints fromProtein(Protein protein, boolean forTraining, Params params) {
        switch (params.point_sampling_strategy) {
            case "atoms":   return of(protein.proteinAtoms)
            case "grid" :   return of(sampleGridAround(protein.proteinAtoms, params))
            case "surface": return fromProteinSurface(protein, forTraining)
            default:        return fromProteinSurface(protein, forTraining)
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
