package cz.siret.prank.program.visualization.renderers

import cz.siret.prank.domain.CofactorHandler
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.LabeledResidue
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.visualization.RenderingModel
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

import java.awt.*
import java.util.List

import static cz.siret.prank.program.visualization.PredictionVisualizer.writeLabeledPointsPdb

/**
 * Generates PyMol visualization of RenderingModel.
 *
 * Used for visualization of residue predictions.
 */
@Slf4j
@CompileStatic
class NewPymolRenderer implements Parametrized {

    String outdir
    RenderingModel model

    String label
    String pmlFile
    String dataDir

    NewPymolRenderer(String outdir, RenderingModel model) {
        this.outdir = outdir
        this.model = model
    }

    void render() {
        label = model.label
        pmlFile = "$outdir/${label}.pml"
        dataDir = "$outdir/data"

        Futils.mkdirs(dataDir)

        String proteinFileAbs = Futils.absPath(model.proteinFile)
        String proteinFile = proteinFileAbs

        if (model.doubleLabeling != null) {
            // temporary solution: set labeling as b-factor to protein atoms

            model.protein.allAtoms.each { it.setTempFactor(0f) }
            model.doubleLabeling.labeledResidues.each { lr ->
                lr.residue.atoms.each { a ->
                    a.setTempFactor((float)(lr.label ?: 0f))
                }
            }
        }

        boolean isCif = proteinFileAbs.contains(".cif")
        if (params.vis_generate_proteins || isCif) {
            // Always generate PDB for CIF inputs (PyMOL can't reliably parse BioJava CIF)
            String name = Futils.shortName(proteinFile)
            if (isCif) {
                name = name.replace(".cif", ".pdb")  // PyMOL uses extension to pick parser
            }
            String newfAbs = "$dataDir/$name"
            newfAbs = model.protein.saveToPdbFile(newfAbs, true)
            proteinFile = "data/" + Futils.shortName(newfAbs)
            proteinFileAbs = newfAbs
        } else if (params.vis_copy_proteins) {
            String name = Futils.shortName(proteinFile)
            String newfAbs = "$dataDir/$name"
            Futils.copy(proteinFileAbs, newfAbs)
            proteinFile = "data/" + Futils.shortName(newfAbs)
            proteinFileAbs = newfAbs
        }

        Futils.writeFile(pmlFile, renderMainPmlScript(proteinFile))
    }


    private String renderMainPmlScript(String proteinFile) {
"""
from pymol import cmd,stored

set depth_cue, 1
set fog_start, 0.4

set_color b_col, [36,36,85]
set_color t_col, [10,10,10]
set bg_rgb_bottom, b_col
set bg_rgb_top, t_col      
set bg_gradient

set  spec_power  =  200
set  spec_refl   =  0

load "$proteinFile", prot
create ligands, prot and organic
select xlig, prot and organic
delete xlig

hide everything, all
remove hydrogens
remove solvent

color white, elem c
color bluewhite, prot

show surface, prot
#show wire, prot

#show sticks, ligands
#set stick_color, magenta
#show spheres, ligands
#set sphere_color, gray60

${renderLigands()}

${renderCofactors()}

${renderLabaledPoints()}

${renderResidueColoring()}

${renderSiteCentroids()}

deselect

orient
"""
    }

    private String renderLigands() {

        List<String> ligandAtomIds = model.protein.allRelevantLigandAtoms.collect {it.PDBserial.toString() }
        String idsOrList = ligandAtomIds.collect {"id $it" }.join(" or ")

        if (ligandAtomIds.empty) return ""

"""
select ligand_atoms, $idsOrList
show spheres, ligand_atoms
set sphere_color, red
"""
    }

