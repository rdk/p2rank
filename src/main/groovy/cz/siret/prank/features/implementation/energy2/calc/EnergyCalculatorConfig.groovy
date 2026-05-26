package cz.siret.prank.features.implementation.energy2.calc

import groovy.transform.CompileStatic

/**
 * Configuration object for the Tier-1 Multi-Probe Energy Calculator.
 * Encapsulates all parameters needed for energy computation.
 */
@CompileStatic
class EnergyCalculatorConfig {

    // Global physics parameters
    final double rCutoff
    final double rOn
    final double rMin
    final double dielectricConstant
    final boolean enableCoulomb
    final double coulombConstant = 332.06371 // kcal·Å·mol⁻¹·e⁻²

    // Aromatic-ring probe interacts only with aromatic residue atoms
    final boolean aromaticOnly

    // Probe selection
    final Set<ProbeType> selectedProbes

    // Probe-specific parameters
    final Map<ProbeType, ProbeParams> probeParams

    // File paths for parameter tables
    final String ljParamsCSV
    // unused: AtomRole.classify is hardcoded; the shipped role-rules.csv is not read
    // anywhere. Field + builder option kept for now in case role rules become
    // data-driven.
    final String roleRulesCSV
    final String hbOverridesCSV

    // Default constructor with recommended values
    EnergyCalculatorConfig() {
        this.rCutoff = 9.0
        this.rOn = 7.0
        this.rMin = 1.8
        this.dielectricConstant = 12.0
        this.enableCoulomb = true
        this.aromaticOnly = false
        this.selectedProbes = EnumSet.allOf(ProbeType.class)
        this.ljParamsCSV = "/tables/energy/lj-params.csv"
        this.roleRulesCSV = "/tables/energy/role-rules.csv"
        this.hbOverridesCSV = "/tables/energy/hb-overrides.csv"
        this.probeParams = createDefaultProbeParams()
    }

    // Builder constructor
    EnergyCalculatorConfig(double rCutoff, double rOn, double rMin,
                          double dielectricConstant, boolean enableCoulomb,
                          boolean aromaticOnly,
                          Set<ProbeType> selectedProbes,
                          String ljParamsCSV, String roleRulesCSV, String hbOverridesCSV,
                          Map<ProbeType, ProbeParams> probeParams) {

        // Validate parameters
        if (rOn >= rCutoff || rOn <= 0 || rMin >= rOn) {
            throw new IllegalArgumentException(
                "Invalid energy parameters: must have 0 < rMin < rOn < rCutoff. " +
                "Got: rMin=${rMin}, rOn=${rOn}, rCutoff=${rCutoff}")
        }

        this.rCutoff = rCutoff
        this.rOn = rOn
        this.rMin = rMin
        this.dielectricConstant = dielectricConstant
        this.enableCoulomb = enableCoulomb
        this.aromaticOnly = aromaticOnly
        this.selectedProbes = EnumSet.copyOf(selectedProbes)
        this.ljParamsCSV = ljParamsCSV
        this.roleRulesCSV = roleRulesCSV
        this.hbOverridesCSV = hbOverridesCSV
        this.probeParams = new HashMap<>(probeParams)
    }

