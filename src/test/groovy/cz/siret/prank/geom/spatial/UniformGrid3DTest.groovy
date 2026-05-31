package cz.siret.prank.geom.spatial

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for the reusable UniformGrid3D spatial hash.
 *
 * The fixed-radius existence query is checked against brute force on random
 * point clouds (including the radius == cellSize boundary), plus edge cases.
 */
@CompileStatic
class UniformGrid3DTest {

    @Test
    void hasAnyWithin_matchesBruteForce() {
        Random rnd = new Random(7)
        int n = 2000
        double cell = 1.0d
        double r = cell          // boundary: radius == cellSize (the 3x3x3 limit)
        double r2 = r * r

        double[][] pts = new double[n][3]
        UniformGrid3D<Integer> grid = new UniformGrid3D<>(cell)
        for (int i = 0; i < n; i++) {
            double x = (rnd.nextDouble() - 0.5d) * 40d
            double y = (rnd.nextDouble() - 0.5d) * 40d
            double z = (rnd.nextDouble() - 0.5d) * 40d
            pts[i] = [x, y, z] as double[]
            grid.insert(x, y, z, i)
        }
        assertEquals(n, grid.size())

        for (int q = 0; q < 500; q++) {
            double x = (rnd.nextDouble() - 0.5d) * 40d
            double y = (rnd.nextDouble() - 0.5d) * 40d
            double z = (rnd.nextDouble() - 0.5d) * 40d
            boolean brute = false
            for (int i = 0; i < n; i++) {
                double dx = pts[i][0] - x, dy = pts[i][1] - y, dz = pts[i][2] - z
                if (dx * dx + dy * dy + dz * dz <= r2) { brute = true; break }
            }
            assertEquals(brute, grid.hasAnyWithin(x, y, z, r), "query $q at radius==cellSize")
        }
    }

    @Test
    void handlesNegativeCoords() {
        UniformGrid3D<String> grid = new UniformGrid3D<>(2.0d)
        grid.insert(-100.0d, -100.0d, -100.0d, "a")
        assertTrue(grid.hasAnyWithin(-100.5d, -100.5d, -100.5d, 1.0d))
        assertFalse(grid.hasAnyWithin(100.0d, 100.0d, 100.0d, 2.0d))
    }

    @Test
    void forEachWithin_visitsAllMatches() {
        UniformGrid3D<String> grid = new UniformGrid3D<>(1.0d)
        grid.insert(0.0d, 0.0d, 0.0d, "a")
        grid.insert(0.1d, 0.0d, 0.0d, "b")
        grid.insert(5.0d, 5.0d, 5.0d, "far")
        List<String> hits = new ArrayList<>()
        grid.forEachWithin(0.0d, 0.0d, 0.0d, 0.5d, { hits.add(it) })
        assertEquals(2, hits.size())
        assertTrue(hits.contains("a") && hits.contains("b"))
    }

    @Test
    void emptyGrid() {
        UniformGrid3D<String> grid = new UniformGrid3D<>(1.0d)
        assertEquals(0, grid.size())
        assertFalse(grid.hasAnyWithin(0.0d, 0.0d, 0.0d, 1.0d))
    }

    @Test
    void rejectsRadiusBeyondCell() {
        UniformGrid3D<String> grid = new UniformGrid3D<>(1.0d)
        grid.insert(0.0d, 0.0d, 0.0d, "a")
        assertThrows(IllegalArgumentException.class, { grid.hasAnyWithin(0.0d, 0.0d, 0.0d, 1.5d) })
    }

    @Test
    void rejectsNonPositiveCellSize() {
        assertThrows(IllegalArgumentException.class, { new UniformGrid3D<String>(0.0d) })
    }
}
