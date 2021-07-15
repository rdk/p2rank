package cz.siret.prank.domain.loaders.electrostatics

import cz.siret.prank.geom.Box
import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic

/**
 * Represents data loaded from Gaussian cube format (*.cube)
 *
 * Based on DelPhi output.
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

//===========================================================================================================//

    private transient Box boundingBox = null

    Point getOrigin() {
        Point.of(originX, originY, originZ)
    }

    Point getMaxPoint() {
        Point.of(
            originX + deltaX*(sizeX-1),
            originY + deltaY*(sizeY-1),
            originZ + deltaX*(sizeX-1)
        )
    }

    /**
     * Box bounded by origin and maxPoint with added margin = (deltaX/2,...)
     * data
     * @return
     */
    Box getBoundingBox() {
        if (boundingBox == null) {
            boundingBox = Box.boundedBy(origin, maxPoint).withMargins(deltaX/2d, deltaY/2d, deltaZ/2d)
        }
        return boundingBox
    }

}
