package cz.siret.prank.geom.cdksurface

import cz.cuni.cusbg.surface.FasterNumericalSurface;
import cz.siret.prank.domain.Protein
import cz.siret.prank.utils.Bench
import cz.siret.prank.utils.CdkUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openscience.cdk.geometry.surface.NumericalSurface
import org.openscience.cdk.interfaces.IAtomContainer

/**
 *
 */
@Slf4j
@CompileStatic
class FasterNumericalSurfaceTest {


    static String dataDir = 'src/test/resources/data'

    /**
     * Test for protein loader as well
     */
    @Test
    @Disabled
    void benchSurface1() {

        Protein protein = Protein.load("$dataDir/2src.pdb")


        IAtomContainer cdkAtoms = CdkUtils.toAtomContainer(protein.proteinAtoms)


        double solventRadius = 1.6
        int outerReps = 5
        int reps = 16

        for(int tesslevel in 2..4) {
            double oldTime = Bench.timeitLogWithHeatup("OLD tess:" + tesslevel, outerReps, {
                reps.times {
                    NumericalSurface numericalSurface = new NumericalSurface(cdkAtoms, solventRadius, tesslevel)
                    numericalSurface.getAllSurfacePoints()
                }
            })

            double newTime = Bench.timeitLogWithHeatup("NEW tess:" + tesslevel, outerReps, {
                reps.times {
                    FasterNumericalSurface numericalSurface = new FasterNumericalSurface(cdkAtoms, solventRadius, tesslevel)
                    numericalSurface.getAllSurfacePoints()
                }
            })

            double timeMult = oldTime / newTime
            log.info("SPEEDUP: {}", Math.round(timeMult * 1000)/1000 )
        }

    }

