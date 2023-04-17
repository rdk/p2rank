package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.Failable
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.Writable
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

/**
 * Ligand categorizer and holder.
 */
@Slf4j
//@CompileStatic
class Ligands implements Parametrized, Writable, Failable {

    /* ligands that are considered during training and evaluation, other ligands are ignored */
    List<Ligand> relevantLigands = new ArrayList<>()

    /* ligands that are ignored because they are too small */
    List<Ligand> smallLigands = new ArrayList<>()

    /* usually cofactors and biologically not relevant ligands */
    List<Ligand> ignoredLigands = new ArrayList<>()

    /* ligands(hetgroups) that are too distant from the protein surface */
    List<Ligand> distantLigands = new ArrayList<>()

//===========================================================================================================//

    /**
     * @return ignoredLigands + smallLigands + distantLigands
     */
    List<Ligand> getAllIgnoredLigands() {
        return ignoredLigands + smallLigands + distantLigands
    }

    int getRelevantLigandCount() {
        relevantLigands.size()
    }

    int getIgnoredLigandCount() {
        ignoredLigands.size()
    }

    int getSmallLigandCount() {
        smallLigands.size()
    }

    int getDistantLigandCount() {
        distantLigands.size()
    }

    /**
     * @return all atoms from relevant ligands
     */
    Atoms getAllRelevantLigandAtoms() {
        return Atoms.join(relevantLigands*.atoms)
    }

//===========================================================================================================//

    public Ligands loadForProtein(Protein protein, LoaderParams loaderParams, String pdbFileName) {

        if (loaderParams.ligandsSeparatedByTER) {
            // ligands are separated by TER lines in specific datasets (CHEN11)
            // we are assuming all ligands are relevant
            List<Atoms> ligAtomGroups = getLigandAtomGroupsByTER(protein.allAtoms, pdbFileName)
            List<Ligand> ligands = makeLigands(ligAtomGroups, protein)
            relevantLigands = ligands

        } else {
            List<Group> ligandGroups = Struct.getLigandGroups(protein)

            if (loaderParams.relevantLigandsDefined) {
                log.info "Relevant ligands are explicitly defined in the dataset: " + loaderParams.relevantLigandDefinitions
            }

            def split = Cutils.splitByPredicate(ligandGroups, {isRelevantLigandGroup(it, protein, loaderParams) })
            List<Group> relevantGroups = split.positives
            List<Group> ignoredGroups = split.negatives

            if (loaderParams.relevantLigandsDefined) {
                checkLigandMatches(loaderParams, pdbFileName)
            }

            List<Atoms> relevantAtomGroups = relevantGroups.collect { Atoms.allFromGroup(it) }
            List<Atoms> ignoredAtomGroups = ignoredGroups.collect { Atoms.allFromGroup(it) }

            relevantAtomGroups = Struct.clusterAtomGroups(relevantAtomGroups, params.ligand_clustering_distance)
            List<Ligand> ligands = makeLigands(relevantAtomGroups, protein)
            if (loaderParams.relevantLigandsDefined) {
                relevantLigands = ligands
            } else {
                categorizeLigands(ligands, loaderParams)
            }

            ignoredLigands = makeLigands(ignoredAtomGroups, protein)
        }

        log.info "Loaded ${relevantLigands.size()} relevant ligands: " + (relevantLigands*.name)

        sortLigands relevantLigands
        sortLigands ignoredLigands
        sortLigands smallLigands
        sortLigands distantLigands

        return this
    }

    private void checkLigandMatches(LoaderParams loaderParams, String pdbFileName) {
        for (Dataset.LigandDefinition ligDef : loaderParams.relevantLigandDefinitions) {
            int maches = ligDef.matchesGroupIds.size()
            log.debug("Ligand definition '{}' matches {} ligand groups: {}", ligDef.originalString, maches, ligDef.matchesGroupIds)
            if (maches == 0) {
                fail("Ligand definition '$ligDef.originalString' in protein '$pdbFileName' matches no ligands.", log)
            }
        }
    }

