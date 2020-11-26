package cz.siret.prank.geom.samplers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Box
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl

@Deprecated
@Slf4j
@CompileStatic
class RandomPointSampler extends  PointSampler implements Parametrized {

    private double MIN_DISTFROM_PROTEIN = params.point_min_distfrom_protein
    private double MAX_DISTFROM_POCKET = params.point_max_distfrom_pocket

    private Random rand = new Random()

    RandomPointSampler(Protein protein) {
        super(protein)
    }

    Atom randomPoint(Box box) {
        Atom a = new AtomImpl()
            a.x = randomFromRange(box.min.x, box.max.x)
            a.y = randomFromRange(box.min.y, box.max.y)
            a.z = randomFromRange(box.min.z, box.max.z)
        return a
    }

    /**
     * sample randomFromRange inner points from pocket
     */
    SampledPoints samplePointsForPocket(Pocket pocket) {

        int count = pocket.surfaceAtoms.count * params.sampling_multiplier

        assert count>0

        Atoms res = new Atoms()
        Box box = Box.aroundAtoms(pocket.surfaceAtoms)

        //log.debug "BOX " + box

        Atoms surroundingProteinAtoms = protein.proteinAtoms.cutoutBox(box.withMargin(MIN_DISTFROM_PROTEIN))
        Atoms realSurfaceAtoms = protein.proteinAtoms.cutoutShell(pocket.surfaceAtoms, 1)

        int SAMPLING_TRIAL_LIMIT = count*400

        int i = 1
        while (res.count < count && i<=SAMPLING_TRIAL_LIMIT) {

            Atom ra = randomPoint(box)

            if (surroundingProteinAtoms.areDistantFromAtomAtLeast(ra, MIN_DISTFROM_PROTEIN)
                    && realSurfaceAtoms.areWithinDistance(ra, MAX_DISTFROM_POCKET)) {
                res.add(ra)
            }
            i++
            //log.debug "sample ${ra.coords.toList().toListString()} dist = $dist"
        }

        log.debug "Sampled $res.count/$count points from $i trials"

        return new SampledPoints(res)
    }

    double randomFromRange(double min, double max) {
        return min + rand.nextDouble() * (max-min)
    }

}