    /*
[INFO] Bench -     OLD tess:2 (heatup): 1390
[INFO] Bench -     OLD tess:2 (run 1): 1214
[INFO] Bench -     OLD tess:2 (run 2): 1237
[INFO] Bench -     OLD tess:2 (run 3): 1237
[INFO] Bench -     OLD tess:2 (run 4): 1214
[INFO] Bench -     OLD tess:2 (run 5): 1240
[WARN] Bench - OLD tess:2 (AVG): 1228
[INFO] Bench -     NEW tess:2 (heatup): 968
[INFO] Bench -     NEW tess:2 (run 1): 929
[INFO] Bench -     NEW tess:2 (run 2): 894
[INFO] Bench -     NEW tess:2 (run 3): 942
[INFO] Bench -     NEW tess:2 (run 4): 922
[INFO] Bench -     NEW tess:2 (run 5): 929
[WARN] Bench - NEW tess:2 (AVG): 923
[INFO] MyNumericalSurfaceTest - SPEEDUP: 1.33
[INFO] Bench -     OLD tess:3 (heatup): 3224
[INFO] Bench -     OLD tess:3 (run 1): 3230
[INFO] Bench -     OLD tess:3 (run 2): 3343
[INFO] Bench -     OLD tess:3 (run 3): 3269
[INFO] Bench -     OLD tess:3 (run 4): 3288
[INFO] Bench -     OLD tess:3 (run 5): 3306
[WARN] Bench - OLD tess:3 (AVG): 3287
[INFO] Bench -     NEW tess:3 (heatup): 2498
[INFO] Bench -     NEW tess:3 (run 1): 2339
[INFO] Bench -     NEW tess:3 (run 2): 2460
[INFO] Bench -     NEW tess:3 (run 3): 2587
[INFO] Bench -     NEW tess:3 (run 4): 2463
[INFO] Bench -     NEW tess:3 (run 5): 2489
[WARN] Bench - NEW tess:3 (AVG): 2467
[INFO] MyNumericalSurfaceTest - SPEEDUP: 1.332
[INFO] Bench -     OLD tess:4 (heatup): 10819
[INFO] Bench -     OLD tess:4 (run 1): 10759
[INFO] Bench -     OLD tess:4 (run 2): 10883
[INFO] Bench -     OLD tess:4 (run 3): 10989
[INFO] Bench -     OLD tess:4 (run 4): 10803
[INFO] Bench -     OLD tess:4 (run 5): 10806
[WARN] Bench - OLD tess:4 (AVG): 10848
[INFO] Bench -     NEW tess:4 (heatup): 7921
[INFO] Bench -     NEW tess:4 (run 1): 7903
[INFO] Bench -     NEW tess:4 (run 2): 7928
[INFO] Bench -     NEW tess:4 (run 3): 7888
[INFO] Bench -     NEW tess:4 (run 4): 7979
[INFO] Bench -     NEW tess:4 (run 5): 7810
[WARN] Bench - NEW tess:4 (AVG): 7901
[INFO] MyNumericalSurfaceTest - SPEEDUP: 1.373


+ primitive collections

[INFO] Bench -     OLD tess:2 (heatup): 1400
[INFO] Bench -     OLD tess:2 (run 1): 1262
[INFO] Bench -     OLD tess:2 (run 2): 1250
[INFO] Bench -     OLD tess:2 (run 3): 1283
[INFO] Bench -     OLD tess:2 (run 4): 1257
[INFO] Bench -     OLD tess:2 (run 5): 1234
[WARN] Bench - OLD tess:2 (AVG): 1257
[INFO] Bench -     NEW tess:2 (heatup): 953
[INFO] Bench -     NEW tess:2 (run 1): 867
[INFO] Bench -     NEW tess:2 (run 2): 842
[INFO] Bench -     NEW tess:2 (run 3): 847
[INFO] Bench -     NEW tess:2 (run 4): 826
[INFO] Bench -     NEW tess:2 (run 5): 878
[WARN] Bench - NEW tess:2 (AVG): 852
[INFO] MyNumericalSurfaceTest - SPEEDUP: 1.475
[INFO] Bench -     OLD tess:3 (heatup): 3352
[INFO] Bench -     OLD tess:3 (run 1): 3415
[INFO] Bench -     OLD tess:3 (run 2): 3532
[INFO] Bench -     OLD tess:3 (run 3): 3415
[INFO] Bench -     OLD tess:3 (run 4): 3342
[INFO] Bench -     OLD tess:3 (run 5): 3436
[WARN] Bench - OLD tess:3 (AVG): 3428
[INFO] Bench -     NEW tess:3 (heatup): 2486
[INFO] Bench -     NEW tess:3 (run 1): 2349
[INFO] Bench -     NEW tess:3 (run 2): 2486
[INFO] Bench -     NEW tess:3 (run 3): 2451
[INFO] Bench -     NEW tess:3 (run 4): 2423
[INFO] Bench -     NEW tess:3 (run 5): 2325
[WARN] Bench - NEW tess:3 (AVG): 2406
[INFO] MyNumericalSurfaceTest - SPEEDUP: 1.425
[INFO] Bench -     OLD tess:4 (heatup): 11796
[INFO] Bench -     OLD tess:4 (run 1): 11680
[INFO] Bench -     OLD tess:4 (run 2): 11596
[INFO] Bench -     OLD tess:4 (run 3): 11612
[INFO] Bench -     OLD tess:4 (run 4): 11627
[INFO] Bench -     OLD tess:4 (run 5): 11684
[WARN] Bench - OLD tess:4 (AVG): 11639
[INFO] Bench -     NEW tess:4 (heatup): 7961
[INFO] Bench -     NEW tess:4 (run 1): 7733
[INFO] Bench -     NEW tess:4 (run 2): 7995
[INFO] Bench -     NEW tess:4 (run 3): 7897
[INFO] Bench -     NEW tess:4 (run 4): 8104
[INFO] Bench -     NEW tess:4 (run 5): 8162
[WARN] Bench - NEW tess:4 (AVG): 7978
[INFO] MyNumericalSurfaceTest - SPEEDUP: 1.459

1.0

[INFO] Bench -     OLD tess:2 (heatup): 1414
[INFO] Bench -     OLD tess:2 (run 1): 1243
[INFO] Bench -     OLD tess:2 (run 2): 1249
[INFO] Bench -     OLD tess:2 (run 3): 1248
[INFO] Bench -     OLD tess:2 (run 4): 1235
[INFO] Bench -     OLD tess:2 (run 5): 1247
[WARN] Bench - OLD tess:2 (AVG): 1244
[INFO] Bench -     NEW tess:2 (heatup): 959
[INFO] Bench -     NEW tess:2 (run 1): 875
[INFO] Bench -     NEW tess:2 (run 2): 860
[INFO] Bench -     NEW tess:2 (run 3): 857
[INFO] Bench -     NEW tess:2 (run 4): 875
[INFO] Bench -     NEW tess:2 (run 5): 857
[WARN] Bench - NEW tess:2 (AVG): 864
[INFO] FasterNumericalSurfaceTest - SPEEDUP: 1.44
[INFO] Bench -     OLD tess:3 (heatup): 3359
[INFO] Bench -     OLD tess:3 (run 1): 3365
[INFO] Bench -     OLD tess:3 (run 2): 3420
[INFO] Bench -     OLD tess:3 (run 3): 3357
[INFO] Bench -     OLD tess:3 (run 4): 3395
[INFO] Bench -     OLD tess:3 (run 5): 3453
[WARN] Bench - OLD tess:3 (AVG): 3398
[INFO] Bench -     NEW tess:3 (heatup): 2510
[INFO] Bench -     NEW tess:3 (run 1): 2329
[INFO] Bench -     NEW tess:3 (run 2): 2458
[INFO] Bench -     NEW tess:3 (run 3): 2571
[INFO] Bench -     NEW tess:3 (run 4): 2557
[INFO] Bench -     NEW tess:3 (run 5): 2575
[WARN] Bench - NEW tess:3 (AVG): 2498
[INFO] FasterNumericalSurfaceTest - SPEEDUP: 1.36
[INFO] Bench -     OLD tess:4 (heatup): 17770
[INFO] Bench -     OLD tess:4 (run 1): 22138
[INFO] Bench -     OLD tess:4 (run 2): 20347
[INFO] Bench -     OLD tess:4 (run 3): 18072
[INFO] Bench -     OLD tess:4 (run 4): 11325
[INFO] Bench -     OLD tess:4 (run 5): 11322
[WARN] Bench - OLD tess:4 (AVG): 16640
[INFO] Bench -     NEW tess:4 (heatup): 8608
[INFO] Bench -     NEW tess:4 (run 1): 8473
[INFO] Bench -     NEW tess:4 (run 2): 8544
[INFO] Bench -     NEW tess:4 (run 3): 8603
[INFO] Bench -     NEW tess:4 (run 4): 8717
[INFO] Bench -     NEW tess:4 (run 5): 8428
[WARN] Bench - NEW tess:4 (AVG): 8553
[INFO] FasterNumericalSurfaceTest - SPEEDUP: 1.946

     */


}