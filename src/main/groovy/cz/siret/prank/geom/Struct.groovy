package cz.siret.prank.geom

import com.google.common.collect.ComparisonChain
import com.google.common.collect.Ordering
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.geom.clustering.AtomClusterer
import cz.siret.prank.geom.clustering.AtomGroupClusterer
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.PdbUtils
import cz.siret.prank.utils.PerfUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.biojava.nbio.structure.*

import javax.annotation.Nullable

import static cz.siret.prank.utils.Cutils.nextInList
import static cz.siret.prank.utils.Cutils.previousInList

@Slf4j
@CompileStatic
class Struct {

    static double dist(Atom a, Atom b) {
        return PerfUtils.dist(a, b)
    }

    static double sqrDist(Atom a, Atom b) {
        return PerfUtils.sqrDist(a, b)
    }

    static double dist(Atom a, List<Atom> list) {
        if (list==null || list.isEmpty()) {
            //log.debug "! dist to empty list of atoms"  // TODO: analyze this reoccurring situation
            return Double.MAX_VALUE
        }

        double sqrDist = sqrDist(a, list)

        return Math.sqrt(sqrDist)
    }

    static double sqrDist(Atom a, List<Atom> list) {
        return PerfUtils.sqrDistL(a, list)
    }

    static double dist(List<Atom> list1, List<Atom> list2) {

        if (list1==null || list1.isEmpty()) {
            //log.debug "!!! dist to empty list of atoms"
            return Double.MAX_VALUE
        }

        return list1.collect{Atom a -> dist(a, list2)}.min()
    }

    static Point distPoint(Atom a, Atom b) {
        Point.of(Math.abs(a.x-b.x), Math.abs(a.y-b.y), Math.abs(a.z-b.z))
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
        if (a.x>box.max.x || a.x<box.min.x) return false
        if (a.y>box.max.y || a.y<box.min.y) return false
        if (a.z>box.max.z || a.z<box.min.z) return false
        return true
    }

    static List<Atom> cutoffAtomsInBox(List<Atom> chooseFrom, Box box) {
        return chooseFrom.findAll { Atom a -> isInBox(a, box)}.asList()
    }

    static boolean isHydrogenAtom(Atom atom) {

        // biojava is not reliable in assigning correct H element - see metapocket ub48 dataset

        // Element.H.is(...) is reference identity (enum singletons), compiled to acmp; the
        // Groovy '==' here routed through ScriptBytecodeAdapter.compareEqual per atom. .is() on
        // the non-null H constant is null-safe (atom.element may be null). charAt(1) avoids the
        // StringGroovyMethods.getAt + Character boxing + compareEqual that name[1]=='H' incurred.
        if (Element.H.is(atom.element)) return true
        if (atom.name.startsWith("H")) return true
        if (atom.name.length()>1 && atom.name.charAt(1)=='H') return true
        return false
    }


    static boolean isHetGroup(Group group) {
        if (group == null) return false

        return GroupType.HETATM.equals(group.type)
    }

    /**
     * Returns true if the group is a potential ligand candidate.
     *
     * BioJava assigns GroupType based on chemical classification (Chemical Component Dictionary),
     * not structural role. This means ligands in non-polymer chains can have any GroupType:
     * - GDP, GTP, ATP get GroupType.NUCLEOTIDE (nucleotide derivatives)
     * - SHR and similar get GroupType.AMINOACID (amino acid derivatives)
     * - Most other ligands get GroupType.HETATM
     *
     * To handle all cases, this method considers a group as a ligand candidate if:
     * - It has GroupType.HETATM (traditional case, works regardless of chain type), OR
     * - It is in a NONPOLYMER chain (covers all misclassified GroupTypes)
     *
     * Groups in polymer chains (protein AA chains, DNA/RNA) are only included
     * if they have GroupType.HETATM (e.g. covalently bound ligands, modified residues).
     */
    static boolean isLigandCandidateGroup(Group group) {
        if (group == null) return false
        if (GroupType.HETATM.equals(group.type)) return true

        // Any group in a non-polymer chain is a ligand candidate,
        // regardless of BioJava's GroupType classification.
        Chain chain = group.getChain()
        return chain != null && isNonPolymerChain(chain)
    }

    static List<Group> getGroups(Structure struc) {
        List<Group> res = new ArrayList<>()
        GroupIterator gi = new GroupIterator(struc)
        while (gi.hasNext()){
            Group g = (Group) gi.next()
            res.add(g)
        }
        return res
    }

