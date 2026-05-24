package cz.siret.prank.features.implementation.physics

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for the centrality algorithms in ContactGraph. We bypass the
 * atom-distance edge-construction step and feed adjacency lists directly into
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

    // ===== Brandes =====

    @Test
    void betweennessStarGraph() {
        // 5-node star: 0 is center, 1..4 are leaves.
        // Center: passes through every shortest path between leaf pairs → CB(0) = 1.0
        // Leaves: CB = 0.
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 0, 2); addEdge(adj, 0, 3); addEdge(adj, 0, 4)

        double[] cb = ContactGraph.computeBetweenness(adj, 5)
        assertEquals(1.0d, cb[0], 1e-9, "star center should have normalized betweenness 1.0")
        for (int i = 1; i < 5; i++) {
            assertEquals(0d, cb[i], 1e-9, "leaf $i should have betweenness 0")
        }
    }

    @Test
    void betweennessPathGraph() {
        // 5-node path 0-1-2-3-4. Middle node (2) sits on most shortest paths.
        // By Brandes convention, normalized CB(2) for a P5 should be the max.
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 1, 2); addEdge(adj, 2, 3); addEdge(adj, 3, 4)

        double[] cb = ContactGraph.computeBetweenness(adj, 5)
        // Endpoints: 0
        assertEquals(0d, cb[0], 1e-9)
        assertEquals(0d, cb[4], 1e-9)
        // Middle node strictly highest, symmetric around 2
        assertTrue(cb[2] > cb[1])
        assertTrue(cb[2] > cb[3])
        assertEquals(cb[1], cb[3], 1e-9, "path graph centrality should be symmetric")
        // Closed-form: raw pair-passes for node 2 = 4 unordered pairs (0-3, 0-4, 1-3, 1-4)
        // Normalization 2/((n-1)(n-2)) = 2/12 = 1/6 → CB(2) = 4/6 = 2/3
        assertEquals(2d / 3d, cb[2], 1e-9)
    }

    @Test
    void betweennessIsolatedAndSmall() {
        // 2-node graph: no betweenness defined → all zeros (n<3 short-circuit).
        List<List<Integer>> adj = emptyAdj(2)
        addEdge(adj, 0, 1)
        double[] cb = ContactGraph.computeBetweenness(adj, 2)
        assertEquals(0d, cb[0])
        assertEquals(0d, cb[1])
    }

    // ===== Closeness =====

    @Test
    void closenessStarGraph() {
        // Center distance to each leaf = 1, sum = 4, n_comp = 5 → CC(0) = 4/4 = 1.
        // Leaf: dist to center 1 + dist to other leaves 2×3 = 7, CC = 4/7.
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 0, 2); addEdge(adj, 0, 3); addEdge(adj, 0, 4)

        double[] cc = ContactGraph.computeClosenessByComponent(adj, 5)
        assertEquals(1.0d, cc[0], 1e-9, "star center closeness")
        for (int i = 1; i < 5; i++) {
            assertEquals(4d / 7d, cc[i], 1e-9, "star leaf $i closeness")
            assertTrue(cc[0] > cc[i], "center should be more central than leaves")
        }
    }

    @Test
    void closenessPathGraph() {
        // P5: 0-1-2-3-4. Distances from middle (2): 2+1+1+2 = 6, CC(2) = 4/6 = 2/3.
        // From endpoint (0): 1+2+3+4 = 10, CC(0) = 4/10 = 0.4. Middle > endpoint.
        List<List<Integer>> adj = emptyAdj(5)
        addEdge(adj, 0, 1); addEdge(adj, 1, 2); addEdge(adj, 2, 3); addEdge(adj, 3, 4)

        double[] cc = ContactGraph.computeClosenessByComponent(adj, 5)
        assertEquals(2d / 3d, cc[2], 1e-9)
        assertEquals(0.4d, cc[0], 1e-9)
        assertEquals(cc[0], cc[4], 1e-9)
        assertEquals(cc[1], cc[3], 1e-9)
        assertTrue(cc[2] > cc[1])
        assertTrue(cc[1] > cc[0])
    }

    @Test
    void closenessNormalizedWithinComponent() {
        // Two disconnected components: triangle {0,1,2} and edge {3,4}.
        // Triangle: every pair distance 1, sum = 2, n_comp = 3 → CC = 2/2 = 1.
        // Edge:     n_comp = 2, sum = 1 → CC = 1/1 = 1.
        // Singleton in a third component (5): CC = 0 (defined as 0 for isolated).
        List<List<Integer>> adj = emptyAdj(6)
        addEdge(adj, 0, 1); addEdge(adj, 1, 2); addEdge(adj, 0, 2)
        addEdge(adj, 3, 4)
        // node 5 isolated

        double[] cc = ContactGraph.computeClosenessByComponent(adj, 6)
        for (int i = 0; i < 3; i++) assertEquals(1.0d, cc[i], 1e-9, "triangle node $i")
        assertEquals(1.0d, cc[3], 1e-9, "edge endpoint 3")
        assertEquals(1.0d, cc[4], 1e-9, "edge endpoint 4")
        assertEquals(0d, cc[5], 1e-9, "isolated singleton")
    }

    @Test
    void closenessIsolatedNode() {
        // Single isolated node in a larger graph still gets 0.
        List<List<Integer>> adj = emptyAdj(3)
        addEdge(adj, 0, 1)
        double[] cc = ContactGraph.computeClosenessByComponent(adj, 3)
        assertTrue(cc[0] > 0d)
        assertTrue(cc[1] > 0d)
        assertEquals(0d, cc[2], 1e-9, "isolated node 2")
    }
}
