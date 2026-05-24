package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Per-protein residue contact graph plus the three derived centrality measures
 * (betweenness, closeness, degree). Built once per Protein in preProcessProtein
 * and cached in Protein.secondaryData[CACHE_KEY].
 *
 * Edges: two residues are connected if any pair of their heavy atoms lies
 * within Params.feat_cgraph_cutoff (Å). Nodes are all residues from
 * Protein.residues, in their natural list order.
 *
 * Protein structure networks and node degree:
 *   Brinda, K.V. & Vishveshwara, S. (2005). A Network Representation of Protein
 *   Structures: Implications for Protein Stability. Biophys. J. 89(6), 4159-4170.
 *   https://doi.org/10.1529/biophysj.105.064485
 *
 * Centrality of functional residues:
 *   Amitai, G. et al. (2004). Network Analysis of Protein Structures Identifies
 *   Functional Residues. J. Mol. Biol. 344(4), 1135-1146.
 *   https://doi.org/10.1016/j.jmb.2004.10.055
 *
 * Adaptation in P2Rank: graph is unweighted; closeness is normalized within
 * each connected component (CC_i = (n_comp − 1) / Σ_{j in comp} d(i,j)) so
 * multi-chain or fragmented structures don't punish small components with
 * artificial zeros. Betweenness uses standard Brandes; degree is the
 * adjacency-list cardinality.
 */
@Slf4j
@CompileStatic
class ContactGraph {

    private static final String CACHE_KEY = "contact_graph"

    final Map<Residue.Key, Integer> indexByKey
    final double[] betweenness
    final double[] closeness
    final double[] degree

    private ContactGraph(Map<Residue.Key, Integer> indexByKey,
                         double[] betweenness, double[] closeness, double[] degree) {
        this.indexByKey = indexByKey
        this.betweenness = betweenness
        this.closeness = closeness
        this.degree = degree
    }

    double betweennessFor(Residue r) { Integer i = indexByKey.get(r.key); i == null ? 0d : betweenness[i] }
    double closenessFor(Residue r)   { Integer i = indexByKey.get(r.key); i == null ? 0d : closeness[i] }
    double degreeFor(Residue r)      { Integer i = indexByKey.get(r.key); i == null ? 0d : degree[i] }

    static ContactGraph getOrCompute(Protein protein, Params params) {
        (ContactGraph) protein.secondaryData.computeIfAbsent(CACHE_KEY, { k -> compute(protein, params) })
    }

    //---------------------------------------------------------------------//
    //  Build
    //---------------------------------------------------------------------//

    private static ContactGraph compute(Protein protein, Params params) {
        long t0 = System.currentTimeMillis()

        List<Residue> residues = protein.residues.list
        int n = residues.size()
        Map<Residue.Key, Integer> indexByKey = new HashMap<>(n)
        for (int i = 0; i < n; i++) indexByKey.put(residues.get(i).key, i)

        if (n == 0) return new ContactGraph(indexByKey, new double[0], new double[0], new double[0])

        // adjacency list — use ArrayLists of boxed ints because n×heavyAtom pair
        // tests dominate cost anyway; structure cost is negligible.
        List<List<Integer>> adj = new ArrayList<>(n)
        for (int i = 0; i < n; i++) adj.add(new ArrayList<Integer>())

        // Atom-level cutoff query. Atoms.areWithinDistance lazily builds a KD-tree.
        // TODO(perf, large N): the per-residue KD-tree is only built when an
        // Atoms set exceeds Atoms.KD_TREE_THRESHOLD (15), which most single
        // residues miss — so this falls through to brute-force pair scans.
        // For N≳400 residues, replacing this loop with a single protein-wide
        // KD-tree of all heavy atoms + per-atom radius query (dedup'd to
        // residue indices) reduces work ~O(N²·k²) → ~O(N·k·log(N·k)).
        List<Atoms> resAtoms = new ArrayList<>(n)
        for (Residue r : residues) resAtoms.add(r.atoms)

        double cutoff = params.feat_cgraph_cutoff
        for (int i = 0; i < n; i++) {
            Atoms ai = resAtoms.get(i)
            if (ai.count == 0) continue
            for (int j = i + 1; j < n; j++) {
                Atoms aj = resAtoms.get(j)
                if (aj.count == 0) continue
                if (ai.areWithinDistance(aj, cutoff)) {
                    adj.get(i).add(j)
                    adj.get(j).add(i)
                }
            }
        }

        double[] degree = new double[n]
        for (int i = 0; i < n; i++) degree[i] = adj.get(i).size()

        double[] betweenness = computeBetweenness(adj, n)
        double[] closeness   = computeClosenessByComponent(adj, n)

        log.debug "ContactGraph: N={} cutoff={} built in {} ms (protein {})",
                n, cutoff, System.currentTimeMillis() - t0, protein.name

        return new ContactGraph(indexByKey, betweenness, closeness, degree)
    }

