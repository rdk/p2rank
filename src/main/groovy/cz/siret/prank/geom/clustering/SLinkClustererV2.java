package cz.siret.prank.geom.clustering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Single-linkage clusterer using Union-Find with path compression and union by rank.
 * O(N² α(N)) ≈ O(N²) — eliminates the O(N) relabeling loop of SLinkClusterer (O(N³)).
 */
public class SLinkClustererV2<E> implements Clusterer<E> {

    private static final Logger log = LoggerFactory.getLogger(SLinkClustererV2.class);

    private int[] parent;
    private int[] rank;

    private int find(int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]; // path halving
            x = parent[x];
        }
        return x;
    }

    private void union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra == rb) return;
        if (rank[ra] < rank[rb]) {
            parent[ra] = rb;
        } else if (rank[ra] > rank[rb]) {
            parent[rb] = ra;
        } else {
            parent[rb] = ra;
            rank[ra]++;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<E>> cluster(List<E> elements, double minDist, Clusterer.Distance<E> distDef) {
        if (elements.isEmpty()) return Collections.emptyList();
        if (elements.size() == 1) return new ArrayList<>(Collections.singletonList(elements));

        Object[] els = elements.toArray();
        int N = els.length;

        parent = new int[N];
        rank = new int[N];
        for (int i = 0; i < N; i++) {
            parent[i] = i;
        }

        // Check all pairs; merge if within minDist (same iteration order as V1)
        for (int j = N - 1; j >= 1; j--) {
            for (int i = j - 1; i >= 0; i--) {
                if (find(i) != find(j)) {
                    double dist = distDef.dist((E) els[i], (E) els[j]);
                    if (dist <= minDist) {
                        union(i, j);
                    }
                }
            }
        }

        // Collect clusters
        Map<Integer, List<E>> clusterMap = new LinkedHashMap<>();
        for (int i = 0; i < N; i++) {
            int root = find(i);
            clusterMap.computeIfAbsent(root, k -> new ArrayList<>()).add((E) els[i]);
        }

        List<List<E>> result = new ArrayList<>(clusterMap.values());

        log.info("clusters ({}): sizes {}", result.size(), result.stream().map(List::size).toList());
        log.info("clusters together: {} / {}", result.stream().mapToInt(List::size).sum(), elements.size());

        return result;
    }
}