    private static boolean isRelevantLigandGroup(Group group, Protein protein, LoaderParams loaderParams) {
        if (loaderParams.relevantLigandsDefined) {
            for (Dataset.LigandDefinition ligDef : loaderParams.relevantLigandDefinitions) {
                if (ligDef.matchesGroup(group, protein)) {
                    return true
                }
            }
            return false
        } else {
            return ! loaderParams.ignoredHetGroups.contains(group.PDBName)
        }
    }


    private List<Ligand> makeLigands(List<Atoms> ligAtomGroups, Protein protein) {
        log.info "loading ${ligAtomGroups.size()} ligands"

        List<Ligand> res = new ArrayList<>()

        for (Atoms ligAtoms in ligAtomGroups) {
            if (ligAtoms.count > 0) {
                Ligand lig = new Ligand(ligAtoms, protein)
                lig.centerToProteinDist = protein.proteinAtoms.dist(lig.atoms.centerOfMass)
                lig.contactDistance = protein.proteinAtoms.dist(lig.atoms)

                res.add(lig)
            }
        }

        return res
    }

    private void sortLigands(List<Ligand> ligands) {
        ligands.sort { it.nameCode + "_" + it.code }
    }


//===========================================================================================================//

    private void categorizeLigands(List<Ligand> ligands, LoaderParams loaderParams) {
        for (Ligand ligand : ligands) {
            categorizeLigand(ligand, loaderParams)
        }
    }

    /**
     * pranks own ligand categorization logic
     * relevant / distant / too small / ignored
     */
    private void categorizeLigand(Ligand lig, LoaderParams loaderParams) {

        if (lig.atoms.count < loaderParams.minLigandAtoms ) {

            log.info "ignoring ligand $lig.name with only $lig.atoms.count atoms (min ${loaderParams.minLigandAtoms})"
            smallLigands.add(lig)

        } else if (lig.contactDistance > params.ligand_protein_contact_distance) {

            log.info "ignoring ligand $lig.name that is not in contact with protein surface d=$lig.contactDistance (max=$params.ligand_protein_contact_distance)"
            distantLigands.add(lig)

        } else if (lig.centerToProteinDist > params.ligc_prot_dist) {

            log.info "ignoring ligand $lig.name that has a center too far from protein surface d=$lig.centerToProteinDist (max=$params.ligc_prot_dist)"
            distantLigands.add(lig)

        } else {

            relevantLigands.add(lig)

        }

    }

//===========================================================================================================//

    /*
     * in chen11 dataset ligand are defined as groups differentiated by TER records, not by the group IDs
     * so some ligands are represented as more than 1 HETATM groups
     */
    private List<Atoms> getLigandAtomGroupsByTER(Atoms allStructAtoms, String pdbFileName) {
        List<Atoms> res = new ArrayList<>()
        List<Atom> curLigAtoms = new ArrayList<>()

        for (String line : new File(pdbFileName).readLines()) {
            if (line.startsWith("A")) {
                continue
            } else if (line.startsWith("HETATM")) {
                String tline = line.substring(6)

                // log.info line.substring(0, line.indexOf(' ')) + "<<<<<"
                int atomId = firstWord(tline).toInteger()
                Atom a = allStructAtoms.getByID(atomId)

                if (a!=null) {
                    curLigAtoms.add( a )
                } else {
                    // this most probably means that is was alternative location record
                    log.warn "can't find atom $atomId! file: $pdbFileName\n$line\n"
                }

            } else if (line.startsWith("TER")) {
                if (curLigAtoms!=null) {
                    res.add(new Atoms(curLigAtoms))
                }
                curLigAtoms = new ArrayList<>()
            }
        }

        return res
    }

    String firstWord(String s) {
        s = s.trim();
        for (int i=0; i!=s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return s.subSequence(0,i)
            }
        }
        return s
    }

}
