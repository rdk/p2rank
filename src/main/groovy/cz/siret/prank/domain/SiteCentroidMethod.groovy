package cz.siret.prank.domain

import groovy.transform.CompileStatic

/**
 * Method for computing binding site centroid for evaluation (DCC criterion).
 */
@CompileStatic
enum SiteCentroidMethod {

    /** Predefined centroid from the input site definition (only for explicitly defined sites) */
    explicit(false, true),

    /** Center of mass of ligand/residue atoms */
    atoms_center_of_mass(true, true),

    /** Centroid of SAS points around site atoms */
    sas_points_centroid(true, true)

    /** Supported for ligand-defined sites */
    final boolean supportedForLigandSites

    /** Supported for explicitly defined sites (ResidueSite) */
    final boolean supportedForExplicitSites

    SiteCentroidMethod(boolean supportedForLigandSites, boolean supportedForExplicitSites) {
        this.supportedForLigandSites = supportedForLigandSites
        this.supportedForExplicitSites = supportedForExplicitSites
    }

    static SiteCentroidMethod parse(String value) {
        try {
            return valueOf(value)
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unsupported site_eval_center_method: '${value}'. Supported values: ${values()*.name().join(', ')}")
        }
    }

}
