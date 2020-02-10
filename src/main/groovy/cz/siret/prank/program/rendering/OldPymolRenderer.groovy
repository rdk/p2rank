package cz.siret.prank.program.rendering

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.utils.ColorUtils
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.zeroturnaround.zip.NameMapper
import org.zeroturnaround.zip.ZipUtil

import java.awt.*
import java.util.List

/**
 * Generates PyMol visualizations.
 *
 * Used for visualization of binding site predictions.
 */
@Slf4j
@CompileStatic
class OldPymolRenderer implements Parametrized {

    String outdir

    OldPymolRenderer(String outputDir) {
        this.outdir = outputDir
    }

    static String pyColor(Color c) {
        sprintf "[%5.3f,%5.3f,%5.3f]", c.red/255, c.green/255, c.blue/255
    }

    private String colorPocketSurfaces(PredictionPair pair) {
        StringBuilder res = new StringBuilder()

        int N = pair.prediction.reorderedPockets.size()

        List<Color> colors = ColorUtils.createSpectrum(N, 0.6d, 0.6d, 1.20d)

        int i = 1;
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

    void visualizeHistograms(Dataset.Item item, ModelBasedRescorer rescorer, PredictionPair pair) {

        String label = item.label

        String pmlf = "$outdir/${label}.pml"
        String pointsDir = "$outdir/data"

        Futils.mkdirs(pointsDir)

        String pointsf = "$pointsDir/${label}_points.pdb.gz"
        String pointsfRelName = "data/${label}_points.pdb.gz"
        String pointsf0 = "$pointsDir/${label}_points0.pdb"
        String pointsf0RelName = "data/${label}_points0.pdb"

        String proteinf = Futils.absPath(item.proteinFile)
        String proteinfabs = proteinf;
        if (params.vis_copy_proteins) {
            String name = Futils.shortName(proteinf)
            String newf = "$pointsDir/$name"
            String newfrel = "data/$name"

            log.info "copying [$proteinf] to [$newf]"
            Futils.copy(proteinf, newf)

            proteinf = newfrel
            proteinfabs = newf;
        }

        String colorPocketSurfaces = colorPocketSurfaces(pair)

        String colorExposedAtoms = ""
        // XXX
        //colorExposedAtoms = pair.prediction.protein.exposedAtoms.list.collect { "set surface_color, grey30, id $it.PDBserial \n set sphere_color, grey30, id $it.PDBserial" }.join("\n")


        Futils.writeFile(pmlf,
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

load "$proteinf", protein
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

load "$pointsfRelName", points
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

$colorExposedAtoms

$colorPocketSurfaces

deselect

orient
""")
         // predefined gradients:  http://kpwu.wordpress.com/2007/11/27/pymol-example-coloring-surface-by-b-factor/
        // http://cupnet.net/pdb_format/
        // http://www.pymolwiki.org/index.php/Colorama

        Writer pdb = Futils.getGzipWriter(pointsf)
        int i = 0
        for (LabeledPoint lp : rescorer.labeledPoints) {
            double beta = lp.hist[1]
            Atom p = lp.point
                      //HETATM   73 H    POC 1   0      13.842  20.130  -4.420  0.50  0.50
   //         pdb.printf "HETATM%5d H    POC 1   0    %8.3f%8.3f%8.3f  0.50%6.3f\n", i, p.x, p.y, p.z, beta
            def lab = "STP"
//            if (lp.pocket!=0)
//                lab = "POC"

            pdb.printf "HETATM%5d H    %3s 1  %2d    %8.3f%8.3f%8.3f  0.50%6.3f\n", i, lab, lp.pocket, p.x, p.y, p.z, beta
            i++
        }
        pdb.close()

//        double q = -0.03
//
//        pdb = FileUtils.getWriter(pointsf0)
//        i = 0
//        for (LabeledPoint lp : rescorer.labeledPoints) {
//            double beta = lp.hist[0]
//            Atom p = lp.point
//
//            log.info "POINT:  ${lp.hist[0]}   ${lp.hist[1]}   ${lp.hist[0]+lp.hist[1]}"
//
//            //HETATM   73 H    POC 1   0      13.842  20.130  -4.420  0.50  0.50
//            pdb.printf "HETATM%5d H    POC 1   0    %8.3f%8.3f%8.3f  0.50%6.3f\n", i, p.x+q, p.y+q, p.z+q, beta
//            i++
//        }
//        pdb.close()


        if (params.zip_visualizations) {
            List<File> fileList = [new File(pmlf), new File(pointsf)]
            if (params.vis_copy_proteins) {
                fileList.add(new File(proteinfabs));
            }
            File zipFile = new File("$outdir/${label}_visualization.zip");
            NameMapper mapper = { String fileName ->
                return fileName.endsWith(".pml") ? fileName : "data/".concat(fileName);
            }
            ZipUtil.packEntries(fileList.toArray(new File[0]), zipFile, mapper);
            fileList.forEach({File f->f.delete()})
        }
    }


    /*
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
 */
}
