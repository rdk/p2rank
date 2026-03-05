package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Binding site defined as a set of residues.
 * Used as ground truth for site-based evaluation (other alternative is binding site defined by ligand).
 */
@Slf4j
@CompileStatic
class ResidueSite implements BindingSite, Parametrized {

    String name
    /** Centroid explicitly defined in the input site definition */
    Atom explicitCentroid
    List<Residue> residues
    Protein protein

    private Atoms cachedAtoms
    private Atoms sasPoints

    ResidueSite(String name, Atom explicitCentroid, List<Residue> residues, Protein protein) {
        assert !residues.isEmpty(), "ResidueSite must have at least one residue"

        this.name = name
        this.explicitCentroid = explicitCentroid
        this.residues = residues
        this.protein = protein
    }

    /**
     * Returns all atoms of the residues in this site.
     */
    @Override
    Atoms getAtoms() {
        if (cachedAtoms == null) {
            cachedAtoms = Atoms.union((List<Atoms>) residues*.atoms)
        }
        return cachedAtoms
    }

    /**
     * Predefined centroid of the site (from input).
     */
    @Override
    Atom getCentroid() {
        return explicitCentroid
    }

    /**
     * Returns the centroid used for evaluation, based on the site_centroid_method parameter.
     *
     * Possible methods:
     *   - explicit: predefined centroid from input site definition
     *   - sas_points_center_of_mass: center of mass of SAS points around site residues
     *   - residue_atoms_center_of_mass: center of mass of all residue atoms
     */
    @Override
    Atom getCentroidForEval() {
        SiteCentroidMethod method = SiteCentroidMethod.parse(params.site_centroid_method)
        if (!method.supportedForExplicitSites) {
            throw new IllegalArgumentException("site_centroid_method '${method}' is not supported for explicitly defined sites")
        }
        switch (method) {
            case SiteCentroidMethod.explicit_centroid:
                return explicitCentroid
            case SiteCentroidMethod.sas_points_center_of_mass:
                return getSasPoints().centerOfMass
            case SiteCentroidMethod.atoms_center_of_mass:
                return getAtoms().centerOfMass
            default:
                throw new IllegalArgumentException("Unsupported site_centroid_method: '${method}'")
        }
    }

    /**
     * Returns the centroid of the site, calculated from site residues as a center of mass of the SAS points defined by the residues.
     *
     * This is more consistent with ligand-based centroid definition than calculating center of mass of all residue atoms which is
     * (in majority of cases) in the empty space around protein surface.
     */
    Atom calcCentroidFromResidues() {
        return getSasPoints().centerOfMass
    }

    @Override
    Atoms getSasPoints() {
        if (sasPoints == null) {
            sasPoints = protein.accessibleSurface.points.cutoutShell(getAtoms(), params.getSasCutoffDist())
        }
        return sasPoints
    }

    @Override
    String getLabel() {
        return name
    }

    @Override
    String toString() {
        return "site $name residues:${residues.size()}"
    }

}
