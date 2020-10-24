package cz.siret.prank.domain.loaders.electrostatics


import groovy.transform.CompileStatic

/**
 * Represents data loaded from Gaussian cube format (*.cube)
 *
 * Based on DelPhi output.
 *
 */
@CompileStatic
class GaussianCube implements Serializable {

    static final long serialVersionUID = 1L;

    double originX
    double originY
    double originZ

    int sizeX
    int sizeY
    int sizeZ

    double deltaX
    double deltaY
    double deltaZ

    List<String> header

    float[][][] data


}
