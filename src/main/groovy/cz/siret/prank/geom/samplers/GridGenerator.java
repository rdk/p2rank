package cz.siret.prank.geom.samplers;

import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.Box;
import cz.siret.prank.geom.Point;
import cz.siret.prank.geom.Struct;
import cz.siret.prank.program.routines.predict.output.grid.VdwRadiusTable;
import org.biojava.nbio.structure.Atom;

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
        // Guard against NaN/Inf propagation from broken PDBs: IEEEremainder(NaN, edge) = NaN,
        // which would otherwise make every lattice point NaN. Lives in the constructor so
        // every entry point (forBox, sampleGridPointsBetween, sampleGridPointsAroundAtoms)
        // is covered without per-caller checks.
        if (!isFiniteBox(box)) {
            throw new IllegalArgumentException(
                    "GridGenerator: non-finite bounding box " +
                    "(min=" + box.getMin() + ", max=" + box.getMax() + "). " +
                    "Check input structure for NaN/Inf coordinates.");
        }

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

    private static boolean isFiniteBox(Box box) {
        return Double.isFinite(box.getMin().getX()) && Double.isFinite(box.getMin().getY())
            && Double.isFinite(box.getMin().getZ()) && Double.isFinite(box.getMax().getX())
            && Double.isFinite(box.getMax().getY()) && Double.isFinite(box.getMax().getZ());
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

    /**
     * Samples lattice points that lie inside a shell around the protein
     * {@code atoms}: within {@code maxDist} of the nearest atom (outer bound)
     * but outside its van-der-Waals shell plus {@code atomBuffer} (inner bound).
     * A single point set ({@code atoms}, i.e. protein + cofactor heavy atoms)
     * gates both bounds, so the grid covers a shell around the whole protein,
     * not just the vicinity of predicted pockets.
     *
     * <p>Per lattice cell, find the nearest atom via {@link Atoms#findNearest}, then:
     * <ul>
     *   <li><b>Outer bound:</b> drop the cell if it is farther than {@code maxDist}
     *       from that atom.</li>
     *   <li><b>Inner bound:</b> drop the cell if it is closer than
     *       {@code vdw_radius(nearest) + atomBuffer}, keeping the grid out of
     *       physical atom volume.</li>
     * </ul>
     *
     * <p>VdW radii come from {@link VdwRadiusTable}, which falls back to Krypton
     * (2.02 Å) for elements that have a null radius in CDK's {@code Elements} enum.
     *
     * <p>Returns a {@link GridSample} carrying the kept points plus the origin the
     * sampler used — callers that need to compute lattice coords downstream read
     * the origin from there instead of recomputing the same box / shift pipeline.
     * The kept-points set is empty when {@code atoms} is empty.
     *
     * <p>Note: per-pocket assignment downstream still uses {@code Pocket.sasPoints}
     * (via {@code pocket_grid_assign_cutoff}); only the grid extent is atom-driven.
     */
    public static GridSample sampleGridPointsBetween(Atoms atoms,
                                                     double edge, double maxDist, double atomBuffer) {
        if (atoms == null || atoms.isEmpty()) return new GridSample(new Atoms(), 0d, 0d, 0d);

        // findNearest builds the kdtree lazily on first query; build it conditionally up front
        // so the box pass and the per-cell pass share one tree without paying for tiny inputs.
        atoms.withKdTreeConditional();

        Box box = Box.aroundAtoms(atoms).withMargin(maxDist);
        GridGenerator grid = GridGenerator.forBox(box, edge);  // ctor guards against NaN/Inf box

        Atoms res = new Atoms(grid.getCount() / 4);
        for (Point p : grid) {
            Atom nearest = atoms.findNearest(p);
            double dist = Struct.dist(nearest, p);
            if (dist > maxDist) continue;
            if (dist < VdwRadiusTable.get(nearest) + atomBuffer) continue;
            res.add(p.copy());
        }

        return new GridSample(res, grid.originX, grid.originY, grid.originZ);
    }

}
