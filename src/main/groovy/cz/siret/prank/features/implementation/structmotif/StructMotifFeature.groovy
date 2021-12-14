package cz.siret.prank.features.implementation.structmotif

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import javax.vecmath.Point3d

import static cz.siret.prank.utils.Cutils.head

/**
 * Structural motifs 
 */
@Slf4j
@CompileStatic
class StructMotifFeature extends SasFeatureCalculator {

    public static double MAX_RADIUS = 6d

    @Override
    String getName() {
        return 'stmotif'
    }

    @Override
    List<String> getHeader() {
        return params.feat_stmotif_motifs
    }

//===========================================================================================================//

    private static final String CACHE_KEY_COMPILED = 'stmotif.compiled';
    private static final String CACHE_KEY_NEIHBORS = 'stmotif.neighbors';

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.secondaryData.computeIfAbsent(CACHE_KEY_COMPILED, {
            List<ResidueMotif> motifs = parseMotifs(params.feat_stmotif_motifs)
            log.info "stmotif feature compiled motif codes: {}", motifs*.compiledCode
            motifs
        })
        protein.secondaryData.computeIfAbsent(CACHE_KEY_NEIHBORS, { new HashMap<>() })
    }

    @Override
    void postProcessProtein(Protein protein) {
        protein.secondaryData.put(CACHE_KEY_COMPILED, null) // cleanup
    }

//===========================================================================================================//

    List<ResidueMotif> parseMotifs(List<String> codes) {
        return codes.collect { ResidueMotif.parse(it) }
    }

//===========================================================================================================//

    /**
     * Lazily calculated related residues
     */
    static class NeighbourResidues {
        List<Residues.ResWithDist> neighboursByDistance // sorted

        NeighbourResidues(List<Residues.ResWithDist> nearestByDistance) {
            this.neighboursByDistance = nearestByDistance
        }

        List<Residue> getResidues() {
            return neighboursByDistance*.residue
        }

        List<Residue> nNearest(int n) {
            return head(n, neighboursByDistance)*.residue
        }

        List<Residue> inRadius(double radius) {
            int i = 0
            while (i < neighboursByDistance.size() && neighboursByDistance[i].distance <= radius) {
                i++
            }

            return neighboursByDistance.subList(0, i)*.residue
        }

    }

    static class PointKey {
        double x
        double y
        double z

        PointKey(double x, double y, double z) {
            this.x = x
            this.y = y
            this.z = z
        }

        static PointKey fromAtom(Atom atom) {
            Point3d p = atom.coordsAsPoint3d
            return new PointKey(p.x, p.y, p.z)
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            PointKey key = (PointKey) o

            if (Double.compare(key.x, x) != 0) return false
            if (Double.compare(key.y, y) != 0) return false
            if (Double.compare(key.z, z) != 0) return false

            return true
        }

        int hashCode() {
            int result
            long temp
            temp = x != +0.0d ? Double.doubleToLongBits(x) : 0L
            result = (int) (temp ^ (temp >>> 32))
            temp = y != +0.0d ? Double.doubleToLongBits(y) : 0L
            result = 31 * result + (int) (temp ^ (temp >>> 32))
            temp = z != +0.0d ? Double.doubleToLongBits(z) : 0L
            result = 31 * result + (int) (temp ^ (temp >>> 32))
            return result
        }
    }

    private NeighbourResidues getNeighborResidues(Protein protein, Atom point) {
        Map<PointKey, NeighbourResidues> cache = (Map<PointKey, NeighbourResidues>) protein.secondaryData.get(CACHE_KEY_NEIHBORS)

        return cache.computeIfAbsent(PointKey.fromAtom(point), {
            def nearestByDistance = Residues.of(protein.residues.cutoutSphere(point, MAX_RADIUS)).sortedByDistanceToAtom(point)
            new NeighbourResidues(nearestByDistance)
        })
    }


    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Protein protein = context.protein

        List<ResidueMotif> motifs = (List<ResidueMotif>) protein.secondaryData.get(CACHE_KEY_COMPILED)
        int numMotifs = motifs.size()
        int maxMotifSize = motifs*.size.max()

        NeighbourResidues neighbors = getNeighborResidues(protein, sasPoint)

        //log.debug("Neighbors: '{}'", neighbors.residues*.codeCharMasked)

        List<Residue> nearest = neighbors.nNearest(maxMotifSize)
        List<Residue> inRadius = []
        if (params.feat_stmotif_useradius) {
            inRadius = neighbors.inRadius(params.feat_stmotif_radius)
        }

        double[] result = new double[numMotifs]
        int i = 0
        for (ResidueMotif motif : motifs) {
            List<Residue> resForMotif
            if (params.feat_stmotif_useradius && inRadius.size() >= motif.size) {
                resForMotif = inRadius
            } else {
                resForMotif = head(motif.size, nearest)
            }

            boolean matches = motif.matches(resForMotif)

            result[i] = matches ? 1d : 0d
            i++
        }

        return result
    }

}
