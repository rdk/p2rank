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
     * TODO implement meaningful FasterNumericalSurface test
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



}