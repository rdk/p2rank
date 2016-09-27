package cz.siret.prank.geom

import com.google.common.collect.ComparisonChain
import com.google.common.collect.Ordering
import cz.siret.prank.geom.clustering.AtomGroupClusterer
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.*
import cz.siret.prank.geom.clustering.AtomClusterer
import cz.siret.prank.geom.clustering.SLinkClusterer
import cz.siret.prank.utils.PerfUtils

@Slf4j
@CompileStatic
class Struct {

    static double dist(Atom a, Atom b) {
        return PerfUtils.dist(a.coords, b.coords)
    }

    static double sqrDist(Atom a, Atom b) {
        return PerfUtils.sqrDist(a.coords, b.coords)
    }

    static double dist(Atom a, List<Atom> list) {
        if (list==null || list.isEmpty()) {
            log.warn "!! dist to empty list of atoms"
            return Double.MAX_VALUE
        }

        double sqrDist = sqrDist(a, list)

        return Math.sqrt(sqrDist)
    }

    static double sqrDist(Atom a, List<Atom> list) {
        if (list==null || list.isEmpty()) {
            log.warn "!! dist to empty list of atoms"
            return Double.MAX_VALUE
        }

        double minDist = Double.MAX_VALUE
        for (Atom b : list) {
            double next = sqrDist(a, b)
            if (next<minDist) {
                minDist = next
            }
        }

        return minDist
    }

    static double dist(List<Atom> list1, List<Atom> list2) {

        if (list1==null || list1.isEmpty()) {
            log.warn "!!! dist to empty list of atoms"
            return Double.MAX_VALUE
        }

        return list1.collect{Atom a -> dist(a, list2)}.min()
    }

    static boolean areWithinDistance(Atom a, List<Atom> list, double dis) {
        dis = dis*dis
        for (Atom b : list) {
            if (sqrDist(a,b) <= dis) {
                return true
            }
        }
        return false
    }

    static boolean areDistantAtLeast(Atom a, List<Atom> list, double dis) {
        dis = dis*dis
        for (Atom b : list) {
            if (sqrDist(a,b) < dis) {
                return false
            }
        }
        return true
    }

    static boolean areWithinDistance(List<Atom> list1, List<Atom> list2, double dis) {
        dis = dis*dis
        for (Atom a : list1) {
            for (Atom b : list2) {
                if (sqrDist(a,b)  <= dis) {
                    return true
                }
            }
        }
        return false
    }

    static List<Atom> cutoffAtoms(List<Atom> chooseFrom, List<Atom> distanceTo, double cutoffDist) {
        return chooseFrom.findAll { Atom a ->
            areWithinDistance(a, distanceTo, cutoffDist)
        }.asList()
    }

    static boolean isInBox(Atom a, Box box) {
        if (a.x>box.max.x || a.x<box.min.x) return false;
        if (a.y>box.max.y || a.y<box.min.y) return false;
        if (a.z>box.max.z || a.z<box.min.z) return false;
        return true
    }

    static List<Atom> cutoffAtomsInBox(List<Atom> chooseFrom, Box box) {
        return chooseFrom.findAll { Atom a -> isInBox(a, box)}.asList()
    }

    static boolean isHydrogenAtom(Atom atom) {

        // biojava is not reliable in assigning correct H element - see metapocket ub48 dataset

        if (Element.H == atom.element) return true
        if (atom.name.startsWith("H")) return true
        if (atom.name.length()>1 && atom.name[1]=='H') return true
        return false
    }

    /**
     * comes from HETATM record
     *
     * depends on modified biojava library
     */
    static boolean isHetAtom(Atom atom) {
        isHetGroup(atom.group)
    }

    static boolean isHetGroup(Group group) {
        if (group==null) return false

        GroupType.HETATM == group.type
    }

    static List<Group> getGroups(Structure struc) {
        List<Group> res = new ArrayList<>()
        GroupIterator gi = new GroupIterator(struc)
        while (gi.hasNext()){
            Group g = (Group) gi.next();
            res.add(g)
        }
        return res
    }

    public static boolean isProteinChainGroup(Group g) {
        return !isHetGroup(g) && !"STP".equals(g.PDBName) && !"HOH".equals(g.PDBName)
    }

    /**
     * @return true if ligand group (except HOH)
     */
    public static boolean isLigandGroup(Group g) {

        return isHetGroup(g) && !"HOH".equals(g.PDBName)
    }

    /**
     * @return ligand groups without HOH
     */
    static List<Group> getLigandGroups(Structure struc) {
        return getGroups(struc).findAll{ isLigandGroup((Group)it) }.asList()
    }

    /**
     * @return all HETATM groups
     */
    static List<Group> getHetGroups(Structure struc) {
        return getGroups(struc).findAll{ isHetGroup((Group)it) }.asList()
    }

    /**
     * single lincage clustering
     * @param clusters
     * @param clusterDist
     * @return
     */
    static List<Atoms> clusterAtoms(Atoms atoms, double clusterDist) {
        return new AtomClusterer(new SLinkClusterer<Atom>()).clusterAtoms(atoms, clusterDist)
    }

    static List<Atoms> clusterAtomGroups(List<Atoms> atomGroups, double clusterDist ) {
        return new AtomGroupClusterer(new SLinkClusterer()).clusterGroups(atomGroups, clusterDist)
    }


    static List<Group> sortGroups(Iterable<Group> groups) {

        new Ordering<Group>() {
            @Override
            int compare(Group left, Group right) {
                ComparisonChain.start()
                    .compare(left?.PDBName, right?.PDBName)
                    .compare(left?.residueNumber, right?.residueNumber)
                .result()
            }
        }.sortedCopy(groups)

    }

}