    /**
     * @return true if ligand group (except HOH)
     */
    static boolean isHetNonWaterGroup(Group g) {

        return isHetGroup(g) && !g.isWater()
    }

    /** @see #isLigandCandidateGroup(Group) */
    static boolean isLigandCandidateNonWaterGroup(Group g) {
        return isLigandCandidateGroup(g) && !g.isWater()
    }


    static List<Group> getResidueHetGroups(Protein protein) {
        protein.residues*.group.findAll { isHetGroup(it) }
    }

    /**
     * Returns ligand candidate groups (without water).
     * Includes HETATM groups from any chain and all groups from non-polymer chains.
     * @see #isLigandCandidateGroup(Group)
     */
    static List<Group> getLigandGroups(Protein protein) {
        List<Group> residueHetGroups = getResidueHetGroups(protein)
        List<Group> groups = getGroups(protein.structure).findAll { isLigandCandidateNonWaterGroup(it) }.toList()

        groups.removeAll { residueHetGroups.contains(it) }  // biojava doesn't implement equals() on groups

        return groups
    }

    /**
     * @return all HETATM groups
     */
    static List<Group> getHetGroups(Structure struc) {
        return getGroups(struc).findAll{ isHetGroup(it) }.asList()
    }

    /**
     * Calculate geometric centroid of CA atoms from the given residues.
     * Residues without CA atoms (e.g. non-standard residues) are skipped.
     * @return centroid atom or null if no CA atoms found
     */
    @Nullable
    static Atom calcCaCentroid(List<Residue> residues) {
        List<Atom> caAtoms = new ArrayList<>()
        for (Residue res : residues) {
            AminoAcid aa = res.aminoAcid
            if (aa != null) {
                Atom ca = aa.getCA()
                if (ca != null) {
                    caAtoms.add(ca)
                }
            }
        }
        if (caAtoms.isEmpty()) {
            return null
        }
        return new Atoms(caAtoms).centroid
    }

    /**
     * single linkage clustering
     * @param clusters
     * @param clusterDist
     * @return
     */
    static List<Atoms> clusterAtoms(Atoms atoms, double clusterDist) {
        return AtomClusterer.singleLinkage().clusterAtoms(atoms, clusterDist)
    }

    static List<Atoms> clusterAtomGroups(List<Atoms> atomGroups, double clusterDist ) {
        return AtomGroupClusterer.singleLinkage().clusterGroups(atomGroups, clusterDist)
    }

    static final Ordering<Group> GROUP_ORDERING = new Ordering<Group>() {
        @Override
        int compare(Group left, Group right) {
            ComparisonChain.start()
                    .compare(left?.residueNumber, right?.residueNumber)
                    .compare(left?.PDBName, right?.PDBName)
                    .result()
        }
    }

    static List<Group> sortedGroups(Iterable<Group> groups) {
        GROUP_ORDERING.sortedCopy(groups)
    }

    /**
     * Returns true if amino acid residue chain.
     *
     * Should distinguish between protein AA chains (->true), peptides and small aa ligands (->false)
     */
    static boolean isProteinAaResidueChain(Chain chain) {
        return isPolyChain(chain) && !getResidueGroupsFromChain(chain).isEmpty()
    }

    static boolean isPolyChain(Chain chain) {
        return EntityType.POLYMER.equals(chain.entityInfo?.type)
    }

    /**
     * Returns true if chain entity type is NONPOLYMER.
     * Non-polymer chains contain single-molecule ligands
     * (including nucleotide-derived ligands like GDP, GTP).
     */
    static boolean isNonPolymerChain(Chain chain) {
        return EntityType.NONPOLYMER.equals(chain.entityInfo?.type)
    }

    static boolean isTerminalResidue(Group group) {
        group?.getAtom("OXT") != null
    }

    private static int getLastTerminalResidue(List<Group> chainGroups) {
        for (int i=chainGroups.size()-1; i>=0; i--) {
            if (isAminoAcidResidueHeuristic(i, chainGroups) && isTerminalResidue(chainGroups[i])) {
                return i
            }
        }
        return -1
    }

