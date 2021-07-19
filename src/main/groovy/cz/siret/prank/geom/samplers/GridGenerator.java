package cz.siret.prank.geom.samplers;

import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.Box;
import cz.siret.prank.geom.Point;

import javax.annotation.Nonnull;
import java.util.Iterator;

public class GridGenerator implements Iterable<Point> {

    private double edge;
    private double originX;
    private double originY;
    private double originZ;
    private int nx;
    private int ny;
    private int nz;

    private GridGenerator() {}

    public GridGenerator(double edge, double originX, double originY, double originZ, int nx, int ny, int nz) {
        this.edge = edge;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public static double shift(double min, double max, double edge) {
        return min + Math.IEEEremainder(max - min, edge);
    }

//===============================================================================================//

    public GridGenerator(Box box, double edge) {
        this.edge = edge;

        originX = shift(box.getMin().getX(), box.getMax().getX(), edge);
        originY = shift(box.getMin().getY(), box.getMax().getY(), edge);
        originZ = shift(box.getMin().getZ(), box.getMax().getZ(), edge);

        nx = (int) (box.getWx() / edge);
        ny = (int) (box.getWy() / edge);
        nz = (int) (box.getWz() / edge);
    }

    public static GridGenerator forBox(Box box, double edge) {
        return new GridGenerator(box, edge);
    }

//===============================================================================================//

    public int getCount() {
        return nx * ny * nz;
    }

    /**
     * @return points to flyweight Point, to use it further use point.copy()
     */
    @Nonnull
    public Iterator<Point> iterator() {
        return new Iterator<Point>() {

            private final Point resPoint = new Point();

            private int x = 0;
            private int y = 0;
            private int z = 0;

            public boolean hasNext() {
                return z < nz;
            }

            public Point next() {

                resPoint.setXYZ(
                        originX + x * edge,
                        originY + y * edge,
                        originZ + z * edge
                );

                x++;
                if (x >= nx) {
                    x = 0;
                    y++;
                    if (y >= ny) {
                        y = 0;
                        z++;
                    }
                }

                return resPoint;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    /**
     * SNAKE PATH: distance between two subsequent points is always exactly equal to one edge
     */
    public Iterator<Point> iteratorSnake() {
        return new Iterator<Point>() {

            private final Point resPoint = new Point();

            private int x = 0;
            private int y = 0;
            private int z = 0;

            private int xinc = +1;
            private int yinc = +1;

            public boolean hasNext() {
                return z < nz;
            }

            public Point next() {

                resPoint.setXYZ(
                        originX + x * edge,
                        originY + y * edge,
                        originZ + z * edge
                );

                x += xinc;
                if (x >= nx || x < 0) {
                    xinc *= -1; // turn backwards
                    x += xinc;  // back to grid

                    y += yinc;
                    if (y >= ny || y < 0) {
                        yinc *= -1;
                        y += yinc;
                        z++;
                    }
                }

                return resPoint;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

//===============================================================================================//

    static Atoms sampleGridPointsAroundAtoms(Atoms atoms, double edge, double radius) {
        atoms.withKdTreeConditional();

        Box box = Box.aroundAtoms(atoms).withMargin(radius);
        GridGenerator grid = GridGenerator.forBox(box, edge);

        double sqrRadius = radius*radius;
        Atoms res = new Atoms(grid.getCount() / 2);
        for (Point p : grid) {
            if (atoms.sqrDist(p) <= sqrRadius) {
                res.add(p.copy()); // copy flyweight
            }
        }

        return res;
    }

}