    /**
     * Render cofactor atoms as teal sticks, distinct from ligand spheres and protein cartoon.
     *
     * Emits one PyMOL selection per cofactor type (e.g. {@code cofactor_FAD},
     * {@code cofactor_PLP}) so users can toggle them independently, plus an aggregate
     * {@code cofactor_atoms} selection that drives the rendering. Unmatched cofactor
     * specifiers are noted in a header comment for diagnostics.
     */
    private String renderCofactors() {
        if (!params.vis_highlight_cofactors) return ""
        CofactorHandler.ExtractionResult result = model.cofactorResult
        if (result == null || result.atoms.empty) return ""

        return cofactorPymolBlock(result)
    }

    /** Shared rendering of a cofactor ExtractionResult - keeps the logic in one place. */
    static String cofactorPymolBlock(CofactorHandler.ExtractionResult result) {
        StringBuilder sb = new StringBuilder()
        sb << "# cofactors (HETATMs treated as protein surface)\n"
        if (!result.unmatchedSpecifiers.isEmpty()) {
            sb << "# unmatched specifiers (no group found): ${result.unmatchedSpecifiers}\n"
        }
        sb << "set_color cofactor_col, [73, 168, 199]\n"

        // Per-name selections - addressable in PyMOL for toggling/colouring individual cofactors.
        List<String> perNameSelectors = new ArrayList<>()
        for (Map.Entry<String, List<Group>> e : result.foundGroups.entrySet()) {
            String selName = "cofactor_" + e.key.replaceAll(/[^A-Za-z0-9]/, '_')
            List<String> ids = new ArrayList<>()
            for (Group g : e.value) {
                for (Atom a : Atoms.allFromGroup(g).withoutHydrogens().list) {
                    ids.add(a.PDBserial.toString())
                }
            }
            if (ids.isEmpty()) continue
            String idsOrList = ids.collect { "id $it" }.join(" or ")
            sb << "select ${selName}, ${idsOrList}\n"
            perNameSelectors << selName
        }
        if (perNameSelectors.isEmpty()) return ""

        sb << "select cofactor_atoms, " << perNameSelectors.join(" or ") << "\n"
        sb << "show sticks, cofactor_atoms\n"
        sb << "color cofactor_col, cofactor_atoms\n"
        sb << "set stick_radius, 0.18, cofactor_atoms\n"
        return sb.toString()
    }

    private String renderResidueColoring() {
        if (model.observedLabeling != null) {
            if (model.predictedLabeling != null) {
                renderObservedVsPredicted(model.observedLabeling, model.predictedLabeling)
            } else {
                renderBinaryResidueColoring(model.observedLabeling)
            }
        } else if (model.doubleLabeling != null) {
            renderDoubleColoring(model.doubleLabeling)
        } else {
            return ""
        }
    }

    private String renderDoubleColoring(ResidueLabeling<Double> labeling) {
        //spectrum b, blue_red, minimum=10, maximum=50   //rainbow_rev
        //cmd.spectrum("b", "rainbow", selection="prot", minimum=0, maximum=1)
"""                      
cmd.spectrum("b", "rainbow", selection="prot", minimum=0, maximum=1)
"""
    }

    private String renderBinaryResidueColoring(BinaryLabeling labeling) {
        StringBuilder res = new StringBuilder()

        res << "set_color pos_res_col = " + pyColor(model.style.positiveResiduesColor) + "\n"
        res << "set_color neg_res_col = " + pyColor(model.style.negativeResiduesColor) + "\n"

        List<Integer> posIds = new ArrayList<>()
        List<Integer> negIds = new ArrayList<>()
        for (LabeledResidue<Boolean> lr : labeling.labeledResidues) {
            (lr.label ? posIds : negIds).addAll(lr.residue.atoms.indexes)
        }

        res << renderBulkSelection("pos_residues", "pos_res_col", posIds)
        res << renderBulkSelection("neg_residues", "neg_res_col", negIds)

        return res.toString()
    }

