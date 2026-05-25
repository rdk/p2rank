package cz.siret.prank.features.implementation.physics

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for the centrality algorithms in ContactGraph. We bypass the
 * atom-distance edge-construction step and feed adjacency arrays directly into
 * computeBetweenness / computeClosenessByComponent.
 */
class ContactGraphTest {

    private static List<List<Integer>> emptyAdj(int n) {
        List<List<Integer>> a = new ArrayList<>(n)
        for (int i = 0; i < n; i++) a.add(new ArrayList<Integer>())
        return a
    }

    private static void addEdge(List<List<Integer>> a, int u, int v) {
        a.get(u).add(v)
        a.get(v).add(u)
    }

    private static int[][] toIntArrays(List<List<Integer>> a) {
        int[][] result = new int[a.size()][]
        for (int i = 0; i < a.size(); i++) {
            List<Integer> row = a.get(i)
            result[i] = new int[row.size()]
            for (int j = 0; j < row.size(); j++) result[i][j] = row.get(j)
        }
        return result
    }

    // ===== Brandes =====

    @Test
    void betweennessStarGraph() {
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 0, 2); addEdge(adj, 0, 3); addEdge(adj, 0, 4)

        double[] cb = ContactGraph.computeBetweenness(toIntArrays(adj), 5)
        assertEquals(1.0d, cb[0], 1e-9, "star center should have normalized betweenness 1.0")
        for (int i = 1; i < 5; i++) {
            assertEquals(0d, cb[i], 1e-9, "leaf $i should have betweenness 0")
        }
    }

    @Test
    void betweennessPathGraph() {
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 1, 2); addEdge(adj, 2, 3); addEdge(adj, 3, 4)

        double[] cb = ContactGraph.computeBetweenness(toIntArrays(adj), 5)
        assertEquals(0d, cb[0], 1e-9)
        assertEquals(0d, cb[4], 1e-9)
        assertTrue(cb[2] > cb[1])
        assertTrue(cb[2] > cb[3])
        assertEquals(cb[1], cb[3], 1e-9, "path graph centrality should be symmetric")
        assertEquals(2d / 3d, cb[2], 1e-9)
    }

    @Test
    void betweennessIsolatedAndSmall() {
        List<List<Integer>> adj = emptyAdj(2)
        addEdge(adj, 0, 1)
        double[] cb = ContactGraph.computeBetweenness(toIntArrays(adj), 2)
        assertEquals(0d, cb[0])
        assertEquals(0d, cb[1])
    }

    // ===== Closeness =====

    @Test
    void closenessStarGraph() {
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 0, 2); addEdge(adj, 0, 3); addEdge(adj, 0, 4)

        double[] cc = ContactGraph.computeClosenessByComponent(toIntArrays(adj), 5)
        assertEquals(1.0d, cc[0], 1e-9, "star center closeness")
        for (int i = 1; i < 5; i++) {
            assertEquals(4d / 7d, cc[i], 1e-9, "star leaf $i closeness")
            assertTrue(cc[0] > cc[i], "center should be more central than leaves")
        }
    }

    @Test
    void closenessPathGraph() {
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 1, 2); addEdge(adj, 2, 3); addEdge(adj, 3, 4)

        double[] cc = ContactGraph.computeClosenessByComponent(toIntArrays(adj), 5)
        assertEquals(2d / 3d, cc[2], 1e-9)
        assertEquals(0.4d, cc[0], 1e-9)
        assertEquals(cc[0], cc[4], 1e-9)
        assertEquals(cc[1], cc[3], 1e-9)
        assertTrue(cc[2] > cc[1])
        assertTrue(cc[1] > cc[0])
    }

    @Test
    void closenessNormalizedWithinComponent() {
        List<List<Integer>> adj = emptyAdj(6)
        addEdge(adj, 0, 1); addEdge(adj, 1, 2); addEdge(adj, 0, 2)
        addEdge(adj, 3, 4)

        double[] cc = ContactGraph.computeClosenessByComponent(toIntArrays(adj), 6)
        for (int i = 0; i < 3; i++) assertEquals(1.0d, cc[i], 1e-9, "triangle node $i")
        assertEquals(1.0d, cc[3], 1e-9, "edge endpoint 3")
        assertEquals(1.0d, cc[4], 1e-9, "edge endpoint 4")
        assertEquals(0d, cc[5], 1e-9, "isolated singleton")
    }

    @Test
    void closenessIsolatedNode() {
        List<List<Integer>> adj = emptyAdj(3)
        addEdge(adj, 0, 1)
        double[] cc = ContactGraph.computeClosenessByComponent(toIntArrays(adj), 3)
        assertTrue(cc[0] > 0d)
        assertTrue(cc[1] > 0d)
        assertEquals(0d, cc[2], 1e-9, "isolated node 2")
    }
}
