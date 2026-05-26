package cz.siret.prank.features.implementation.energy2.calc

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element

import java.util.function.ToDoubleFunction

/**
 * Batch calculator: computes multiple Tier-1 single-point probe energies in one pass (no hydrogens).
 * Shares distances, window s(r), and 1/r powers across probes to avoid redundant work.
 * LJ uses Lorentz–Berthelot mixing; HB uses isotropic 12–10 well; Coulomb uses high dielectric.
 * Partner role masks (donor/acceptor) are heavy-atom heuristics; directionality is intentionally ignored for speed.
 * Smooth switch [R_on, R_c] removes cutoff artifacts and damps far-field tails.
 * r is clamped to r_min to prevent singularities near atom centers.
 * Outputs are kcal/mol; more negative indicates stronger probe-protein preference.
 */
@Slf4j
@CompileStatic
class EnergyCalculator {

    // Configuration
    private final EnergyCalculatorConfig config

    // Cached constants for performance
    private final double RC
    private final double RON
    private final double MIN_R
    private final double RC_MINUS_RON
    private final double INV_RC_MINUS_RON
    private final double PI = Math.PI
    private final double K_E  // Coulomb constant
    private final List<ProbeType> selectedProbesList

    // Parameter tables
    private final Map<Element, LJParams> ljParams
    private final Map<Integer, HBParams> hbOverrides

    /**
     * Per-atom charge lookup (e units). When null and {@code enableCoulomb=true},
     * the Coulomb term collapses to a no-op (every charge is 0). Decoupled
     * via {@link ToDoubleFunction} so {@code energy2.calc} doesn't have to
     * depend on the {@code electrostatics} package — callers wire in
     * {@code PartialChargeTable.forProtein(protein)::get} or any other supplier.
     */
    private final ToDoubleFunction<Atom> chargeSupplier

    // Counter for warnings
    private volatile int missingParamWarningCount = 0
    private static final int MAX_WARNING_COUNT = 10

    /**
     * Container for LJ parameters
     */
    @CompileStatic
    static class LJParams {
        final double sigma
        final double epsilon

        LJParams(double sigma, double epsilon) {
            this.sigma = sigma
            this.epsilon = epsilon
        }
    }

    /**
     * Container for H-bond override parameters
     */
    @CompileStatic
    static class HBParams {
        final double r0
        final double epsilon

        HBParams(double r0, double epsilon) {
            this.r0 = r0
            this.epsilon = epsilon
        }
    }

    /**
     * Per-atom invariants cached across SAS points. These properties depend
     * only on the atom identity, not on the query point, so they are computed
     * once per atom and reused for every SAS point that sees this neighbour.
     */
    private static final Set<String> AROMATIC_RESIDUES = Set.of("PHE", "TYR", "TRP", "HIS")

    @CompileStatic
    private static class AtomData {
        final double sigma
        final double epsilon
        final double charge
        final AtomRole role
        final boolean aromatic
        final boolean skip  // true for hydrogens or atoms with no element

        AtomData(double sigma, double epsilon, double charge, AtomRole role, boolean aromatic, boolean skip) {
            this.sigma = sigma; this.epsilon = epsilon; this.charge = charge
            this.role = role; this.aromatic = aromatic; this.skip = skip
        }
    }

    private final Map<Integer, AtomData> atomDataCache = new HashMap<>()

    private AtomData getAtomData(Atom atom) {
        int serial = atom.PDBserial
        if (serial != 0) {
            AtomData cached = atomDataCache.get(serial)
            if (cached != null) return cached
        }

        Element element = atom.getElement()
        if (element == Element.H || element == null) {
            AtomData ad = new AtomData(0, 0, 0, new AtomRole(false, false, 0), false, true)
            if (serial != 0) atomDataCache.put(serial, ad)
            return ad
        }

        LJParams ljParam = ljParams[element]
        double sigma = ljParam ? ljParam.sigma : 3.5d
        double epsilon = ljParam ? ljParam.epsilon : 0.1d

        if (!ljParam) {
            logMissingParamWarning(element)
        }

        double charge = (config.enableCoulomb && chargeSupplier != null)
                ? chargeSupplier.applyAsDouble(atom)
                : 0.0d

        AtomRole role = AtomRole.classify(atom)

        String resName = atom.getGroup()?.getPDBName()?.trim()?.toUpperCase()
        boolean aromatic = resName != null && AROMATIC_RESIDUES.contains(resName)

        AtomData ad = new AtomData(sigma, epsilon, charge, role, aromatic, false)
        if (serial != 0) {
            atomDataCache.put(serial, ad)
        }
        return ad
    }

