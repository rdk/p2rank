package cz.siret.prank.geom.samplers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.pockets.FPocketLoader
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic

@CompileStatic
class SurfacePointSampler extends PointSampler implements Parametrized {

    public final double VAN_DER_WAALS_COMPENSATION = params.surface_additional_cutoff

    SurfacePointSampler(Protein protein) {
        super(protein)
     }

    @Override
    SampledPoints samplePointsForPocket(Pocket pocket) {

        String cacheKey = "sampled-points"
        if (forTraining)
            cacheKey += "-train"

        if (pocket.cache.containsKey(cacheKey)) {
            return (SampledPoints) pocket.cache.get(cacheKey)
        }

        SampledPoints protSurf = SampledPoints.fromProtein(protein, forTraining, params)


        SampledPoints res
        if (Params.inst.strict_inner_points && (pocket instanceof FPocketLoader.FPocketPocket)) {
            FPocketLoader.FPocketPocket fpocket = (FPocketLoader.FPocketPocket) pocket
            res = protSurf.cutoutShell(fpocket.vornoiCenters, 6) // 6 is max radius of fpocket alpha sphere
        } else {

            res = protSurf.cutoutShell(pocket.surfaceAtoms, protein.accessibleSurface.solventRadius + VAN_DER_WAALS_COMPENSATION)
        }

        pocket.cache.put(cacheKey, res)

        return res
    }

}