    static List<Group> getResidueGroupsFromChain(Chain chain) {

        List<Group> chainGroups = chain.getAtomGroups()
        int n = chainGroups.size()
        List<Group> res = new ArrayList<>(n)

        log.info "all groups in chain {}: {}", getAuthorId(chain), n

        //int lastTerminalResidueIdx = getLastTerminalResidue(chainGroups)
        //log.info "lastTerminalResidueIdx: {}", lastTerminalResidueIdx

        for (int i=0; i!=n; i++) {
            Group g = chainGroups[i]
            if (isAminoAcidResidueHeuristic(i, chainGroups)) {
                res.add g

                // Note: commented out because this just doesn't work in proteins with discontinued chains (OXT atom is not present at the last residue)
                //if (i == lastTerminalResidueIdx) {
                //    break // this is done so amino acid ligands at the end are excluded
                //}
            } else {
                log.debug "group {} ({}) considered non-protein", i, g.getPDBName()
            }
        }

        return res
    }

//===========================================================================================================//

	static Atom centerOfMass(Collection<? extends Atom> atoms) {
		return PerfUtils.centerOfMass(atoms)
	}

    static Atom calculateCentroid(Collection<? extends Atom> atoms) {
        return PerfUtils.calculateCentroid(atoms)
    }

//===========================================================================================================//

    static boolean isAminoAcidGroup(Group g) {
        if (g == null) return false
        return GroupType.AMINOACID.equals(g.type)
    }

    /**
     * Should distinguish in particular between modified amino acid residues that are part of the chain (and return true)
     * and amino acid ligands that are not residues (and return false)
     */
    static boolean isAminoAcidResidue(@Nullable Group g) {
        if (g == null) return false

        if (isAminoAcidGroup(g)) return true
        if (g.getPDBName()?.startsWith("UNK")) return true  // TODO revisit

        return false
    }

    /**
     * Tries to determine status of Residue vs AA lLigand based on neighbours in the chain
     * @return
     */
    static boolean isAminoAcidResidueHeuristic(@Nullable Group g, @Nullable Group prev, @Nullable Group next) {
        if (isAminoAcidResidue(g)) return true

        if (g.hasAminoAtoms()
                && (prev == null || isAminoAcidGroup(prev))
                && (next != null && isAminoAcidGroup(next)) ) {  //    next==null clause not admissible, AA ligands are often at the end of the chain

            return true
        }
        return false
    }

    static boolean isAminoAcidResidueHeuristic(int idx, List<Group> chainGroups) {
        return isAminoAcidResidueHeuristic(
                Cutils.listElement(idx, chainGroups),
                Cutils.listElement(idx-1, chainGroups),
                Cutils.listElement(idx+1, chainGroups),
        )
    }

//===========================================================================================================//

    /**
     * returns chain authorId == chain letter in old PDB model
     */
    static String getAuthorId(Chain chain) {
        return chain?.name
    }

    static String getMmcifId(Chain chain) {
        return chain?.id
    }

    static String maskEmptyChainId(String chainId) {
        // Note: masking empty chainId with "A"
        // Doesn't happen in files from PDB but can be seen in some custom PDB files: e.g. in bu48 dataset
        if (StringUtils.isBlank(chainId))  {
            log.warn("Protein has a chain with empty code (authorID): '$chainId'. Masking with 'A'")
            return "A"
        }
        return chainId
    }

    static List<Residue> getResiduesFromChain(Chain chain) {

        List<Group> groups = getResidueGroupsFromChain(chain)

        log.info "{} groups in chain {}", groups.size(), getAuthorId(chain)

        //ordering seems reliable
        //groups.toSorted { it.residueNumber.seqNum }

        List<Residue> residues = new ArrayList<>(groups.size())
        for (Group g : groups) {
            residues.add(Residue.fromGroup(g))
        }

        int len = residues.size()
        for (int i=0; i!=len; i++) {
            residues[i].previousInChain = previousInList(i, residues)
            residues[i].nextInChain = nextInList(i, residues)
        }

        return residues
    }

    /**
     * does not perform checks
     */
    static ResidueChain toResidueChain(Chain chain) {
        new ResidueChain(getAuthorId(chain), getMmcifId(chain), getResiduesFromChain(chain))
    }

    static List<ResidueChain> residueChainsFromStructure(Structure struc) {

        struc.getPolyChains()

        struc.chains.findAll { isProteinAaResidueChain(it) }
                    .collect { toResidueChain(it) }
                    .asList()      
    }

    static final Structure reduceStructureToModel0(Structure s) {

        // TODO try StructureTools.removeModels()

        if (s.nrModels()==1) {
            return s
        } else {
            return PdbUtils.reduceStructureToModel(s, 0)
        }
    }

}
