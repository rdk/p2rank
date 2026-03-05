package cz.siret.prank.domain

import groovy.transform.CompileStatic

/**
 * Method for computing binding site centroid for evaluation (DCC criterion).
 */
@CompileStatic
enum SiteCentroidMethod {

    /** Predefined centroid from the input site definition (only for explicitly defined sites) */
    explicit_centroid(false, true),

    /** Center of mass of ligand/residue atoms */
    atoms_center_of_mass(true, true),

    /** Center of mass of SAS points around site atoms */
    sas_points_center_of_mass(true, true)

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
                "Unsupported site_centroid_method: '${value}'. Supported values: ${values()*.name().join(', ')}")
        }
    }

}
