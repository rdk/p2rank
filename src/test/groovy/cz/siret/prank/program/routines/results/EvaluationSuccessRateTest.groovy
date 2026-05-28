package cz.siret.prank.program.routines.results

import cz.siret.prank.prediction.pockets.criteria.DCA
import cz.siret.prank.prediction.pockets.criteria.PocketCriteria
import cz.siret.prank.prediction.pockets.criteria.PocketCriterion
import cz.siret.prank.program.params.Params
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests calcSuccessRate logic with synthetic LigRow data.
 * Catches off-by-one errors in tolerance/top-N computation
 * that would silently inflate or deflate DCA metrics.
 */
@Isolated
@ResourceLock("Params")
class EvaluationSuccessRateTest {

    @BeforeAll
    static void initAll() {
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDownAll() {
        Params.INSTANCE = new Params()
    }

    private static Evaluation makeEvaluation(List<Evaluation.LigRow> rows) {
        def eval = new Evaluation()
        PocketCriterion dca4 = new DCA("DCA_4", 4.0d)
        eval.criteria = new PocketCriteria([dca4])
        eval.ligandRows.addAll(rows)
        eval.ligandCount = rows.size()
        eval.proteinCount = rows.collect { it.protName }.toSet().size()
        return eval
    }

    private static Evaluation.LigRow ligRow(int ligCount, int dcaRank) {
        def row = new Evaluation.LigRow()
        row.protName = "test"
        row.ligCount = ligCount
        row.ranks = [dcaRank]
        return row
    }

    @Test
    void allIdentifiedTopNplus0() {
        // 2 ligands in protein, both identified at ranks 1 and 2
        def eval = makeEvaluation([
                ligRow(2, 1),
                ligRow(2, 2)
        ])

        // top-(n+0): tolerance=0, topNplusK mode -> pockets within rank <= ligCount + 0 = 2
        double rate = eval.calcSuccessRate(0, 0)
        assertEquals(1.0, rate, 0.001, "both ligands identified within top-2")
    }

    @Test
    void oneIdentifiedOneNotInTopN() {
        // 1 ligand per protein, identified at rank 1 and rank 3
        def eval = makeEvaluation([
                ligRow(1, 1),
                ligRow(1, 3)
        ])

        // top-(n+0): tolerance=0 -> pocket must be at rank <= 1
        double rate0 = eval.calcSuccessRate(0, 0)
        assertEquals(0.5, rate0, 0.001, "only 1 of 2 within top-1")

        // top-(n+2): tolerance=2 -> pocket must be at rank <= 3
        double rate2 = eval.calcSuccessRate(0, 2)
        assertEquals(1.0, rate2, 0.001, "both within top-3")
    }

    @Test
    void unidentifiedLigandRankMinusOne() {
        // rank=-1 means not identified at all
        def eval = makeEvaluation([
                ligRow(1, -1),
                ligRow(1, 1)
        ])

        double rate = eval.calcSuccessRate(0, 0)
        assertEquals(0.5, rate, 0.001, "unidentified ligand (rank=-1) not counted")
    }

    @Test
    void emptyEvaluationReturnsZero() {
        def eval = makeEvaluation([])
        double rate = eval.calcSuccessRate(0, 0)
        assertEquals(0.0, rate, 0.001, "empty eval -> 0")
    }

    @Test
    void topNModeIgnoresLigCount() {
        // top-N mode (not top-N+K): tolerance is the absolute cutoff
        def eval = makeEvaluation([
                ligRow(5, 3),  // ligCount=5 should not matter in topN mode
                ligRow(5, 1)
        ])

        // topN=2: pocket must be at rank <= 2 regardless of ligCount
        double rate = eval.calcSuccessRateTopN(0, 2)
        assertEquals(0.5, rate, 0.001, "only rank=1 is within top-2 (rank=3 is out)")
    }

    @Test
    void toleranceBoundaryExact() {
        // rank exactly at the boundary
        def eval = makeEvaluation([
                ligRow(2, 2)  // ligCount=2, rank=2 -> boundary is 2+0=2
        ])

        double rate = eval.calcSuccessRate(0, 0)
        assertEquals(1.0, rate, 0.001, "rank=2 is exactly at boundary ligCount+tolerance=2")
    }

    @Test
    void toleranceBoundaryOneOver() {
        // rank one over the boundary
        def eval = makeEvaluation([
                ligRow(2, 3)  // ligCount=2, rank=3 -> boundary is 2+0=2, 3>2
        ])

        double rate = eval.calcSuccessRate(0, 0)
        assertEquals(0.0, rate, 0.001, "rank=3 exceeds boundary ligCount+tolerance=2")
    }
}
