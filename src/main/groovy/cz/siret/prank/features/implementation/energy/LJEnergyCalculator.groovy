package cz.siret.prank.features.implementation.energy

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element

/**
 * Energy calculator for vdW methyl-probe scoring.
 * Immutable, thread-safe. Handles LB mixing, cosine switch, and numeric guardrails.
 */
@Slf4j
@CompileStatic
class LJEnergyCalculator implements Parametrized {

    // Cached constants for performance
    private final double RC
    private final double RON
    private final double MIN_R
    private final double RC_MINUS_RON
    private final double INV_RC_MINUS_RON
    private final double PI = Math.PI
    private final double PROBE_SIG
    private final double PROBE_EPS
    private final String POLICY
    private final double FALLBACK_SIG
    private final double FALLBACK_EPS

    // LJ parameters loaded from CSV: Element -> (sigma, epsilon)
    private final Map<Element, LJParams> ljParams

    // Counter for fallback/skip warnings (to avoid spam)
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
     * Initialize the calculator with parameters
     */
    LJEnergyCalculator(double probeSigma, double probeEpsilon,
                      double rc, double ron, double minR, String policy,
                      double fallbackSigma, double fallbackEpsilon) {

        // Validate numeric relationships
        if (ron >= rc || ron <= 0 || minR >= ron) {
            throw new IllegalArgumentException(
                "Invalid energy parameters: must have 0 < min_r < ron < rc. " +
                "Got: min_r=${minR}, ron=${ron}, rc=${rc}")
        }

        // Cache constants
        this.RC = rc
        this.RON = ron
        this.MIN_R = minR
        this.RC_MINUS_RON = rc - ron
        this.INV_RC_MINUS_RON = 1.0 / (rc - ron)
        this.PROBE_SIG = probeSigma
        this.PROBE_EPS = probeEpsilon
        this.POLICY = policy
        this.FALLBACK_SIG = fallbackSigma
        this.FALLBACK_EPS = fallbackEpsilon

        // Load LJ parameters from CSV
        this.ljParams = loadLJParamsFromCSV(Futils.readResource("/tables/energy/lj-params.csv"))

        log.info("LJEnergyCalculator initialized: rc={}, ron={}, min_r={}, probe=(σ={}, ε={}), {} elements loaded",
                 rc, ron, minR, probeSigma, probeEpsilon, ljParams.size())
    }

    /**
     * Load LJ parameters from CSV file
     */
    private Map<Element, LJParams> loadLJParamsFromCSV(String csvText) {
        Map<Element, LJParams> params = new HashMap<>()

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

        return Collections.unmodifiableMap(params)
    }

    /**
     * Compute the total vdW energy for a point given its neighbor atoms
     */
    double computeEnergyForPoint(Atom point, Atoms neighbours) {
        if (!neighbours) {
            return 0.0
        }

        double totalEnergy = 0.0

        for (Atom atom : neighbours) {
            // Intentionally ignore hydrogen atoms: vdW-only model + heavy-atom SAS is sufficient and faster; H LJ terms are negligible.

            // Get LJ parameters for this element
            LJParams atomParams = getLJParams(atom.getElement())
            if (atomParams == null) {
                // Handle according to policy
                if (POLICY == "skip") {
                    logMissingParamWarning(atom.getElement())
                    continue
                } else if (POLICY == "error") {
                    throw new IllegalStateException("Missing LJ parameters for element: ${atom.getElement()}")
                } else if (POLICY == "fallback") {
                    logMissingParamWarning(atom.getElement())
                    atomParams = new LJParams(FALLBACK_SIG, FALLBACK_EPS)
                }
            }

            // Calculate distance
            double r = Struct.dist(atom, point)
            if (r >= RC) {
                continue  // Beyond cutoff
            }

            // Clamp intersite distance: r = max(r, min_r) to avoid singularities when a SAS point is very close to an atom.
            r = Math.max(r, MIN_R)

            // Lorentz–Berthelot mixing:
            //   sigma_ij = (sigma_i + sigma_probe) / 2
            //   epsilon_ij = sqrt(epsilon_i * epsilon_probe)
            double sigmaIJ = (atomParams.sigma + PROBE_SIG) * 0.5
            double epsilonIJ = Math.sqrt(atomParams.epsilon * PROBE_EPS)

            // Cosine switching from R_on to R_c prevents cutoff artifacts:
            //   s(r) = 1 for r <= R_on; s(r) = 0.5 * (1 + cos(pi*(r - R_on)/(R_c - R_on))) for R_on < r < R_c; s(r) = 0 for r >= R_c.
            double switchValue = switchValue(r)

            // Compute LJ 12-6 energy
            // Avoid allocations in the hot loop; compute t^6, t^12 via multiplications for speed and numeric stability.
            double t = sigmaIJ / r
            double t2 = t * t
            double t6 = t2 * t2 * t2
            double t12 = t6 * t6
            double ljEnergy = 4.0 * epsilonIJ * (t12 - t6)

            totalEnergy += switchValue * ljEnergy
        }

        return totalEnergy
    }

    /**
     * Get LJ parameters for an element
     */
    private LJParams getLJParams(Element element) {
        return ljParams.get(element)
    }

    /**
     * Compute cosine switching function value
     */
    private double switchValue(double r) {
        if (r <= RON) {
            return 1.0
        } else {
            return 0.5 * (1.0 + Math.cos(PI * (r - RON) * INV_RC_MINUS_RON))
        }
    }

    /**
     * Log missing parameter warnings (rate-limited)
     */
    private void logMissingParamWarning(Element element) {
        if (missingParamWarningCount < MAX_WARNING_COUNT) {
            log.warn("Missing element parameters handled per policy '${POLICY}': ${element}")
            missingParamWarningCount++
            if (missingParamWarningCount == MAX_WARNING_COUNT) {
                log.warn("Further missing element parameter warnings will be suppressed")
            }
        }
    }
}
