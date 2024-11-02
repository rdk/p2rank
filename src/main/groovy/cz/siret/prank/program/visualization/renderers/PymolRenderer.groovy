package cz.siret.prank.program.visualization.renderers

import cz.siret.prank.domain.*
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.visualization.PredictionVisualizer
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.awt.*
import java.util.List

import static cz.siret.prank.utils.Futils.writeFile

/**
 * Generates PyMol visualization of Pocket predictions.
 */
@Slf4j
@CompileStatic
class PymolRenderer implements Parametrized {

    String outdir

    PymolRenderer(String outputDir) {
        this.outdir = outputDir
    }

    static String pyColor(Color c) {
        sprintf "[%5.3f,%5.3f,%5.3f]", c.red/255, c.green/255, c.blue/255
    }

    void renderPredictions(Dataset.Item item, PredictionPair pair, String proteinFile, String pointsFileRelative) {

        String label = item.label
        String pmlf = "$outdir/${label}_pymol.pml"

        writeFile(pmlf, renderMainScript(proteinFile, pointsFileRelative, pair))

    }

    private String renderMainScript(String proteinFile, String pointsFileRelative, PredictionPair pair) {
// language=python
"""from pymol import cmd,stored

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

color white, elem c
color bluewhite, protein
#show_as cartoon, protein
show surface, protein
#set transparency, 0.15

show sticks, ligands
set stick_color, magenta


${renderLigands(pair.holoProtein)}

# SAS points

load "$pointsFileRelative", points
hide nonbonded, points
show nb_spheres, points
set sphere_scale, 0.2, points
cmd.spectrum("b", "green_red", selection="points", minimum=0, maximum=0.7)


stored.list=[]
cmd.iterate("(resn STP)","stored.list.append(resi)")    # read info about residues STP
lastSTP=stored.list[-1] # get the index of the last residue
hide lines, resn STP

cmd.select("rest", "resn STP and resi 0")

for my_index in range(1,int(lastSTP)+1): cmd.select("pocket"+str(my_index), "resn STP and resi "+str(my_index))
for my_index in range(1,int(lastSTP)+1): cmd.show("spheres","pocket"+str(my_index))
for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_scale","0.4","pocket"+str(my_index))
for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_transparency","0.1","pocket"+str(my_index))

${colorExposedAtoms(pair)}

${colorPocketSurfaces(pair)}   

deselect

orient
"""
    }

    private String colorPocketSurfaces(PredictionPair pair) {
        StringBuilder res = new StringBuilder()

        int nPockets = pair.prediction.reorderedPockets.size()
        List<Color> colors = PredictionVisualizer.generatePocketColors(nPockets)

        int i = 1
        pair.prediction.reorderedPockets.each { Pocket pocket ->
            String ids = pocket.surfaceAtoms.indexes.join(",")
            String name = "surf_pocket$i"
            String ncol = "pcol$i"

            res << "set_color $ncol = " + pyColor(colors[i-1]) + "\n"
            res << "select $name, protein and id [$ids] \n"
            res << "set surface_color,  $ncol, $name \n"
            i++
        }

        return res.toString()
    }

    private String colorExposedAtoms(PredictionPair pair) {
        // return pair.prediction.protein.exposedAtoms.list.collect { "set surface_color, grey30, id $it.PDBserial \n set sphere_color, grey30, id $it.PDBserial" }.join("\n")

        return ""
    }

    private String renderLigands(Protein protein) {

        if (!params.vis_highlight_ligands) {
            return "" // keep ligands rendered as purple sticks
        }

        // or highlight them: render as red balls

"""  
# relevant ligands
${renderLigands("ligands_relevant", "violet", protein.relevantLigands)}
                  
# ignored ligands
${renderLigands("ligands_ignored", "lightorange", protein.allIgnoredLigands)}
"""
    }

    private String renderLigands(String label, String color, List<Ligand> ligands) {

        Atoms ligandAtoms = Atoms.join(ligands*.atoms)

        if (ligandAtoms.empty) return ""

        List<String> ligandAtomIds = ligandAtoms.collect {it.PDBserial.toString() }
        String idsOrList = ligandAtomIds.collect {"id $it" }.join(" or ")

"""
select $label, $idsOrList
show spheres, $label
color $color, $label
"""
    }

/* random notes:

#set ray_shadow, 0
#set depth_cue, 0
#set ray_trace_fog, 0
//#set antialias, 2
set bg_rgb_top, [10,10,10]
set bg_rgb_bottom, [36,36,85]


#create protein, fprotein and polymer
#delete fprotein

#color bluewhite, fprotein
#remove solvent
#set stick_color, magenta
#hide lines
#show sticks

#set sphere_scale, 0.33
#show_as sticks, ligands

#show spheres, ligand

#select pockets, resn STP
#print stored.list

#show spheres, resn STP

#for my_index in range(2,int(lastSTP)+2): cmd.color(my_index,"pocket"+str(my_index))

#load $pointsf0RelName, points0
#hide nonbonded, points0
#show nb_spheres, points0
#cmd.spectrum("b", "yellow_blue", selection="points0", minimum=0.3, maximum=1)

#set ray_trace_mode, 1

// predefined gradients:  http://kpwu.wordpress.com/2007/11/27/pymol-example-coloring-surface-by-b-factor/
// http://cupnet.net/pdb_format/
// http://www.pymolwiki.org/index.php/Colorama
*/

}
