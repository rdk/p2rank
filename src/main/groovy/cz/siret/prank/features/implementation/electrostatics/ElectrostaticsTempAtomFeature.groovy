package cz.siret.prank.features.implementation.electrostatics

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.features.implementation.electrostatics.ElectrostaticsTempSasFeature.CubePreloader.ensureCubeLoaded
import static cz.siret.prank.features.implementation.electrostatics.ElectrostaticsTempSasFeature.cubeValueForPoint

/**
 *
 */
@Slf4j
@CompileStatic
class ElectrostaticsTempAtomFeature extends AtomFeatureCalculator implements Parametrized, Writable {

    @Override
    String getName() {
        return "electrostatics_temp_atomic"
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        ensureCubeLoaded(protein, context)
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {

        return [cubeValueForPoint(proteinSurfaceAtom, context.protein)] as double[]
    }

}
