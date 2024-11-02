package cz.siret.prank.program.visualization

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.visualization.renderers.ChimeraXRenderer
import cz.siret.prank.program.visualization.renderers.PymolRenderer
import cz.siret.prank.utils.ColorUtils
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import java.awt.*
import java.util.List

/**
 * Visualizes pocket predictions.
 */
@Slf4j
@CompileStatic
class PredictionVisualizer implements Parametrized {

    String outdir

    PredictionVisualizer(String outdir) {
        this.outdir = visualizationsDir(outdir)
    }

//===========================================================================================================//

    static String visualizationsDir(String outdir) {
        return "$outdir/visualizations"
    }

    static List<Color> generatePocketColors(int n) {
        return ColorUtils.createSpectrum(n, 0.6d, 0.6d, 1.20d)
    }

//===========================================================================================================//

    static void writeLabeledPointsPdb(String pointsf, List<LabeledPoint> labeledPoints) {
        Writer pdb = Futils.getGzipWriter(pointsf)
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

//    void conditionallyZipVisualizations(List<File> fileList, String label) {
//        if (params.zip_visualizations) {
//            List<File> fileList = [new File(pmlf), new File(pointsf)]
//            if (params.vis_copy_proteins) {
//                fileList.add(new File(proteinfAbs))
//            }
//            File zipFile = new File("$outdir/${label}_visualization.zip")
//            NameMapper mapper = { String fileName ->
//                return fileName.endsWith(".pml") ? fileName : "data/".concat(fileName)
//            }
//            ZipUtil.packEntries(fileList.toArray(new File[0]) as File[], zipFile, mapper)
//            fileList.forEach({ File f -> f.delete() })
//        }
//    }

//===========================================================================================================//

    void generateVisualizations(Dataset.Item item, ModelBasedRescorer rescorer, PredictionPair pair) {

        String label = item.label

        //String pmlf = "$outdir/${label}.pml"

        String dataDir = Futils.mkdirs("$outdir/data")


        // SAS points

        String pointsFileRelative = "data/${label}_points.pdb.gz"
        String pointsFile = "$outdir/$pointsFileRelative"

        writeLabeledPointsPdb(pointsFile, rescorer.labeledPoints)

        // protein files

        String proteinFile = Futils.absPath(item.proteinFile)
        String proteinFileAbs = proteinFile
        if (params.vis_copy_proteins) {
            String name = Futils.shortName(proteinFile)
            String newf = "$dataDir/$name"
            String newfrel = "data/$name"

            log.info "copying [$proteinFile] to [$newf]"
            Futils.copy(proteinFile, newf)

            proteinFile = newfrel
            proteinFileAbs = newf
        }

        // renderers

        if ('pymol' in params.vis_renderers) {
            try {
                new PymolRenderer(outdir).renderPredictions(item, pair, proteinFile, pointsFileRelative)
            } catch (Exception e) {
                log.error("Error rendering PyMol visualization for $label", e)
            }
        }
        if ('chimerax' in params.vis_renderers) {
            try {
                new ChimeraXRenderer(outdir).renderPredictions(item, pair, proteinFile, pointsFileRelative)
            } catch (Exception e) {
                log.error("Error rendering ChimeraX visualization for $label", e)
            }
        }

    }

}