    private Map<ProbeType, ProbeParams> createDefaultProbeParams() {
        Map<ProbeType, ProbeParams> params = new EnumMap<>(ProbeType.class)

        // NEUTRAL_APOLAR_SP: generic hydrophobe (methyl/ethyl surrogate)
        params.put(ProbeType.NEUTRAL_APOLAR_SP, new ProbeParams(
            ljSigma: 3.5,
            ljEpsilon: 0.08,
            hbR0: 0.0,
            hbEpsilon: 0.0,
            ljWeight: 1.0,
            charge: 0.0,
            energyMinCap: Double.NEGATIVE_INFINITY
        ))

        // AROMATIC_RING_SP: benzene-like hydrophobe with larger σ and capped well depth
        params.put(ProbeType.AROMATIC_RING_SP, new ProbeParams(
            ljSigma: 4.2,
            ljEpsilon: 0.12,
            hbR0: 0.0,
            hbEpsilon: 0.0,
            ljWeight: 1.0,
            charge: 0.0,
            energyMinCap: -0.8
        ))

        // HB_ACCEPTOR_SP: probe acts as H-bond acceptor
        params.put(ProbeType.HB_ACCEPTOR_SP, new ProbeParams(
            ljSigma: 3.0,
            ljEpsilon: 0.05,
            hbR0: 3.0,
            hbEpsilon: 0.8,
            ljWeight: 0.25,
            charge: 0.0,
            energyMinCap: Double.NEGATIVE_INFINITY
        ))

        // HB_DONOR_SP: probe acts as H-bond donor
        params.put(ProbeType.HB_DONOR_SP, new ProbeParams(
            ljSigma: 3.0,
            ljEpsilon: 0.05,
            hbR0: 3.0,
            hbEpsilon: 0.8,
            ljWeight: 0.25,
            charge: 0.0,
            energyMinCap: Double.NEGATIVE_INFINITY
        ))

        // CATION_SP: cationic fragment
        params.put(ProbeType.CATION_SP, new ProbeParams(
            ljSigma: 3.0,
            ljEpsilon: 0.05,
            hbR0: 0.0,
            hbEpsilon: 0.0,
            ljWeight: 1.0,
            charge: 0.5,
            energyMinCap: Double.NEGATIVE_INFINITY
        ))

        return params
    }

    /**
     * Builder class for creating custom configurations
     */
    static class Builder {
        private double rCutoff = 9.0
        private double rOn = 7.0
        private double rMin = 1.8
        private double dielectricConstant = 12.0
        private boolean enableCoulomb = true
        private boolean aromaticOnly = false
        private Set<ProbeType> selectedProbes = EnumSet.allOf(ProbeType.class)
        private String ljParamsCSV = "/tables/energy/lj-params.csv"
        private String roleRulesCSV = "/tables/energy/role-rules.csv"
        private String hbOverridesCSV = "/tables/energy/hb-overrides.csv"
        private Map<ProbeType, ProbeParams> probeParams = null

        public Builder rCutoff(double rCutoff) { this.rCutoff = rCutoff; return this }
        public Builder rOn(double rOn) { this.rOn = rOn; return this }
        public Builder rMin(double rMin) { this.rMin = rMin; return this }
        public Builder dielectricConstant(double dielectricConstant) {
            this.dielectricConstant = dielectricConstant; return this
        }
        public Builder enableCoulomb(boolean enableCoulomb) {
            this.enableCoulomb = enableCoulomb; return this
        }
        public Builder aromaticOnly(boolean aromaticOnly) {
            this.aromaticOnly = aromaticOnly; return this
        }
        public Builder selectedProbes(Set<ProbeType> selectedProbes) {
            this.selectedProbes = selectedProbes; return this
        }
        public Builder ljParamsCSV(String ljParamsCSV) {
            this.ljParamsCSV = ljParamsCSV; return this
        }
        public Builder roleRulesCSV(String roleRulesCSV) {
            this.roleRulesCSV = roleRulesCSV; return this
        }
        public Builder hbOverridesCSV(String hbOverridesCSV) {
            this.hbOverridesCSV = hbOverridesCSV; return this
        }
        public Builder probeParams(Map<ProbeType, ProbeParams> probeParams) {
            this.probeParams = probeParams; return this
        }

        public EnergyCalculatorConfig build() {
            Map<ProbeType, ProbeParams> params = this.probeParams ?:
                new EnergyCalculatorConfig().createDefaultProbeParams()

            return new EnergyCalculatorConfig(
                rCutoff, rOn, rMin, dielectricConstant, enableCoulomb, aromaticOnly,
                selectedProbes, ljParamsCSV, roleRulesCSV, hbOverridesCSV, params
            )
        }
    }
}



