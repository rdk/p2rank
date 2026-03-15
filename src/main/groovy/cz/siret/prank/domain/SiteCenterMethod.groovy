package cz.siret.prank.domain

import groovy.transform.CompileStatic

/**
 * Method for computing binding site centroid for evaluation (DCC criterion).
 *
 * @see cz.siret.prank.program.params.Params#site_eval_center_method
 */
@CompileStatic
enum SiteCenterMethod {

    /** Predefined centroid from the input site definition (only for explicitly defined sites) */
    explicit(false, true),

    /** Center of mass of ligand/residue atoms */
    atoms_center_of_mass(true, true),

    /** Centroid of SAS points around site atoms */
    sas_points_centroid(true, true),

    /** Geometric centroid of CA atoms of contact residues */
    ca_atoms_centroid(true, true),

    /** Geometric centroid of all protein contact atoms (only for ligand-defined sites) */
    contact_atoms_centroid(true, false)

    /** Supported for ligand-defined sites */
    final boolean supportedForLigandSites

    /** Supported for explicitly defined sites (ResidueSite) */
    final boolean supportedForExplicitSites

    SiteCenterMethod(boolean supportedForLigandSites, boolean supportedForExplicitSites) {
        this.supportedForLigandSites = supportedForLigandSites
        this.supportedForExplicitSites = supportedForExplicitSites
    }

    static SiteCenterMethod parse(String value) {
        try {
            return valueOf(value)
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unsupported site_eval_center_method: '${value}'. Supported values: ${values()*.name().join(', ')}")
        }
    }

}
