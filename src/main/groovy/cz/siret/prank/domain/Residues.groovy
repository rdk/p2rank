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
        getResidue(Residue.Key.forAtom(a) as Residue.Key)
    }

    @Nullable
    Residue getResidueForGroup(Group g) {
        getResidue(Residue.Key.forAtom(g?.atoms?.first()) as Residue.Key)
    }

    List<Residue> getDistinctForAtoms(Atoms atoms) {
        atoms.distinctGroups.collect { getResidueForGroup(it) }.findAll{ it != null }.asList()
    }

    Residue findNearest(Atom point) {
        Atom nearestAtom = getAtoms().withKdTree().kdTree.findNearest(point)
        return getResidueForAtom(nearestAtom)
    }

    @Override
    Iterator<Residue> iterator() {
        return list.iterator()
    }

}
