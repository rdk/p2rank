package cz.siret.prank.features.implementation.secstruct

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.secstruc.SecStrucType


/**
 *
 */
@CompileStatic
class SecStructCloudSF extends SasFeatureCalculator implements Parametrized {

    final List<String> HEADER = SsHistogram.header

    @Override
    String getName() {
        return 'ss_cloud'
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        double radius = params.protrusion_radius // TODO new param

        Atoms atoms = context.extractor.deepLayer.cutoutSphere(sasPoint, radius)
        List<Residue> residues = context.protein.residues.getDistinctForAtoms(atoms)
        List<SecStrucType> types = residues.collect { it.ss.type }.asList()

        return SsHistogram.average(types)
    }
}