    /**
     * Precomputed neighbor data for efficiency
     */
    @CompileStatic
    private static class NeighborData {
        final double r
        final double invR
        final double invR2
        final double invR6
        final double invR10
        final double invR12
        final double switchValue
        final Element element
        final double sigma
        final double epsilon
        final double charge
        final AtomRole role
        final boolean active
        final boolean aromatic

        NeighborData(double r, double invR, double invR2, double invR6, double invR10, double invR12,
                    double switchValue, Element element, double sigma, double epsilon,
                    double charge, AtomRole role, boolean active, boolean aromatic) {
            this.r = r
            this.invR = invR
            this.invR2 = invR2
            this.invR6 = invR6
            this.invR10 = invR10
            this.invR12 = invR12
            this.switchValue = switchValue
            this.element = element
            this.sigma = sigma
            this.epsilon = epsilon
            this.charge = charge
            this.role = role
            this.active = active
            this.aromatic = aromatic
        }
    }

    /**
     * Initialize the calculator with configuration. Coulomb term will be a
     * no-op (every charge = 0) — use the 2-arg constructor to wire in a real
     * charge source.
     */
    EnergyCalculator(EnergyCalculatorConfig config) {
        this(config, null)
    }

    /**
     * Initialize with configuration and a per-atom charge supplier. The
     * supplier is consulted once per neighbor in {@link #precomputeNeighborData}
     * when {@code config.enableCoulomb} is true; pass null (or use the 1-arg
     * ctor) to disable Coulomb regardless of config.
     */
    EnergyCalculator(EnergyCalculatorConfig config, ToDoubleFunction<Atom> chargeSupplier) {
        this.config = config
        this.chargeSupplier = chargeSupplier

        // Cache constants
        this.RC = config.rCutoff
        this.RON = config.rOn
        this.MIN_R = config.rMin
        this.RC_MINUS_RON = config.rCutoff - config.rOn
        this.INV_RC_MINUS_RON = 1.0 / RC_MINUS_RON
        this.K_E = config.coulombConstant
        this.selectedProbesList = new ArrayList<>(config.selectedProbes)

        // Load parameter tables
        this.ljParams = loadLJParamsFromCSV()
        this.hbOverrides = loadHBOverridesFromCSV()

        log.info("EnergyCalculator initialized: rc={}, ron={}, min_r={}, probes={}, {} LJ elements, {} HB overrides loaded, chargeSupplier={}",
                 RC, RON, MIN_R, selectedProbesList, ljParams.size(), hbOverrides.size(),
                 chargeSupplier == null ? "none (Coulomb=0)" : "wired")
    }

    /**
     * Compute energies for all selected probes at a single point
     * Returns ordered vector aligned with selectedProbes
     */
    List<Double> computeEnergyForPoint(Atom point, Atoms neighbours) {
        if (!neighbours || neighbours.isEmpty()) {
            List<Double> zeros = new ArrayList<>()
            for (int i = 0; i < selectedProbesList.size(); i++) {
                zeros.add(0.0 as Double)
            }
            return zeros
        }

        // Precompute neighbor data once
        List<NeighborData> neighborData = precomputeNeighborData(point, neighbours)

        // Initialize energy accumulators
        List<Double> energies = new ArrayList<>()
        for (int i = 0; i < selectedProbesList.size(); i++) {
            energies.add(0.0 as Double)
        }

        // Accumulate contributions from each active neighbor
        for (NeighborData neighbor : neighborData) {
            if (!neighbor.active) continue

            for (int probeIdx = 0; probeIdx < selectedProbesList.size(); probeIdx++) {
                ProbeType probe = selectedProbesList[probeIdx]
                ProbeParams probeParams = config.probeParams[probe]

                double energy = computeProbeAtomEnergy(probe, probeParams, neighbor)
                double currentEnergy = energies[probeIdx]
                energies[probeIdx] = currentEnergy + energy
            }
        }

        return energies
    }

