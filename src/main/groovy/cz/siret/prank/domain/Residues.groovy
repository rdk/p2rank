package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.Cutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

import javax.annotation.Nullable

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
        atoms.distinctGroups.collect { getResidueForGroup(it) }.findAll{ it != null }.asList()
    }

    Residue findNearest(Atom point) {
        Atom nearestAtom = getAtoms().withKdTree().kdTree.findNearest(point)
        return getResidueForAtom(nearestAtom)
    }

    List<Residue> findNNearestToAtoms(int n, Atoms toAtoms) {
        List<Tuple2<Residue,Double>> resWithDist = list.collect { Tuple.tuple(it, it.atoms.dist(toAtoms)) }
        resWithDist.sort { it.v2 }

        resWithDist = Cutils.head(n, resWithDist)

        return resWithDist.collect { it.v1 }
    }

    @Override
    Iterator<Residue> iterator() {
        return list.iterator()
    }

}
