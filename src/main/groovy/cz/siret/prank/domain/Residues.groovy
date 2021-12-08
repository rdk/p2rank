package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.Cutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

import javax.annotation.Nullable
import java.util.function.Function

/**
 *
 */
@Slf4j
@CompileStatic
class Residues implements Iterable<Residue> {

    private List<Residue> list
    private Atoms atoms
    private Map<Residue.Key, Residue> indexByKey

    Residues(List<Residue> list) {
        this.list = list
        this.indexByKey = Cutils.mapWithIndex(list, { it.key })

        if (indexByKey.size() < list.size()) {
            log.warn "Multiple residues with same label! [{}]", Cutils.findDuplicates(list*.key).join(",")
        }
    }

    static Residues of(List<Residue> list) {
        return new Residues(list)
    }

    List<Residue> getList() {
        return list
    }

    @Override
    Iterator<Residue> iterator() {
        return list.iterator()
    }

    Atoms getAtoms() {
        if (atoms == null) {
            atoms = Atoms.join(list.collect { it.atoms })
        }
        atoms
    }

    boolean contains(Residue residue) {
        indexByKey.containsKey(residue.key)
    }

    int getCount() {
        list.size()
    }

    @Nullable
    Residue getResidue(Residue.Key key) {
        indexByKey.get(key)
    }

    @Nullable
    Residue getResidueForAtom(Atom a) {
        getResidue(Residue.Key.forAtom(a))
    }

    @Nullable
    Residue getResidueForGroup(Group g) {
        getResidue(Residue.Key.forAtom(g?.atoms?.first()))
    }

    List<Residue> getDistinctForAtoms(Atoms atoms) {
        atoms.distinctGroups.collect { getResidueForGroup(it) }.findAll{ it != null }.unique()
    }

//===========================================================================================================//

    List<Residue> cutoutSphere(Atom atom, double radius) {
        getDistinctForAtoms(getAtoms().cutoutSphere(atom, radius))
    }

    List<Residue> cutoutShell(Atoms aroundAtoms, double radius) {
        getDistinctForAtoms(getAtoms().cutoutShell(aroundAtoms, radius))
    }

//===========================================================================================================//

    Residue findNearest(Atom point) {
        Atom nearestAtom = getAtoms().withKdTree().kdTree.findNearest(point)
        return getResidueForAtom(nearestAtom)
    }

    /**
     * @return sorted ascending
     */
    List<Residue> findNNearestToAtom(int n, Atom point) {
        return findNNearestTo(n, {it.atoms.sqrDist(point) })
    }

    /**
     * @return sorted ascending
     */
    List<Residue> findNNearestToAtoms(int n, Atoms toAtoms) {
        return findNNearestTo(n, {it.atoms.sqrDist(toAtoms) })
    }

    /**
     * @return sorted ascending
     */
    List<Residue> findNNearestTo(int n, Function<Residue, Double> distanceFunction) {
        List<ResWithDist> resWithDist = Cutils.head(n, sortedByDistance(distanceFunction))
        return resWithDist.collect { it.residue }
    }

    List<ResWithDist> sortedByDistanceToAtom(Atom point) {
        return sortedByDistance({it.atoms.dist(point) })
    }

    List<ResWithDist> sortedByDistance(Function<Residue, Double> distanceFunction) {
        List<ResWithDist> resWithDist = list.collect { new ResWithDist(it, distanceFunction.apply(it)) }
        resWithDist.sort { it.distance }
        return resWithDist
    }

    static class ResWithDist {
        Residue residue
        double distance

        ResWithDist(Residue residue, double distance) {
            this.residue = residue
            this.distance = distance
        }
    }

}
