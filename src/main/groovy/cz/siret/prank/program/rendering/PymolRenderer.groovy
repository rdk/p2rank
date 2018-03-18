package cz.siret.prank.program.rendering

import cz.siret.prank.domain.labeling.BinaryResidueLabeling
import cz.siret.prank.domain.labeling.LabeledResidue
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PDBUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Structure

import java.awt.*

/**
 *
 */
@Slf4j
@CompileStatic
class PymolRenderer implements Parametrized {

    String outdir
    RenderingModel model

    PymolRenderer(String outdir, RenderingModel model) {
        this.outdir = outdir
        this.model = model
    }

    void render() {

        String label = model.label

        String pmlFile = "$outdir/${label}.pml"
        String dataDir = "$outdir/data"

        Futils.mkdirs(dataDir)

        //String pointsf = "$pointsDir/${label}_points.pdb.gz"
        //String pointsfRelName = "data/${label}_points.pdb.gz"

        String proteinFileAbs = Futils.absPath(model.proteinFile)
        String proteinFile = proteinFileAbs

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

        Futils.writeFile(pmlFile, renderMain(proteinFile))
    }


    private String renderMain(String proteinFile) {
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

load $proteinFile, protein
create ligands, protein and organic
select xlig, protein and organic
delete xlig

hide everything, all

color white, elem c
color bluewhite, protein
show surface, protein

show sticks, ligands
set stick_color, magenta

${renderResidueColoring()}

${renderLabaledPoints()}

deselect

orient
"""
    }


    private String renderResidueColoring() {
        renderBinaryResidueColoring(model.binaryLabeling)
    }

    private String renderBinaryResidueColoring(BinaryResidueLabeling labeling) {
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

    private String renderLabaledPoints() {
        if (model.labeledPoints == null) return ""

        """
#load \$pointsfRelName, points
#hide nonbonded, points
#show nb_spheres, points
#cmd.spectrum("b", "green_red", selection="points", minimum=0, maximum=0.7)
#
##select pockets, resn STP
#stored.list=[]
#cmd.iterate("(resn STP)","stored.list.append(resi)")    #read info about residues STP
##print stored.list
#lastSTP=stored.list[-1] #get the index of the last residu
#hide lines, resn STP
#
##show spheres, resn STP
#cmd.select("rest", "resn STP and resi 0")

#for my_index in range(1,int(lastSTP)+1): cmd.select("pocket"+str(my_index), "resn STP and resi "+str(my_index))
##for my_index in range(2,int(lastSTP)+2): cmd.color(my_index,"pocket"+str(my_index))
#for my_index in range(1,int(lastSTP)+1): cmd.show("spheres","pocket"+str(my_index))
#for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_scale","0.4","pocket"+str(my_index))
#for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_transparency","0.1","pocket"+str(my_index))
"""

    }

//===========================================================================================================//

    static String pyColor(Color c) {
        sprintf "[%5.3f,%5.3f,%5.3f]", c.red/255, c.green/255, c.blue/255
    }

    
}
