package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
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

    /** Key for storing {@link cz.siret.prank.domain.loaders.AhojSiteInfo} in {@link #secondaryData} */
    static final String KEY_AHOJ_SITE_INFO = "ahoj_site_info"

    String name
    /** Centroid explicitly defined in the input site definition */
    Atom explicitCenter
    List<Residue> residues
    Protein protein

    /** Extensible metadata storage (analogous to {@link Protein#secondaryData}) */
    Map<String, Object> secondaryData = new HashMap<>()

    private Atoms cachedAtoms
    Atoms sasPoints
    Pocket predictedPocket

    ResidueSite(String name, Atom explicitCenter, List<Residue> residues, Protein protein) {
        assert !residues.isEmpty(), "ResidueSite must have at least one residue"

        this.name = name
        this.explicitCenter = explicitCenter
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
        return explicitCenter
    }

    /**
     * Returns the centroid used for evaluation, based on the site_eval_center_method parameter.
     * @see SiteCenterMethod
     */
    @Override
    Atom getCenterForEval() {
        SiteCenterMethod method = SiteCenterMethod.parse(params.site_eval_center_method)
        if (!method.supportedForExplicitSites) {
            throw new IllegalArgumentException("site_eval_center_method '${method}' is not supported for explicitly defined sites")
        }
        return getCenterForMethod(method)
    }

    @Override
    Atom getCenterForMethod(SiteCenterMethod method) {
        switch (method) {
            case SiteCenterMethod.explicit:
                return explicitCenter
            case SiteCenterMethod.sas_points_centroid:
                return getSasPoints().centroid
            case SiteCenterMethod.atoms_center_of_mass:
                return getAtoms().centerOfMass
            case SiteCenterMethod.ca_atoms_centroid:
                return Struct.calcCaCentroid(residues)
            default:
                return null
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
