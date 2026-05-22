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

import static cz.siret.prank.features.implementation.electrostatics.DelphiCubeSasFeature.CubePreloader.ensureCubeLoaded
import static cz.siret.prank.features.implementation.electrostatics.DelphiCubeSasFeature.cubeValueForPoint

/**
 * Atom-level feature: reads electrostatic potential from precomputed Delphi
 * cube files on disk. The CLI/CSV feature key remains
 * {@code electrostatics_temp_atomic} for back-compat.
 *
 * <p>Distinct from {@link PartialChargeFeature} which uses AMBER partial charges.
 */
@Slf4j
@CompileStatic
class DelphiCubeAtomFeature extends AtomFeatureCalculator implements Parametrized, Writable {

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
