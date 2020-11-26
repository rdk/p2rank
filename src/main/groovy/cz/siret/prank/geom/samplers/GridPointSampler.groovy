package cz.siret.prank.geom.samplers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Box
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class GridPointSampler extends PointSampler implements Parametrized {

    private static double BOX_MARGIN = 3 // A

    private double MIN_DISTFROM_PROTEIN = params.point_min_distfrom_protein
    private double MAX_DISTFROM_POCKET = params.point_max_distfrom_pocket

    private double CELL_EDGE = params.grid_cell_edge

    GridPointSampler(Protein protein) {
        super(protein)
    }

    /**
     * get grid inner points from pocket
     */
    @Override
    SampledPoints samplePointsForPocket(Pocket pocket) {

        Atoms res = new Atoms()
        Box box = Box.aroundAtoms(pocket.surfaceAtoms).withMargin(BOX_MARGIN)

        protein.proteinAtoms.withKdTreeConditional()

        Atoms surroundingProteinAtoms = protein.proteinAtoms.cutoutBox(box.withMargin(MIN_DISTFROM_PROTEIN))
        Atoms realSurfaceAtoms = surroundingProteinAtoms.cutoutShell(pocket.surfaceAtoms, 1)

        if (pocket.surfaceAtoms.count != realSurfaceAtoms.count) {
            log.warn "received pocket surface atoms != real surface atoms ($pocket.surfaceAtoms.count != $realSurfaceAtoms.count)"
        }

        surroundingProteinAtoms.withKdTreeConditional()
        double sqrMinProteinDist = MIN_DISTFROM_PROTEIN * MIN_DISTFROM_PROTEIN
        realSurfaceAtoms.withKdTreeConditional()
        double sqrMaxPocketDist = MAX_DISTFROM_POCKET * MAX_DISTFROM_POCKET


        GridGenerator grid = new GridGenerator(box, CELL_EDGE)

        for (Point p in grid) {
            //if (realSurfaceAtoms.areWithinDistance(a, MAX_DISTFROM_POCKET)) {
            //if (surroundingProteinAtoms.areDistantFromAtomAtLeast(a, MIN_DISTFROM_PROTEIN)

            if (realSurfaceAtoms.sqrDist(p) <= sqrMaxPocketDist ) { // XXX: null nearest = INF.
                if (surroundingProteinAtoms.sqrDist(p) >= sqrMinProteinDist ) { // XXX: null nearest = INF.
                    res.add(p.copy())
                }
            }

        }

        log.debug "Sampled $res.count out of $grid.count grid points"

        return new SampledPoints(res)
    }

}
