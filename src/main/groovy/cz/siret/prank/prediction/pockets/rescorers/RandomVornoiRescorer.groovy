package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.params.Parametrized
import org.biojava.nbio.structure.Atom

class RandomVornoiRescorer extends PocketRescorer implements Parametrized  {

    double accuracy = 0.7

    RandomVornoiRescorer(double accuracy) {
        this.accuracy = accuracy
    }

    @Override
    void rescorePockets(Prediction prediction, ProcessedItemContext context) {

        prediction.pockets.each { Pocket pocket ->

            int positiveVC = pocket.vornoiCenters.list.collect { Atom vc ->

                double closestLigandDistance = ligandAtoms.dist(vc)

                boolean positive = (closestLigandDistance <= params.positive_point_ligand_distance)

                if (Math.random()>accuracy) {   // classifier error
                    positive = !positive
                }

                return (positive) ? 1 : 0

            }.sum()

            pocket.newScore = ((double)positiveVC) / pocket.vornoiCenters.count
        }
    }

}
