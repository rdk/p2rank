package cz.siret.prank.program.rendering

import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.labeling.LabeledResidue
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import java.awt.*
import java.util.List

/**
 * Generates PyMol visualization of RenderingModel.
 *
 * Used for visualization of residue predictions.
 */
@Slf4j
@CompileStatic
class PymolRenderer implements Parametrized {

    String outdir
    RenderingModel model

    String label
    String pmlFile
    String dataDir

    PymolRenderer(String outdir, RenderingModel model) {
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

        if (params.vis_generate_proteins || params.vis_copy_proteins) {
            String name = Futils.shortName(proteinFile)
            String newfAbs = "$dataDir/$name"

            if (params.vis_generate_proteins) {
                newfAbs = model.protein.saveToPdbFile(newfAbs, true)
            } else if (params.vis_copy_proteins) {
                Futils.copy(proteinFileAbs, newfAbs)
            }
            String newfRelative = "data/" + Futils.shortName(newfAbs)

            proteinFile = newfRelative
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

load "$proteinFile", protein
create ligands, protein and organic
select xlig, protein and organic
delete xlig

hide everything, all
remove hydrogens
remove solvent

color white, elem c
color bluewhite, protein

#show surface, protein
show wire, protein

#show sticks, ligands
#set stick_color, magenta
#show spheres, ligands
#set sphere_color, gray60

${renderLigands()} 

${renderLabaledPoints()} 

${renderResidueColoring()}

deselect

orient
"""
    }

    private String renderLigands() {

        List<String> ligandAtomIds = model.protein.allRelevantLigandAtoms.collect {it.PDBserial.toString() }
        String idsOrList = ligandAtomIds.collect {"id $it" }.join(" or ")

        if (ligandAtomIds.empty) return

"""                      
select ligand_atoms, $idsOrList
show spheres, ligand_atoms
set sphere_color, red
"""
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
        }
    }

    private String renderDoubleColoring(ResidueLabeling<Double> labeling) {
        //spectrum b, blue_red, minimum=10, maximum=50   //rainbow_rev
        //cmd.spectrum("b", "rainbow", selection="protein", minimum=0, maximum=1)
"""                      
cmd.spectrum("b", "rainbow", selection="protein", minimum=0, maximum=1)
"""
    }

    private String renderBinaryResidueColoring(BinaryLabeling labeling) {
        StringBuilder res = new StringBuilder()

        res << "set_color pos_res_col = " + pyColor(model.style.positiveResiduesColor) + "\n"
        res << "set_color neg_res_col = " + pyColor(model.style.negativeResiduesColor) + "\n"

        int i = 1
        for (LabeledResidue<Boolean> lr : labeling.labeledResidues) {
            String ids = lr.residue.atoms.indexes.join(",")
            String key = "residue_$i"
            String ncol = lr.label ? "pos_res_col" : "neg_res_col"

            res << "select $key, protein and id [$ids] \n"
            res << "color $ncol, $key \n"
            res << "set surface_color, $ncol, $key \n"
            i++
        }

        return res.toString()
    }

    private String renderObservedVsPredicted(BinaryLabeling observed, BinaryLabeling predicted) {
        StringBuilder res = new StringBuilder()

        res << "set_color tp_col = " + pyColor(model.style.tpColor) + "\n"
        res << "set_color fp_col = " + pyColor(model.style.fpColor) + "\n"
        res << "set_color fn_col = " + pyColor(model.style.fnColor) + "\n"

        for (int i = 0; i!=observed.labeledResidues.size(); i++) {
            LabeledResidue<Boolean> obs = observed.labeledResidues[i]
            LabeledResidue<Boolean> pred = predicted.labeledResidues[i]

            if (!obs.label && !pred.label) continue // TN

            String col = ""
            if (obs.label) {
                if (pred.label) {
                    col = "tp_col"
                } else {
                    col = "fn_col"
                }
            } else {
                col = "fp_col"
            }

            String ids = obs.residue.atoms.indexes.join(",")
            String key = "residue_$i"

            res << "select $key, protein and id [$ids] \n"
            res << "color $col, $key \n"
            res << "set surface_color, $col, $key \n"
        }

        return res.toString()
    }

    private String renderLabaledPoints() {
        if (model.labeledPoints == null) return "# labeled points not rendered"

        String pointsfAbs = "$dataDir/${label}_points.pdb.gz"
        String pointsfRel = "data/" + Futils.shortName(pointsfAbs)

        writeLabeledPoints(pointsfAbs, model.labeledPoints)

"""
load "$pointsfRel", points
hide nonbonded, points
show nb_spheres, points
cmd.spectrum("b", "green_red", selection="points", minimum=0, maximum=0.7)

#select pockets, resn STP
stored.list=[]
cmd.iterate("(resn STP)","stored.list.append(resi)")    #read info about residues STP
#print stored.list
lastSTP=stored.list[-1] #get the index of the last residu
hide lines, resn STP

# sas points
cmd.select("rest", "resn STP and resi 0")
cmd.set("sphere_scale","0.3","rest")

# pockets (old)
#for my_index in range(1,int(lastSTP)+1): cmd.select("pocket"+str(my_index), "resn STP and resi "+str(my_index))
#for my_index in range(1,int(lastSTP)+1): cmd.show("spheres","pocket"+str(my_index))
#for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_scale","0.4","pocket"+str(my_index))
#for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_transparency","0.1","pocket"+str(my_index))
"""

    }

    void writeLabeledPoints(String fname, List<LabeledPoint> labeledPoints) {
        Writer pdb = Futils.getGzipWriter(fname)
        int i = 0
        for (LabeledPoint lp : labeledPoints) {
            double beta = lp.score
            Atom p = lp.point
            def lab = "STP"
            pdb.printf "HETATM%5d H    %3s 1  %2d    %8.3f%8.3f%8.3f  0.50%6.3f\n", i, lab, lp.pocket, p.x, p.y, p.z, beta
            i++
        }
        pdb.close()
    }

//===========================================================================================================//

    static String pyColor(Color c) {
        sprintf "[%5.3f,%5.3f,%5.3f]", c.red/255, c.green/255, c.blue/255
    }
    
}
