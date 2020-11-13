package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.table.PropertyTable
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

/**
 * implementation of PLB index from
 * Soga et al. 2007 "Use of Amino Acid Composition to Predict Ligand-Binding Sites"
 */
@Slf4j
@CompileStatic
class PLBIndexRescorer extends PocketRescorer {

    static final PropertyTable aaPropensitiesTable   = PropertyTable.parse(Futils.readResource("/tables/aa-propensities.csv"))

    @Override
    void rescorePockets(Prediction prediction, ProcessedItemContext context) {

        List<ExtPocket> extPockets = new ArrayList<>()

        double M = prediction.pocketCount

        for (Pocket pocket : prediction.pockets) {
            double plbi = 0

            if (params.plb_rescorer_atomic) {
                for (Atom a : pocket.surfaceAtoms) {
                    String aaCode = PdbUtils.getCorrectedAtomResidueCode(a)
                    Double prop = aaPropensitiesTable.getValue(aaCode, "RAx")
                    if (prop!=null) {
                        plbi += prop
                    }
                }
            } else {
                // according to article
                for (Group g : pocket.surfaceAtoms.distinctGroupsSorted) {
                    String aaCode = PdbUtils.getCorrectedResidueCode(g)
                    Double prop = aaPropensitiesTable.getValue(aaCode, "RAx")
                    if (prop!=null) {
                        plbi += prop
                    }
                }
            }


            ExtPocket extPocket = new ExtPocket(pocket: pocket, PLBi: plbi)
            extPocket.PLBi = plbi
            extPockets.add(extPocket)
        }

        double mu = ((double)(extPockets*.PLBi).sum(0)) / M

        double sig = 0
        for (ExtPocket p : extPockets) {
            double x = p.PLBi - mu
            sig += x*x
        }
        sig = Math.sqrt((double)sig/M)

        for (ExtPocket p : extPockets) {
            p.ZPLB = (p.PLBi - mu) / sig
            p.pocket.newScore = p.ZPLB

            log.info Sutils.toStr(p)
        }

    }

    private static class ExtPocket {
        Pocket pocket
        double PLBi
        double ZPLB
    }

}
