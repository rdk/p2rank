package cz.siret.prank.domain.loaders.electrostatics

import cz.siret.prank.geom.Point

/**
 * Represents data loaded from Gaussian cube format (*.cube)
 *
 * Based on DelPhi output.
 *
 */
class GaussianCube {

    Point origin

    int nx
    int ny
    int nz

    double dx
    double dy
    double dz

    List<String> header

    float[][][] data


}
