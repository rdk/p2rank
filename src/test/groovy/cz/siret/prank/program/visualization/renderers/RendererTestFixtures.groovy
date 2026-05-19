package cz.siret.prank.program.visualization.renderers

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Shared test fixtures for the pocket-grid renderer tests. Both
 * {@code PocketGridChimeraXRendererTest} and {@code PocketGridPymolRendererTest}
 * exercised an identical 3-atom / 2-pocket-overlap grid; extracted here so
 * a fixture change touches one site.
 */
@CompileStatic
final class RendererTestFixtures {

    private RendererTestFixtures() {}

    static BitSet bits(int... values) {
        BitSet b = new BitSet()
        for (int v : values) b.set(v)
        return b
    }

    /**
     * 3 grid points (a=0,0,0 — b=1,0,0 — c=2,0,0), spacing 1.0,
     * pocket 1 = {a, b}, pocket 2 = {b, c}. Used by the renderer tests to
     * verify per-pocket coloring, multi-membership behavior, etc.
     */
    static PocketGrid tinyGrid() {
        Atom a = new Point(0d, 0d, 0d)
        Atom b = new Point(1d, 0d, 0d)
        Atom c = new Point(2d, 0d, 0d)
        LongIntHashMap idx = new LongIntHashMap()
        idx.put(PocketGrid.pack(0, 0, 0), 0)
        idx.put(PocketGrid.pack(1, 0, 0), 1)
        idx.put(PocketGrid.pack(2, 0, 0), 2)
        Map<Integer, BitSet> assigned = new LinkedHashMap<>()
        assigned.put(1, bits(0, 1))
        assigned.put(2, bits(1, 2))
        return new PocketGrid(new Atoms([a, b, c]), 1.0d, 0d, 0d, 0d, idx, assigned)
    }

}
