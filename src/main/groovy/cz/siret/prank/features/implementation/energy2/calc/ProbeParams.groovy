package cz.siret.prank.features.implementation.energy2.calc

import groovy.transform.CompileStatic

/**
 * Parameters for a specific probe type
 */
@CompileStatic
class ProbeParams {
    final double ljSigma        // LJ sigma parameter (Å)
    final double ljEpsilon      // LJ epsilon parameter (kcal/mol)
    final double hbR0           // H-bond equilibrium distance (Å)
    final double hbEpsilon      // H-bond well depth (kcal/mol)
    final double ljWeight       // Weight for LJ background in HB probes
    final double charge         // Probe charge (e)
    final double energyMinCap   // Energy minimum cap (kcal/mol)

    ProbeParams(Map args) {
        this.ljSigma = args.ljSigma as double
        this.ljEpsilon = args.ljEpsilon as double
        this.hbR0 = args.hbR0 as double
        this.hbEpsilon = args.hbEpsilon as double
        this.ljWeight = args.ljWeight as double
        this.charge = args.charge as double
        this.energyMinCap = args.energyMinCap as double
    }
}
