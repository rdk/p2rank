package cz.siret.prank.features.weight

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.MathUtils
import groovy.transform.CompileStatic

/**
 * Distance weight functions.
 */
@CompileStatic
abstract class WeightFun implements Parametrized {

    static enum Option { ONE, OLD, NEW, GAUSS, INV, INV2, INVPOW, INVPOW2 }

    abstract double weight(double dist);

    static WeightFun create(String name) {
        createFunction(Option.valueOf(name))
    }
    
    static WeightFun createFunction(Option option) {
        // creating new instances since they are parametrized
        switch (option)  {
            case Option.ONE   : return new One()
            case Option.NEW   : return new New()
            case Option.OLD   : return new Old()
            case Option.GAUSS : return new Gauss()
            case Option.INV   : return new Inv()
            case Option.INV2  : return new Inv2()
            case Option.INVPOW  : return new InvPow()
            case Option.INVPOW2  : return new InvPow2()
        }
    }

    static class One extends WeightFun {
        double weight(double dist) {
            return 1
        }
    }

    static class New extends WeightFun {
        double base = params.point_min_distfrom_protein
        double weight(double dist) {
            calcWeightNew(dist, base)
        }
        double calcWeightNew(double dist, double base) {
            if (dist<=base) {
                return 1
            } else {
                return (base*base)/(dist*dist)
            }
        }
    }

    static class Old extends WeightFun {
        final double MIN_DIST = params.weight_dist_param
        double exp = params.weight_power

        double weight(double dist) {
            double weight
            if (dist <= MIN_DIST) {
                weight = 1
            } else {
                weight = MIN_DIST/dist
            }
            weight = Math.pow(weight, exp)
            return weight
        }
    }

    static class Gauss extends WeightFun {
        double mean = params.point_min_distfrom_protein
        double sigma = params.weight_sigma

        double weight(double dist) {
            return MathUtils.gaussNorm(dist-mean, sigma)
        }
    }

    static class Inv extends WeightFun {
        double rmax = params.neighbourhood_radius

        double weight(double dist) {
            return 1 - dist/rmax
        }
    }

    static class InvPow extends WeightFun {
        double rmax = params.neighbourhood_radius
        double exp = params.weight_power

        double weight(double dist) {
            double w = 1 - dist/rmax
            w = Math.pow(w, exp)
            return w
        }
    }

    static class Inv2 extends WeightFun {
        double rmin = params.solvent_radius
        double rmax = params.neighbourhood_radius

        double weight(double dist) {
            return (rmax-dist)/(rmax-rmin)
        }
    }

    static class InvPow2 extends WeightFun {
        double rmin = params.weight_dist_param 
        double rmax = params.neighbourhood_radius
        double exp = params.weight_power
        
        double weight(double dist) {
            if (dist < rmin)
                dist = rmin
            double w = (rmax-dist)/(rmax-rmin)
            w = Math.pow(w, exp)
            return w
        }
    }

}