    private String renderObservedVsPredicted(BinaryLabeling observed, BinaryLabeling predicted) {
        StringBuilder res = new StringBuilder()

        res << "set_color tp_col = " + pyColor(model.style.tpColor) + "\n"
        res << "set_color fp_col = " + pyColor(model.style.fpColor) + "\n"
        res << "set_color fn_col = " + pyColor(model.style.fnColor) + "\n"

        List<Integer> tpIds = new ArrayList<>()
        List<Integer> fpIds = new ArrayList<>()
        List<Integer> fnIds = new ArrayList<>()

        for (int i = 0; i != observed.labeledResidues.size(); i++) {
            LabeledResidue<Boolean> obs = observed.labeledResidues[i]
            LabeledResidue<Boolean> pred = predicted.labeledResidues[i]

            if (!obs.label && !pred.label) continue // TN

            List<Integer> ids = obs.residue.atoms.indexes
            if (obs.label) {
                (pred.label ? tpIds : fnIds).addAll(ids)
            } else {
                fpIds.addAll(ids)
            }
        }

        res << renderBulkSelection("tp_residues", "tp_col", tpIds)
        res << renderBulkSelection("fp_residues", "fp_col", fpIds)
        res << renderBulkSelection("fn_residues", "fn_col", fnIds)

        return res.toString()
    }

    private String renderLabaledPoints() {
        if (model.labeledPoints == null) return "# labeled points not rendered"

        String pointsfAbs = "$dataDir/${label}_points.pdb.gz"
        String pointsfRel = "data/" + Futils.shortName(pointsfAbs)

        writeLabeledPointsPdb(pointsfAbs, model.labeledPoints)

"""
load "$pointsfRel", points
hide nonbonded, points
show nb_spheres, points
cmd.spectrum("b", "$params.vis_point_gradient_pymol", selection="points", minimum=0, maximum=$params.vis_point_gradient_max)

#select pockets, resn STP
stored.list=[]
cmd.iterate("(resn STP)","stored.list.append(resi)")    #read info about residues STP
#print stored.list
lastSTP=stored.list[-1] #get the index of the last residu
hide lines, resn STP

# sas points
cmd.select("rest", "resn STP and resi 0")
cmd.set("sphere_scale","0.3","rest")


"""

// # pockets (old)
// #for my_index in range(1,int(lastSTP)+1): cmd.select("pocket"+str(my_index), "resn STP and resi "+str(my_index))
// #for my_index in range(1,int(lastSTP)+1): cmd.show("spheres","pocket"+str(my_index))
// #for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_scale","0.4","pocket"+str(my_index))
// #for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_transparency","0.1","pocket"+str(my_index))

    }

    /**
     * Renders a single PyMol selection + coloring for a list of atom IDs.
     * Chunks the IDs to avoid excessively long command lines.
     */
    private static String renderBulkSelection(String selName, String colorName, List<Integer> atomIds) {
        if (atomIds.isEmpty()) return ""

        StringBuilder res = new StringBuilder()

        // Chunk into groups to keep command lines reasonable
        int chunkSize = 2000
        boolean first = true
        for (int start = 0; start < atomIds.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, atomIds.size())
            String idList = atomIds.subList(start, end).join(",")
            if (first) {
                res << "select $selName, prot and id [$idList] \n"
                first = false
            } else {
                res << "select $selName, $selName or (prot and id [$idList]) \n"
            }
        }

        res << "color $colorName, $selName \n"
        res << "set surface_color, $colorName, $selName \n"

        return res.toString()
    }

    private String renderSiteCentroids() {
        if (!params.vis_site_centers || model.siteCentroids == null || model.siteCentroids.isEmpty()) return ""

        StringBuilder res = new StringBuilder()
        res << "# site centroids\n"
        for (Atom c : model.siteCentroids) {
            res << sprintf("pseudoatom site_centers, pos=[%.3f, %.3f, %.3f]\n", c.x, c.y, c.z)
        }
        res << "show spheres, site_centers\n"
        res << "set sphere_scale, 0.8, site_centers\n"
        res << "color hotpink, site_centers\n"
        return res.toString()
    }

//===========================================================================================================//

    static String pyColor(Color c) {
        sprintf "[%5.3f,%5.3f,%5.3f]", c.red/255, c.green/255, c.blue/255
    }
    
}
