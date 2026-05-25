package cz.siret.prank.features.implementation.physics

import com.carrotsearch.hppc.IntArrayDeque
import com.carrotsearch.hppc.IntArrayList
import com.carrotsearch.hppc.IntHashSet
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Per-chain residue contact graph plus the three derived centrality measures
 * (betweenness, closeness, degree). Built once per Protein in preProcessProtein
 * and cached in Protein.secondaryData[CACHE_KEY].
 *
 * Centrality is computed independently for each chain: edges connect residues
 * within the same chain only, so inter-chain contacts at quaternary interfaces
 * do not inflate betweenness or degree. Results from all chains are merged
 * into protein-wide lookup arrays keyed by Residue.Key.
 *
 * Edges: two residues in the same chain are connected if any pair of their
 * heavy atoms lies within Params.feat_cgraph_cutoff (Å).
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
 * each connected component (CC_i = (n_comp − 1) / Σ_{j in comp} d(i,j)).
 * Betweenness uses standard Brandes; degree is the adjacency-list cardinality.
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

        List<Residue> allResidues = protein.residues.list
        int totalN = allResidues.size()
        Map<Residue.Key, Integer> indexByKey = new HashMap<>(totalN)
        for (int i = 0; i < totalN; i++) indexByKey.put(allResidues.get(i).key, i)

        if (totalN == 0) return new ContactGraph(indexByKey, new double[0], new double[0], new double[0])

        double[] betweenness = new double[totalN]
        double[] closeness = new double[totalN]
        double[] degree = new double[totalN]

        double cutoff = params.feat_cgraph_cutoff

        for (ResidueChain chain : protein.residueChains) {
            List<Residue> chainResidues = chain.residues
            int n = chainResidues.size()
            if (n == 0) continue

            int[] globalIdx = new int[n]
            for (int i = 0; i < n; i++) {
                globalIdx[i] = indexByKey.get(chainResidues.get(i).key)
            }

            List<Atom> flatAtoms = new ArrayList<>(n * 8)
            Map<Integer, Integer> atomToLocalIdx = new HashMap<>(n * 8)
            for (int i = 0; i < n; i++) {
                for (Atom a : chainResidues.get(i).atoms) {
                    flatAtoms.add(a)
                    atomToLocalIdx.put(a.PDBserial, i)
                }
            }
            Atoms chainAtoms = new Atoms(flatAtoms).withKdTree()

            IntHashSet[] adjSets = new IntHashSet[n]
            for (int i = 0; i < n; i++) adjSets[i] = new IntHashSet()

            for (int i = 0; i < n; i++) {
                for (Atom a : chainResidues.get(i).atoms) {
                    Atoms neighbors = chainAtoms.cutoutSphere(a, cutoff)
                    for (Atom b : neighbors) {
                        Integer j = atomToLocalIdx.get(b.PDBserial)
                        if (j != null && j.intValue() != i) {
                            adjSets[i].add(j)
                        }
                    }
                }
            }

            int[][] adj = new int[n][]
            for (int i = 0; i < n; i++) {
                adj[i] = adjSets[i].toArray()
            }

            double[] chainBet = computeBetweenness(adj, n)
            double[] chainClose = computeClosenessByComponent(adj, n)

            for (int i = 0; i < n; i++) {
                int gi = globalIdx[i]
                betweenness[gi] = chainBet[i]
                closeness[gi] = chainClose[i]
                degree[gi] = (double) adjSets[i].size()
            }
        }

        log.debug "ContactGraph: N={} chains={} cutoff={} built in {} ms (protein {})",
                totalN, protein.residueChains.size(), cutoff,
                System.currentTimeMillis() - t0, protein.name

        return new ContactGraph(indexByKey, betweenness, closeness, degree)
    }

    //---------------------------------------------------------------------//
    //  Brandes (2001) — unweighted betweenness centrality
    //---------------------------------------------------------------------//

    static double[] computeBetweenness(int[][] adj, int n) {
        double[] cb = new double[n]
        if (n < 3) return cb

        int[] stack = new int[n]
        int[] sigma = new int[n]
        int[] dist = new int[n]
        double[] delta = new double[n]
        IntArrayList[] preds = new IntArrayList[n]
        for (int i = 0; i < n; i++) preds[i] = new IntArrayList()

        IntArrayDeque queue = new IntArrayDeque(n)

        for (int s = 0; s < n; s++) {
            int top = 0
            for (int i = 0; i < n; i++) {
                sigma[i] = 0
                dist[i] = -1
                delta[i] = 0d
                preds[i].elementsCount = 0
            }
            sigma[s] = 1
            dist[s] = 0
            queue.clear()
            queue.addLast(s)

            while (!queue.isEmpty()) {
                int v = queue.removeFirst()
                stack[top++] = v
                int[] neighbors = adj[v]
                for (int ni = 0; ni < neighbors.length; ni++) {
                    int w = neighbors[ni]
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1
                        queue.addLast(w)
                    }
                    if (dist[w] == dist[v] + 1) {
                        sigma[w] += sigma[v]
                        preds[w].add(v)
                    }
                }
            }

            for (int idx = top - 1; idx >= 0; idx--) {
                int w = stack[idx]
                int[] predBuf = preds[w].buffer
                int predCount = preds[w].elementsCount
                for (int pi = 0; pi < predCount; pi++) {
                    int v = predBuf[pi]
                    delta[v] += ((double) sigma[v] / (double) sigma[w]) * (1d + delta[w])
                }
                if (w != s) cb[w] += delta[w]
            }
        }

        double norm = 2d / (((double)(n - 1)) * ((double)(n - 2)))
        for (int i = 0; i < n; i++) cb[i] = (cb[i] / 2d) * norm
        return cb
    }

    //---------------------------------------------------------------------//
    //  Closeness — normalized within each connected component
    //  CC_i = (n_comp - 1) / Σ_{j in same component, j != i} d(i, j)
    //---------------------------------------------------------------------//

    static double[] computeClosenessByComponent(int[][] adj, int n) {
        double[] cc = new double[n]
        if (n == 0) return cc

        int[] dist = new int[n]
        IntArrayDeque queue = new IntArrayDeque(n)

        for (int s = 0; s < n; s++) {
            for (int i = 0; i < n; i++) dist[i] = -1
            dist[s] = 0
            queue.clear()
            queue.addLast(s)
            int reached = 1
            long sumDist = 0L

            while (!queue.isEmpty()) {
                int v = queue.removeFirst()
                int[] neighbors = adj[v]
                for (int ni = 0; ni < neighbors.length; ni++) {
                    int w = neighbors[ni]
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1
                        sumDist += dist[w]
                        reached++
                        queue.addLast(w)
                    }
                }
            }

            if (sumDist > 0L) {
                cc[s] = ((double)(reached - 1)) / ((double) sumDist)
            } else {
                cc[s] = 0d
            }
        }
        return cc
    }

}