    /**
     * Precompute shared radial terms for all neighbors
     */
    private List<NeighborData> precomputeNeighborData(Atom point, Atoms neighbours) {
        List<NeighborData> data = new ArrayList<>()

        for (Atom atom : neighbours) {
            AtomData ad = getAtomData(atom)
            if (ad.skip) continue

            double r = Struct.dist(atom, point)
            if (r >= RC) continue

            r = Math.max(r, MIN_R)

            double invR = 1.0 / r
            double invR2 = invR * invR
            double invR6 = invR2 * invR2 * invR2
            double invR10 = invR6 * invR2 * invR2
            double invR12 = invR6 * invR6

            double switchValue = computeSwitchValue(r)

            data.add(new NeighborData(r, invR, invR2, invR6, invR10, invR12,
                                    switchValue, atom.getElement(), ad.sigma, ad.epsilon,
                                    ad.charge, ad.role, true, ad.aromatic))
        }

        return data
    }

    /**
     * Compute energy between a specific probe and neighbor atom
     */
    private double computeProbeAtomEnergy(ProbeType probe, ProbeParams probeParams, NeighborData neighbor) {
        double totalEnergy = 0.0

        switch (probe) {
            case ProbeType.NEUTRAL_APOLAR_SP:
                totalEnergy = computeLJEnergy(probeParams, neighbor)
                break

            case ProbeType.AROMATIC_RING_SP:
                if (config.aromaticOnly && !neighbor.aromatic) {
                    break
                }
                totalEnergy = computeLJEnergy(probeParams, neighbor)
                if (totalEnergy < probeParams.energyMinCap) {
                    totalEnergy = probeParams.energyMinCap
                }
                break

            case ProbeType.HB_ACCEPTOR_SP:
                // Only interact with donor atoms
                if (neighbor.role.isDonor) {
                    totalEnergy = computeHBEnergy(probeParams, neighbor)

                    // Add LJ background if weight > 0
                    if (probeParams.ljWeight > 0) {
                        double ljBackground = computeLJEnergy(probeParams, neighbor)
                        totalEnergy += probeParams.ljWeight * ljBackground
                    }
                }
                break

            case ProbeType.HB_DONOR_SP:
                // Only interact with acceptor atoms
                if (neighbor.role.isAcceptor) {
                    totalEnergy = computeHBEnergy(probeParams, neighbor)

                    // Add LJ background if weight > 0
                    if (probeParams.ljWeight > 0) {
                        double ljBackground = computeLJEnergy(probeParams, neighbor)
                        totalEnergy += probeParams.ljWeight * ljBackground
                    }
                }
                break

            case ProbeType.CATION_SP:
                // LJ background
                totalEnergy = computeLJEnergy(probeParams, neighbor)

                // Add Coulomb if enabled and charges available
                if (config.enableCoulomb && neighbor.charge != 0.0) {
                    double coulombEnergy = K_E * (neighbor.charge * probeParams.charge) / config.dielectricConstant * neighbor.invR
                    totalEnergy += coulombEnergy
                }
                break
        }

        return neighbor.switchValue * totalEnergy
    }

    /**
     * Compute LJ 12-6 energy using Lorentz-Berthelot mixing
     */
    private double computeLJEnergy(ProbeParams probeParams, NeighborData neighbor) {
        // Lorentz–Berthelot mixing
        double sigmaMix = (neighbor.sigma + probeParams.ljSigma) * 0.5
        double epsilonMix = Math.sqrt(neighbor.epsilon * probeParams.ljEpsilon)

        // Use precomputed powers: (σ_mix/r)^6 = σ_mix^6 * inv_r6
        double sigma2 = sigmaMix * sigmaMix
        double sigma6 = sigma2 * sigma2 * sigma2
        double sigma12 = sigma6 * sigma6

        return 4.0 * epsilonMix * (sigma12 * neighbor.invR12 - sigma6 * neighbor.invR6)
    }