    //---------------------------------------------------------------------//
    //  Brandes (2001) — unweighted betweenness centrality
    //  TODO(perf, large N): HPPC primitive collections (IntArrayList /
    //  IntArrayDeque, already on classpath via build.gradle) would remove
    //  the Integer boxing in the hot inner loops here and in
    //  computeClosenessByComponent below. Typical 2-3× speedup on Brandes.
    //  Negligible at N=200; worth doing if profiling shows ContactGraph
    //  dominating for N≳400.
    //---------------------------------------------------------------------//

    static double[] computeBetweenness(List<List<Integer>> adj, int n) {
        double[] cb = new double[n]
        if (n < 3) return cb

        int[] stack = new int[n]
        int[] sigma = new int[n]
        int[] dist = new int[n]
        double[] delta = new double[n]
        List<List<Integer>> preds = new ArrayList<>(n)
        for (int i = 0; i < n; i++) preds.add(new ArrayList<Integer>())

        ArrayDeque<Integer> queue = new ArrayDeque<>(n)

        for (int s = 0; s < n; s++) {
            // reset per source
            int top = 0
            for (int i = 0; i < n; i++) {
                sigma[i] = 0
                dist[i] = -1
                delta[i] = 0d
                preds.get(i).clear()
            }
            sigma[s] = 1
            dist[s] = 0
            queue.clear()
            queue.add(s)

            while (!queue.isEmpty()) {
                int v = queue.poll()
                stack[top++] = v
                for (int w : adj.get(v)) {
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1
                        queue.add(w)
                    }
                    if (dist[w] == dist[v] + 1) {
                        sigma[w] += sigma[v]
                        preds.get(w).add(v)
                    }
                }
            }

            // accumulate dependencies in reverse BFS order
            for (int idx = top - 1; idx >= 0; idx--) {
                int w = stack[idx]
                for (int v : preds.get(w)) {
                    delta[v] += ((double) sigma[v] / (double) sigma[w]) * (1d + delta[w])
                }
                if (w != s) cb[w] += delta[w]
            }
        }

        // undirected normalization: each pair counted twice → divide by 2,
        // then by (N-1)(N-2)/2 to map to [0, 1] (Brandes convention).
        double norm = 2d / (((double)(n - 1)) * ((double)(n - 2)))
        for (int i = 0; i < n; i++) cb[i] = (cb[i] / 2d) * norm
        return cb
    }

    //---------------------------------------------------------------------//
    //  Closeness — normalized within each connected component
    //  CC_i = (n_comp - 1) / Σ_{j in same component, j != i} d(i, j)
    //---------------------------------------------------------------------//

    static double[] computeClosenessByComponent(List<List<Integer>> adj, int n) {
        double[] cc = new double[n]
        if (n == 0) return cc

        int[] dist = new int[n]
        ArrayDeque<Integer> queue = new ArrayDeque<>(n)

        for (int s = 0; s < n; s++) {
            for (int i = 0; i < n; i++) dist[i] = -1
            dist[s] = 0
            queue.clear()
            queue.add(s)
            int reached = 1
            long sumDist = 0L

            while (!queue.isEmpty()) {
                int v = queue.poll()
                for (int w : adj.get(v)) {
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1
                        sumDist += dist[w]
                        reached++
                        queue.add(w)
                    }
                }
            }

            if (sumDist > 0L) {
                cc[s] = ((double)(reached - 1)) / ((double) sumDist)
            } else {
                cc[s] = 0d  // isolated singleton
            }
        }
        return cc
    }

}
