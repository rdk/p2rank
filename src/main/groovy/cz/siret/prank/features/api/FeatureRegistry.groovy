package cz.siret.prank.features.api

import cz.siret.prank.features.ResidueTypeFeature
import cz.siret.prank.features.api.wrappers.AtomicToSasFeatWrapper
import cz.siret.prank.features.api.wrappers.ResidueToAtomicFeatWrapper
import cz.siret.prank.features.api.wrappers.ResidueToSasFeatWrapper
import cz.siret.prank.features.api.wrappers.SasToAtomicFeatWrapper
import cz.siret.prank.features.implementation.*
import cz.siret.prank.features.implementation.asa.Asa2Feature
import cz.siret.prank.features.implementation.asa.AsaFeature
import cz.siret.prank.features.implementation.asa.AsaResiduesFeature
import cz.siret.prank.features.implementation.chem.ChemFeature
import cz.siret.prank.features.implementation.conservation.*
import cz.siret.prank.features.implementation.contactres.ContactResidue1Feature
import cz.siret.prank.features.implementation.contactres.ContactResidue1PositionFeature
import cz.siret.prank.features.implementation.contactres.ContactResiduesPositionFeature
import cz.siret.prank.features.implementation.contactres.ContactResiduesRF
import cz.siret.prank.features.implementation.csv.CsvFileFeature
import cz.siret.prank.features.implementation.physics.AnmEffectivenessRF
import cz.siret.prank.features.implementation.physics.AnmMsfRF
import cz.siret.prank.features.implementation.physics.AnmSensorRF
import cz.siret.prank.features.implementation.physics.CgBetweennessRF
import cz.siret.prank.features.implementation.physics.CgClosenessRF
import cz.siret.prank.features.implementation.physics.CgDegreeRF
import cz.siret.prank.features.implementation.electrostatics.DelphiCubeAtomFeature
import cz.siret.prank.features.implementation.electrostatics.DelphiCubeSasFeature
import cz.siret.prank.features.implementation.electrostatics.ElectrostaticsSasFeature
import cz.siret.prank.features.implementation.electrostatics.PartialChargeFeature
import cz.siret.prank.features.implementation.energy.*
import cz.siret.prank.features.implementation.energy2.*
import cz.siret.prank.features.implementation.energy3.*
import cz.siret.prank.features.implementation.histogram.PairHistogramFeature
import cz.siret.prank.features.implementation.propensity.AaPropensityFeature
import cz.siret.prank.features.implementation.propensity.AtomTypePropensityFeature
import cz.siret.prank.features.implementation.propensity.DupletsPropensityFeature
import cz.siret.prank.features.implementation.propensity.TripletsPropensityFeature
import cz.siret.prank.features.implementation.secstruct.*
import cz.siret.prank.features.implementation.sidechain.IsSidechainAtomFeature
import cz.siret.prank.features.implementation.sidechain.IsSidechainCloudFeature
import cz.siret.prank.features.implementation.sidechain.IsSidechainSasFeature
import cz.siret.prank.features.implementation.structmotif.StructMotifFeature
import cz.siret.prank.features.implementation.table.AAIndexAtomFeature
import cz.siret.prank.features.implementation.table.AAIndexFeature
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
     * @param calculator
     */
    static void register(FeatureCalculator calculator) {
        if (features.containsKey(calculator.name)) {
            throw new PrankException("Trying to register 2 Features with the same name: " + calculator.name)
        }

        if (!(calculator.type in [FeatureCalculator.Type.ATOM, FeatureCalculator.Type.SAS_POINT]))  {
            throw new IllegalStateException("Invalid feature: $calculator.name. Only ATOM and SAS_POINT features ca be used directly.")
        }

        features.put(calculator.name, calculator)
    }

    static Map<String, FeatureCalculator> getFeatureImplementations() {
        return features
    }

    static {

        register new ChemFeature()
        register new AtomicToSasFeatWrapper(new ChemFeature())
        register new VolsiteFeature()
        register new AtomicToSasFeatWrapper(new VolsiteFeature())   // maps closest atom to SAS point
        register new BfactorFeature()
        register new AtomicToSasFeatWrapper(new BfactorFeature())
        register new AtomTableFeature()
        register new AtomicToSasFeatWrapper(new AtomTableFeature())
        register new ResidueTableFeature()
        register new AtomicToSasFeatWrapper(new ResidueTableFeature())
        register new ProtrusionFeature()

        register new SurfaceProtrusionFeature()
        register new ProtrusionHistogramFeature()
        register new ContactResidue1Feature()
        register new ContactResiduesPositionFeature()
        register new ContactResidue1PositionFeature()
        register new AsaFeature()
        register new Asa2Feature()
        register new AsaResiduesFeature()
        register new PairHistogramFeature()
        register new PyramidFeature()
        register new ProteinMassFeature()
        register new XyzDummyFeature()
        register new AAIndexAtomFeature()
        register new AtomicResidueFeature()

        register new ResidueToSasFeatWrapper(new ResidueTypeFeature())
        register new ResidueToAtomicFeatWrapper(new ResidueTypeFeature())
        
        register new ResidueToSasFeatWrapper(new AAIndexFeature())
        register new ResidueToAtomicFeatWrapper(new AAIndexFeature())

        // propensity
        register new ResidueToSasFeatWrapper(new AaPropensityFeature())
        register new ResidueToAtomicFeatWrapper(new AaPropensityFeature())
        register new ResidueToSasFeatWrapper(new DupletsPropensityFeature())
        register new ResidueToAtomicFeatWrapper(new DupletsPropensityFeature())
        register new ResidueToSasFeatWrapper(new TripletsPropensityFeature())
        register new ResidueToAtomicFeatWrapper(new TripletsPropensityFeature())
        register new AtomTypePropensityFeature()
        register new AtomicToSasFeatWrapper(new AtomTypePropensityFeature())

        register new ResidueToSasFeatWrapper(new SecStructRF())
        register new ResidueToAtomicFeatWrapper(new SecStructRF())
        register new SecStructCloudSF()
        register new SecStructSimpleCloudSF()
        register new ResidueToSasFeatWrapper(new SecStructSimpleRF())
        register new ResidueToAtomicFeatWrapper(new SecStructSimpleRF())

        // ss motifs
        register new ResidueToSasFeatWrapper(new SecStructSimpleMotifRF(true))
        register new ResidueToAtomicFeatWrapper(new SecStructSimpleMotifRF(true))
        register new ResidueToSasFeatWrapper(new SecStructSimpleMotifRF(false))
        register new ResidueToAtomicFeatWrapper(new SecStructSimpleMotifRF(false))

        register new ResidueToSasFeatWrapper(new ContactResiduesRF())

        // conservation
        register new ConservationFeature()
        register new ConservationCloudFeature()
        register new ConservationCloudScaledFeature()
        register new ResidueToSasFeatWrapper(new ConservRF())
        register new ResidueToAtomicFeatWrapper(new ConservRF())
        register new ConservCloudSF()
        register new ConservCloud2SF()
        // z-score conservation
        register new ResidueToSasFeatWrapper(new ZConservRF())
        register new ResidueToAtomicFeatWrapper(new ZConservRF())
        register new ZConservCloudSF()
        register new ZConservCloud2SF()

        register new CsvFileFeature()

        register new DelphiCubeSasFeature()
        register new DelphiCubeAtomFeature()

        register new PartialChargeFeature()
        register new AtomicToSasFeatWrapper(new PartialChargeFeature())
        register new ElectrostaticsSasFeature()

        register new IsExposedAtomFeature()
        register new AtomicToSasFeatWrapper(new IsExposedAtomFeature())
        register new NearestExposedDistSasFeature()

        register new StructMotifFeature()
        register new SasToAtomicFeatWrapper(new StructMotifFeature())

        register new IsSidechainAtomFeature()
        register new IsSidechainSasFeature()
        register new IsSidechainCloudFeature()

        register new MethylEnergyFeature()
        register new MethylEnergyCloudSF()
        register new MethylEnergyCloudXSF()
        register new MethylEnergyCloudX2SF()
        register new MethylEnergyCloudX2FullSF()


        register new NeutralApolarProbeEnergyFeature()
        register new HBAcceptorProbeEnergyFeature()
        register new HBDonorProbeEnergyFeature()
        register new AromaticRingProbeEnergyFeature()
        register new CationProbeEnergyFeature()

        register new NeutralApolarSingleProbeEnergyFeature()
        register new HBAcceptorSingleProbeEnergyFeature()
        register new HBDonorSingleProbeEnergyFeature()
        register new AromaticRingSingleProbeEnergyFeature()
        register new CationSingleProbeEnergyFeature()

        register new NeutralApolarDirectFeature()
        register new HBDonorDirectFeature()
        register new HBAcceptorDirectFeature()
        register new AromaticRingDirectFeature()
        register new CationDirectFeature()



        register new HybridizationFeature()
        register new AtomicToSasFeatWrapper(new HybridizationFeature())

        // physics-based residue descriptors (ANM + contact graph)
        register new ResidueToSasFeatWrapper(new AnmSensorRF())
        register new ResidueToAtomicFeatWrapper(new AnmSensorRF())
        register new ResidueToSasFeatWrapper(new AnmEffectivenessRF())
        register new ResidueToAtomicFeatWrapper(new AnmEffectivenessRF())
        register new ResidueToSasFeatWrapper(new AnmMsfRF())
        register new ResidueToAtomicFeatWrapper(new AnmMsfRF())
        register new ResidueToSasFeatWrapper(new CgBetweennessRF())
        register new ResidueToAtomicFeatWrapper(new CgBetweennessRF())
        register new ResidueToSasFeatWrapper(new CgClosenessRF())
        register new ResidueToAtomicFeatWrapper(new CgClosenessRF())
        register new ResidueToSasFeatWrapper(new CgDegreeRF())
        register new ResidueToAtomicFeatWrapper(new CgDegreeRF())

        // Register new feature implementations here

    }

}