    /**
     * Compute H-bond 12-10 energy with optional role-based overrides
     */
    private double computeHBEnergy(ProbeParams probeParams, NeighborData neighbor) {
        // Determine HB parameters (check for role overrides)
        HBParams hbParam = hbOverrides[neighbor.role.roleClassID]
        double r0 = hbParam?.r0 ?: probeParams.hbR0
        double epsHB = hbParam?.epsilon ?: probeParams.hbEpsilon

        // Compute r0 powers
        double r0_2 = r0 * r0
        double r0_4 = r0_2 * r0_2
        double r0_5 = r0_4 * r0
        double r0_10 = r0_5 * r0_5
        double r0_12 = r0_10 * r0_2

        // E_HB(r; r0, ε_hb) = ε_hb * [ (r0/r)^12 − 2*(r0/r)^10 ]
        return epsHB * (r0_12 * neighbor.invR12 - 2.0 * r0_10 * neighbor.invR10)
    }

    /**
     * Compute smooth switching function value
     */
    private double computeSwitchValue(double r) {
        if (r <= RON) {
            return 1.0
        } else {
            return 0.5 * (1.0 + Math.cos(PI * (r - RON) * INV_RC_MINUS_RON))
        }
    }

    /**
     * Load LJ parameters from CSV file
     */
    private Map<Element, LJParams> loadLJParamsFromCSV() {
        Map<Element, LJParams> params = new HashMap<>()

        try {
            String csvText = Futils.readResource(config.ljParamsCSV)

            csvText.eachLine { line, lineNum ->
                // Skip header line
                if (lineNum == 0) return

                // Skip comments and empty lines
                line = line.trim()
                if (!line || line.startsWith('#')) return

                String[] parts = line.split(',')
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid CSV format at line ${lineNum}: expected 3 columns, got ${parts.length}")
                }

                try {
                    String elementSymbol = parts[0].trim()
                    double sigma = Double.parseDouble(parts[1].trim())
                    double epsilon = Double.parseDouble(parts[2].trim())

                    Element element = Element.valueOfIgnoreCase(elementSymbol)
                    if (element != null) {
                        params.put(element, new LJParams(sigma, epsilon))
                    } else {
                        log.warn("Unknown element symbol in CSV: ${elementSymbol}")
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid numeric values in CSV at line ${lineNum}: ${line}", e)
                }
            }
        } catch (Exception e) {
            log.warn("Could not load LJ parameters from ${config.ljParamsCSV}: ${e.message}")
        }

        return params
    }

    /**
     * Load H-bond override parameters from CSV file
     */
    private Map<Integer, HBParams> loadHBOverridesFromCSV() {
        Map<Integer, HBParams> overrides = new HashMap<>()

        try {
            String csvText = Futils.readResource(config.hbOverridesCSV)

            csvText.eachLine { line, lineNum ->
                // Skip header line
                if (lineNum == 0) return

                // Skip comments and empty lines
                line = line.trim()
                if (!line || line.startsWith('#')) return

                String[] parts = line.split(',')
                if (parts.length != 3) {
                    return  // Skip malformed lines silently for optional overrides
                }

                try {
                    int roleClass = Integer.parseInt(parts[0].trim())
                    double r0 = Double.parseDouble(parts[1].trim())
                    double epsilon = Double.parseDouble(parts[2].trim())

                    overrides.put(roleClass, new HBParams(r0, epsilon))
                } catch (NumberFormatException e) {
                    log.debug("Skipping invalid HB override line ${lineNum}: ${line}")
                }
            }
        } catch (Exception e) {
            log.debug("HB overrides file not found or invalid: ${config.hbOverridesCSV}")
        }

        return overrides
    }

    /**
     * Log missing parameter warnings (rate-limited)
     */
    private void logMissingParamWarning(Element element) {
        if (missingParamWarningCount < MAX_WARNING_COUNT) {
            log.warn("Missing LJ parameters for element: ${element}, using fallback values")
            missingParamWarningCount++
            if (missingParamWarningCount == MAX_WARNING_COUNT) {
                log.warn("Further missing element parameter warnings will be suppressed")
            }
        }
    }
}