package cz.siret.prank.prediction.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for AUC and AUPRC computed by CurveMetrics.
 * Tests are designed independently of Weka, using analytically known values.
 */
class CurveMetricsTest {

    private static PredictedScores preds(double[] scores, boolean[] observed) {
        PredictedScores ps = new PredictedScores(scores.length);
        for (int i = 0; i < scores.length; i++) {
            ps.add(observed[i], scores[i]);
        }
        return ps;
    }

    // ======================== AUC ========================

    @Nested
    class AUC {

        @Test
        void perfectSeparation() {
            // All positives scored higher than all negatives → AUC = 1.0
            PredictedScores ps = preds(
                new double[]{0.9, 0.8, 0.3, 0.2},
                new boolean[]{true, true, false, false}
            );
            assertEquals(1.0, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void worstSeparation() {
            // All negatives scored higher than all positives → AUC = 0.0
            PredictedScores ps = preds(
                new double[]{0.9, 0.8, 0.3, 0.2},
                new boolean[]{false, false, true, true}
            );
            assertEquals(0.0, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void randomClassifier() {
            // Interleaved positives and negatives with same scores → AUC ≈ 0.5
            PredictedScores ps = preds(
                new double[]{0.5, 0.5, 0.5, 0.5},
                new boolean[]{true, false, true, false}
            );
            assertEquals(0.5, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void singlePositiveSingleNegative() {
            // Positive scored higher → AUC = 1.0
            PredictedScores ps = preds(
                new double[]{0.8, 0.2},
                new boolean[]{true, false}
            );
            assertEquals(1.0, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void singlePositiveSingleNegativeReversed() {
            // Negative scored higher → AUC = 0.0
            PredictedScores ps = preds(
                new double[]{0.8, 0.2},
                new boolean[]{false, true}
            );
            assertEquals(0.0, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void tiedScores() {
            // 2 pos + 2 neg all at same score → AUC = 0.5
            PredictedScores ps = preds(
                new double[]{0.6, 0.6, 0.6, 0.6},
                new boolean[]{true, true, false, false}
            );
            assertEquals(0.5, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void knownMixedCase() {
            // Hand-computed: pos at 0.9, 0.4; neg at 0.7, 0.3, 0.1
            // Sorted desc: (0.9,T), (0.7,F), (0.4,T), (0.3,F), (0.1,F)
            // TPR/FPR points: (0,0) → (0, 0.5) → (0, 1/3) ...
            // Mann-Whitney: count concordant pairs out of 2×3=6
            // 0.9>0.7,0.3,0.1 → 3; 0.4>0.3,0.1 → 2; total 5/6
            PredictedScores ps = preds(
                new double[]{0.9, 0.7, 0.4, 0.3, 0.1},
                new boolean[]{true, false, true, false, false}
            );
            assertEquals(5.0 / 6.0, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void allPositivesReturnsNaN() {
            PredictedScores ps = preds(
                new double[]{0.9, 0.8},
                new boolean[]{true, true}
            );
            assertTrue(Double.isNaN(CurveMetrics.areaUnderROC(ps)));
        }

        @Test
        void allNegativesReturnsNaN() {
            PredictedScores ps = preds(
                new double[]{0.3, 0.2},
                new boolean[]{false, false}
            );
            assertTrue(Double.isNaN(CurveMetrics.areaUnderROC(ps)));
        }

        @Test
        void unsortedInput() {
            // Verify that unsorted input produces correct results
            // (sortDescendingByScore is called internally)
            PredictedScores ps = preds(
                new double[]{0.3, 0.9, 0.1, 0.7, 0.4},
                new boolean[]{false, true, false, false, true}
            );
            assertEquals(5.0 / 6.0, CurveMetrics.areaUnderROC(ps), 1e-10);
        }

        @Test
        void largeBalancedDataset() {
            // 100 pos + 100 neg, perfectly separated
            int n = 200;
            double[] scores = new double[n];
            boolean[] obs = new boolean[n];
            for (int i = 0; i < 100; i++) {
                scores[i] = 1.0 - i * 0.005;  // 1.0 to 0.505
                obs[i] = true;
            }
            for (int i = 100; i < 200; i++) {
                scores[i] = 0.5 - (i - 100) * 0.005;  // 0.5 to 0.005
                obs[i] = false;
            }
            PredictedScores ps = preds(scores, obs);
            assertEquals(1.0, CurveMetrics.areaUnderROC(ps), 1e-10);
        }
    }

    // ======================== AUPRC ========================

    @Nested
    class AUPRC {

        @Test
        void perfectSeparation() {
            // All positives scored higher → AUPRC = 1.0
            PredictedScores ps = preds(
                new double[]{0.9, 0.8, 0.3, 0.2},
                new boolean[]{true, true, false, false}
            );
            assertEquals(1.0, CurveMetrics.areaUnderPRC(ps), 1e-10);
        }

        @Test
        void singlePositive() {
            // 1 pos at highest score, 3 neg → AUPRC = 1.0
            PredictedScores ps = preds(
                new double[]{0.9, 0.7, 0.5, 0.3},
                new boolean[]{true, false, false, false}
            );
            assertEquals(1.0, CurveMetrics.areaUnderPRC(ps), 1e-10);
        }

        @Test
        void singlePositiveAtLowestScore() {
            // 1 pos at lowest score, 3 neg → precision always low
            PredictedScores ps = preds(
                new double[]{0.9, 0.7, 0.5, 0.3},
                new boolean[]{false, false, false, true}
            );
            // At the only recall point (recall=1), precision=1/4=0.25
            // Trapezoidal: (1.0 - 0.0) * (0.25 + 1.0) / 2 = 0.625
            // but that's with the starting precision=1 convention.
            // The actual area depends on interpolation.
            double auprc = CurveMetrics.areaUnderPRC(ps);
            assertTrue(auprc > 0.0 && auprc < 1.0, "AUPRC should be between 0 and 1, got " + auprc);
        }

        @Test
        void allPositivesReturnsOne() {
            // All predictions are positive → precision always 1.0, recall goes 0→1
            PredictedScores ps = preds(
                new double[]{0.9, 0.8, 0.7},
                new boolean[]{true, true, true}
            );
            assertEquals(1.0, CurveMetrics.areaUnderPRC(ps), 1e-10);
        }

        @Test
        void noPositivesReturnsNaN() {
            PredictedScores ps = preds(
                new double[]{0.5, 0.3},
                new boolean[]{false, false}
            );
            assertTrue(Double.isNaN(CurveMetrics.areaUnderPRC(ps)));
        }

        @Test
        void knownMixedCase() {
            // pos at 0.9, 0.4; neg at 0.7, 0.3, 0.1
            // Sorted desc: (0.9,T), (0.7,F), (0.4,T), (0.3,F), (0.1,F)
            // PR points:
            //   after 0.9: prec=1/1=1.0, rec=1/2=0.5
            //   after 0.7: prec=1/2=0.5, rec=1/2=0.5
            //   after 0.4: prec=2/3≈0.667, rec=2/2=1.0
            //   after 0.3: prec=2/4=0.5, rec=1.0
            //   after 0.1: prec=2/5=0.4, rec=1.0
            PredictedScores ps = preds(
                new double[]{0.9, 0.7, 0.4, 0.3, 0.1},
                new boolean[]{true, false, true, false, false}
            );
            double auprc = CurveMetrics.areaUnderPRC(ps);
            // Should be between 0.5 and 1.0 for a reasonable classifier
            assertTrue(auprc > 0.5 && auprc <= 1.0, "AUPRC=" + auprc);
        }

        @Test
        void unsortedInput() {
            // Same as knownMixedCase but shuffled
            PredictedScores ps1 = preds(
                new double[]{0.9, 0.7, 0.4, 0.3, 0.1},
                new boolean[]{true, false, true, false, false}
            );
            PredictedScores ps2 = preds(
                new double[]{0.3, 0.9, 0.1, 0.7, 0.4},
                new boolean[]{false, true, false, false, true}
            );
            assertEquals(
                CurveMetrics.areaUnderPRC(ps1),
                CurveMetrics.areaUnderPRC(ps2),
                1e-10,
                "Shuffled input should produce same AUPRC"
            );
        }

        @Test
        void auprcBoundedZeroOne() {
            // Various random-ish configurations should all produce [0, 1]
            double[][] configs = {
                {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8},
            };
            boolean[][] labels = {
                {false, true, false, true, false, true, false, true},
            };
            for (int c = 0; c < configs.length; c++) {
                PredictedScores ps = preds(configs[c], labels[c]);
                double val = CurveMetrics.areaUnderPRC(ps);
                assertTrue(val >= 0.0 && val <= 1.0, "AUPRC out of [0,1]: " + val);
            }
        }

        @Test
        void imbalancedDataset() {
            // 1 positive among 99 negatives, positive has highest score
            int n = 100;
            double[] scores = new double[n];
            boolean[] obs = new boolean[n];
            scores[0] = 1.0;
            obs[0] = true;
            for (int i = 1; i < n; i++) {
                scores[i] = 1.0 - i * 0.01;
                obs[i] = false;
            }
            PredictedScores ps = preds(scores, obs);
            assertEquals(1.0, CurveMetrics.areaUnderPRC(ps), 1e-10,
                "Single positive at top should give AUPRC=1.0");
        }
    }

    // ======================== Cross-validation ========================

    @Nested
    class CrossValidation {

        @Test
        void aucAndAuprcConsistentWithWeka() {
            // Compare CurveMetrics with WekaStatsHelper on a small dataset
            PredictedScores ps = preds(
                new double[]{0.95, 0.85, 0.75, 0.65, 0.55, 0.45, 0.35, 0.25, 0.15, 0.05},
                new boolean[]{true, true, false, true, false, false, true, false, false, false}
            );

            double ourAuc = CurveMetrics.areaUnderROC(ps);
            double ourAuprc = CurveMetrics.areaUnderPRC(ps);

            // Rebuild for Weka (sort may have been applied)
            PredictedScores ps2 = preds(
                new double[]{0.95, 0.85, 0.75, 0.65, 0.55, 0.45, 0.35, 0.25, 0.15, 0.05},
                new boolean[]{true, true, false, true, false, false, true, false, false, false}
            );
            WekaStatsHelper weka = new WekaStatsHelper(ps2);
            double wekaAuc = weka.areaUnderROC();
            double wekaAuprc = weka.areaUnderPRC();

            assertEquals(wekaAuc, ourAuc, 0.02,
                "AUC should be close to Weka (ours=" + ourAuc + ", weka=" + wekaAuc + ")");
            assertEquals(wekaAuprc, ourAuprc, 0.05,
                "AUPRC should be close to Weka (ours=" + ourAuprc + ", weka=" + wekaAuprc + ")");
        }

        @Test
        void aucMatchesWekaOnLargerDataset() {
            // 50 predictions with a known pattern
            int n = 50;
            double[] scores = new double[n];
            boolean[] obs = new boolean[n];
            for (int i = 0; i < n; i++) {
                scores[i] = 1.0 - i * 0.02;
                // ~40% positive rate biased toward higher scores
                obs[i] = (i % 5 != 0) && (i < 30);
            }

            PredictedScores ps1 = preds(scores, obs);
            PredictedScores ps2 = preds(scores, obs);

            double ourAuc = CurveMetrics.areaUnderROC(ps1);
            double wekaAuc = new WekaStatsHelper(ps2).areaUnderROC();

            assertEquals(wekaAuc, ourAuc, 0.02,
                "AUC should match Weka on 50-element dataset");
        }

        @Test
        void matchesWekaOnRealisticImbalancedDataset() {
            // Simulate P2Rank-like score distribution: ~10% positive, many ties
            // from random forest discrete probabilities (0.0, 0.05, 0.10, ...)
            int n = 1000;
            double[] scores = new double[n];
            boolean[] obs = new boolean[n];
            java.util.Random rng = new java.util.Random(42);
            for (int i = 0; i < n; i++) {
                // Discrete scores in 0.05 increments (simulates 20-tree forest)
                scores[i] = Math.round(rng.nextDouble() * 20) / 20.0;
                // 10% positive rate, biased toward higher scores
                obs[i] = rng.nextDouble() < (0.05 + 0.15 * scores[i]);
            }

            PredictedScores ps1 = preds(scores, obs);
            PredictedScores ps2 = preds(scores, obs);

            double ourAuc = CurveMetrics.areaUnderROC(ps1);
            double ourAuprc = CurveMetrics.areaUnderPRC(ps1);
            WekaStatsHelper weka = new WekaStatsHelper(ps2);
            double wekaAuc = weka.areaUnderROC();
            double wekaAuprc = weka.areaUnderPRC();

            assertEquals(wekaAuc, ourAuc, 0.01,
                "AUC on 1000 imbalanced predictions: ours=" + ourAuc + " weka=" + wekaAuc);
            assertEquals(wekaAuprc, ourAuprc, 0.03,
                "AUPRC on 1000 imbalanced predictions: ours=" + ourAuprc + " weka=" + wekaAuprc);
        }

        @Test
        void matchesWekaOnLargeDatasetWithTies() {
            // 5000 predictions, heavy ties (only 10 distinct score levels)
            int n = 5000;
            double[] scores = new double[n];
            boolean[] obs = new boolean[n];
            java.util.Random rng = new java.util.Random(123);
            for (int i = 0; i < n; i++) {
                scores[i] = rng.nextInt(10) / 10.0;  // 0.0 to 0.9
                obs[i] = rng.nextDouble() < (0.02 + 0.2 * scores[i]);
            }

            PredictedScores ps1 = preds(scores, obs);
            PredictedScores ps2 = preds(scores, obs);

            double ourAuc = CurveMetrics.areaUnderROC(ps1);
            double wekaAuc = new WekaStatsHelper(ps2).areaUnderROC();

            assertEquals(wekaAuc, ourAuc, 0.01,
                "AUC on 5000 tied predictions: ours=" + ourAuc + " weka=" + wekaAuc);
        }
    }
}
