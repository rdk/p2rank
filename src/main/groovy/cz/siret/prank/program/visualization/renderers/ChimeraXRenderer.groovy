package cz.siret.prank.program.visualization.renderers

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.utils.ColorUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.program.visualization.PredictionVisualizer.generatePocketColors
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Generates ChimeraX visualization of pocket predictions.
 */
@Slf4j
@CompileStatic
class ChimeraXRenderer {

    String bgColor = "#242455"
    String proteinColor = "#bdbdde"  // less purple: "#c1c0db"
    String ligandColor = "magenta"
    String transparency = "35"  // 0-100(full transparency)

//===========================================================================================================//

    String outdir

    ChimeraXRenderer(String outdir) {
        this.outdir = outdir
    }

    void renderPredictions(Dataset.Item item, PredictionPair pair, String proteinFile, String pointsFileRelative) {

        String label = item.label
        String file = "$outdir/${label}_chimerax.cxc"

        writeFile(file, renderMainScript(proteinFile, pointsFileRelative, pair))

    }

//===========================================================================================================//

    private String renderMainScript(String proteinFile, String pointsFileRelative, PredictionPair pair) {
"""open $proteinFile
surf
hide solvent
color protein $proteinColor
color ligand $ligandColor


${colorPockets(pair)}

open $pointsFileRelative
color by bfactor #2 palette lime:red  range 0,0.7


set bgcolor $bgColor
transparency $transparency
graphics silhouettes true
lighting full
view
"""
    }

    private StringBuilder colorPockets(PredictionPair pair) {
        StringBuilder res = new StringBuilder()

        List<Pocket> pockets = pair.prediction.reorderedPockets
        int n = pockets.size()
        List<String> colors = generatePocketColors(n).collect { ColorUtils.colorToHex(it) }


        for (int i = 0; i != n; i++) {
            res << "color name color_pocket${i + 1} ${colors[i]}\n"
        }
        res << "\n"


        for (int i = 0; i != n; i++) {
            Pocket pocket = pockets[i]
            res << "name pocket${i + 1}_atoms " << pocket.surfaceAtoms.collect { "@@serial_number=${it.PDBserial}" }.join(" ") << "\n"
            res << "color pocket${i + 1}_atoms color_pocket${i + 1}\n"
            res << "\n"
        }

        return res
    }

}
