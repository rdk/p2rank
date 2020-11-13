package cz.siret.prank.features.api

import cz.siret.prank.features.implementation.*
import cz.siret.prank.features.implementation.chem.ChemFeature
import cz.siret.prank.features.implementation.conservation.*
import cz.siret.prank.features.implementation.electrostatics.ElectrostaticsTempAtomFeature
import cz.siret.prank.features.implementation.electrostatics.ElectrostaticsTempSasFeature
import cz.siret.prank.features.implementation.external.CsvFileFeature
import cz.siret.prank.features.implementation.histogram.PairHistogramFeature
import cz.siret.prank.features.implementation.residue.ContactResiduesRF
import cz.siret.prank.features.implementation.secstruct.SecStructCloudSF
import cz.siret.prank.features.implementation.secstruct.SecStructRF
import cz.siret.prank.features.implementation.sequence.DupletsPropensityFeature
import cz.siret.prank.features.implementation.sequence.TripletsPropensityFeature
import cz.siret.prank.features.implementation.table.AtomTableFeature
import cz.siret.prank.features.implementation.table.ResidueTableFeature
import cz.siret.prank.features.implementation.volsite.VolsiteFeature
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic

/**
 * Registry of feature implementations
 */
@CompileStatic
class FeatureRegistry {

    private static Map<String, FeatureCalculator> features = new HashMap<>()

    /**
     *
     * @param key unique feature key. Add this key to Params.features to enable this feature.
     * @param featureCalculator
     */
    static void register(FeatureCalculator featureCalculator) {
        if (features.containsKey(featureCalculator.name)) {
            throw new PrankException("Trying to register 2 Features with the same name: " + featureCalculator.name)
        }

        features.put(featureCalculator.name, featureCalculator)
    }

    static Map<String, FeatureCalculator> getFeatureImplementations() {
        return features
    }

    static {

        register new ChemFeature()
        register new VolsiteFeature()
        register new ProtrusionFeature()
        register new BfactorFeature()
        register new AtomTableFeature()
        register new ResidueTableFeature()
        
        register new SurfaceProtrusionFeature()
        register new ProtrusionHistogramFeature()
        register new AtomicResidueFeature()
        register new ContactResidue1Feature()
        register new ContactResiduesPositionFeature()
        register new ContactResidue1PositionFeature()
        register new AsaFeature()
        register new Asa2Feature()
        register new AsaResiduesFeature()
        register new XyzDummyFeature()
        register new PairHistogramFeature()
        register new PyramidFeature()
        register new ProteinMassFeature()
        register new AAIndexFeature()

        register new ResidueToSasFeatWrapper(new DupletsPropensityFeature())
        register new ResidueToAtomicFeatWrapper(new DupletsPropensityFeature())
        register new ResidueToSasFeatWrapper(new TripletsPropensityFeature())
        register new ResidueToAtomicFeatWrapper(new TripletsPropensityFeature())

        register new ResidueToSasFeatWrapper(new SecStructRF())
        register new ResidueToAtomicFeatWrapper(new SecStructRF())
        register new SecStructCloudSF()
        register new ResidueToSasFeatWrapper(new ContactResiduesRF())

        register new ConservationFeature()
        register new ConservationCloudFeature()
        register new ConservationCloudScaledFeature()
        register new ResidueToSasFeatWrapper(new ConservRF())
        register new ResidueToAtomicFeatWrapper(new ConservRF())
        register new ConservCloudSF()

        register new CsvFileFeature()

        register new ElectrostaticsTempSasFeature()
        register new ElectrostaticsTempAtomFeature()

        // Register new feature implementations here

    }

}
