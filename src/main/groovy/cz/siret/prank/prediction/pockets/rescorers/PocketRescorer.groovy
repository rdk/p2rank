package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Cutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
abstract class PocketRescorer implements Parametrized {

    /** optional - for evaluation statistics */
    Protein ligandedProtein
    Atoms ligandAtoms = null

    boolean collectStats = false

    void collectStatsForProtein(Protein liganatedProtein) {
        collectStats = true
        this.ligandedProtein = liganatedProtein
        if (liganatedProtein != null) {
            ligandAtoms = liganatedProtein.allRelevantLigandAtoms
        }
    }

    /**
     * should set pocket.newScore on all pockets
     * and optionally store information to pocket.auxInfo
     */
    abstract void rescorePockets(Prediction prediction, ProcessedItemContext context);

    /**
     * reorder pockets or make new pocket predictions
     */
    void reorderPockets(Prediction prediction, ProcessedItemContext context) {

        rescorePockets(prediction, context)

        if (!params.predictions) {
            prediction.reorderedPockets = new ArrayList<>(prediction.pockets)
            prediction.reorderedPockets = prediction.reorderedPockets.sort {
                Pocket a, Pocket b -> b.newScore <=> a.newScore
            } // descending
        }

        if (params.predictions) {
            setRanks(prediction)
        }

        setNewRanks(prediction)
    }

    private void setNewRanks(Prediction prediction) {
        int i = 1
        for (Pocket pocket : prediction.reorderedPockets) {
            pocket.newRank = i++
        }
    }

    private void setRanks(Prediction prediction) {
        int i = 1
        for (Pocket pocket : prediction.pockets) {
            pocket.rank = i++
        }
    }

    /**
     *
     * @param n reorder only first #true pockets + n
     */
    void reorderFirstNPockets(Prediction prediction, ProcessedItemContext context, int n) {

        rescorePockets(prediction, context)

        log.info "reordering first $n of $prediction.pocketCount pockets"

        ArrayList<Pocket> head = new ArrayList<>(Cutils.head(n, prediction.pockets))
        ArrayList<Pocket> tail = new ArrayList<>(Cutils.tail(n, prediction.pockets))

        reorder(head)

        prediction.pockets = head + tail

        setNewRanks(prediction)
    }

    void reorder(ArrayList<Pocket> pockets) {
        pockets.sort(new Comparator<Pocket>() {
            int compare(Pocket o1, Pocket o2) {
                return Double.compare(o2.newScore, o1.newScore)
            }
        })
    }

}