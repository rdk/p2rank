import cz.siret.prank.program.params.Params

/**
 * to override default params configuration (stored in default.groovy)
 * run program with
 *
 * prank.sh -c example.groovy ...
 *
 */
(params as Params).with {

    seed = 23

    threads = 2

    /**
     * produce pymol visualisations
     */
    visualizations = true

    /**
     * copy all protein pdb files to visualization folder (making visualizations portable)
     */
    vis_copy_proteins = true

}
