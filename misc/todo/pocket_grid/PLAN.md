# Plan — Pocket grid points export + per-pocket descriptors

Companion to `SPEC.md`. Ordered, atomic phases. Each phase is a single
reviewable commit (or two if splitting tests helps). Compile + test must
be green at the end of every phase.

## Phase order rationale

Layered, foundation-first. Each phase only depends on phases above it.

```
1. TableData STRING refactor          (foundation, no behavior change)
2. VdW radius helper + grid generator (foundation)
3. PocketGrid data class + fill strategies
4. PocketGridBuilder (orchestration)
5. Descriptors infrastructure + menu
6. Export-data classes + exporters
7. PyMOL renderer + PDB sidecar
8. Params + Main-startup validation
9. Wire into PredictPockets + RescorePockets routines
10. Documentation (2 new MD files + cross-ref)
11. Smoke test on real data
```

---

## Phase 1 — `TableData` STRING column-type refactor

**Goal:** Extend the export infrastructure to support string columns. No
behavioral change to existing SAS-points export.

**Changes:**
- `TableData.groovy` — add `ColumnType.STRING`; new method
  `default String getString(int rowIndex, int colIndex) { throw ... }`
  for STRING columns; default `getColumn` only meaningful for numeric.
- `TableExporter.groovy`:
  - `writeCsv` — string branch with RFC 4180 quoting (escape `,`, `"`, newline).
  - `writeArrow` — `VarCharVector` for STRING columns; `buildSchema` updated.
  - `writeParquet` — `BINARY` with `LogicalTypeAnnotation.stringType()`;
    `RowDehydrator` updated.
- `PointExportData.groovy` — no functional change; verify
  `getColumnType` doesn't accidentally return STRING (it currently can't —
  all columns are DOUBLE/INT).

**Tests:**
- `TableExporterTest` — new round-trip tests for a synthetic table with
  one STRING column, one INT, one DOUBLE; csv, csv.gz, arrow, parquet.
- CSV quoting edge cases: value contains `,`, `"`, `\n`.
- Regression: existing `PointsExporterTest` still passes (no schema
  changes to SAS export).

**Commit:** `Extend TableData with STRING column type`

---

## Phase 2 — VdW radius helper + `GridGenerator` extension

**Goal:** Make per-atom VdW radii available; extend the existing grid
sampler.

**Changes:**
- New `src/main/groovy/cz/siret/prank/program/routines/predict/output/grid/VdwRadiusTable.groovy`:
  - `static double get(Atom atom)` — looks up via CDK `Elements` by
    element symbol; if `null`, falls back to Krypton's 2.02 Å (matches
    the existing pattern in `PatchedCdkNumericalSurface.groovy:54-56`).
  - Caches `String elementSymbol → double radius` in a
    `ConcurrentHashMap` (predict runs multi-threaded via
    `Dataset.process(...)`, so the cache is shared across threads;
    `computeIfAbsent` is safe and avoids races).
- `GridGenerator.java` — extend
  `sampleGridPointsAroundAtoms(Atoms, edge, radius)` into a new variant
  `sampleGridPointsBetween(Atoms, edge, maxDist, double atomBuffer)`:
  - Keep existing method unchanged.
  - New method uses `Atoms.withKdTreeConditional()`, walks the lattice,
    for each cell computes `nearest = atoms.nearestSqrDist(p)`,
    `vdw = VdwRadiusTable.get(nearestAtom)`, drops if
    `sqrt(nearest) < vdw + atomBuffer` or `sqrt(nearest) > maxDist`.
  - Note: `nearestSqrDist` returns squared distance only; for the per-atom
    VdW check we need the actual nearest **atom**, not just distance.
    Use `Atoms.findNearest(point)` (`Atoms.java:244`) which returns the
    Atom; then compute `dist` once.

**Tests:**
- `VdwRadiusTableTest` — known elements (C, N, O, S, P, Fe, Cu, Co)
  return non-null; Co/Ni/Cu use the Krypton fallback (2.02 Å); unknown
  symbol → fallback.
- `GridGeneratorTest` (new file or existing if present) — synthetic
  small `Atoms` set, verify min/max filtering on cubic lattice
  produces expected count. Edge case: single-atom input.

**Commit:** `Add VdwRadiusTable and GridGenerator min/max sampler`

---

## Phase 3 — `PocketGrid` data class + fill strategies

**Goal:** Pure data + algorithms, no orchestration.

**Changes:**
- `PocketGrid.groovy`:
  - Fields:
    - `Atoms allPoints` — kept grid points after filtering, wrapped as
      `Atoms` (since `Point implements Atom`). Reusing `Atoms` gives us
      `cutoutShell`, `withKdTree`, `getByID` for free.
    - `Map<Integer, Set<Integer>> pocketToPointIndices` (rank → indices
      into `allPoints`).
    - `Set<Integer> assignedIndices` (union of all per-pocket sets).
    - `double spacing`.
    - `Map<LatticeCoord, Integer> latticeIndex` — integer-lattice
      coordinate `(i, j, k)` → point index. Computed from
      `originX/Y/Z` + `spacing` during grid generation; **required by
      `MorphologicalCloser`** for `O(1)` neighbor lookups (without it
      morph closing degrades to all-pairs distance comparisons).
    - `LatticeCoord` is a small immutable value class with proper
      `equals`/`hashCode`.
  - Provides: `Atoms pointsForPocket(int rank)`,
    `Set<Integer> pocketsForPoint(int pointIndex)`,
    `Set<Integer> neighborsOf(int pointIndex, int connectivity)` (where
    `connectivity ∈ {6, 18, 26}` consults `latticeIndex`).
- `fill/PocketShapeFiller.groovy` — interface:
  ```groovy
  Set<Integer> fill(Set<Integer> rawShellIndices,
                    List<Point> allPoints,
                    double spacing,
                    Params params)
  ```
- `fill/NoOpFiller.groovy` — returns input unchanged.
- `fill/MorphologicalCloser.groovy`:
  - Operates on a `Map<(int,int,int) → Integer>` lattice index built from
    allPoints. For each iteration, scans candidate cells (immediate
    neighbors of assigned cells) and promotes those whose neighbor count
    ≥ `pocket_grid_fill_min_neighbors`. Stops at fixed-point or
    `pocket_grid_fill_max_iters`.
  - Neighborhood: 26-connectivity (configurable later if needed).
- `fill/ConvexHullFiller.groovy` — initial **stub** that throws
  `UnsupportedOperationException("convex_hull fill not yet implemented")`
  so users get a clear error if they select it. Real impl in a followup.

**Tests:**
- `MorphologicalCloserTest` — synthetic shapes:
  - Pure sphere shell (3-cell-thick) → fills to solid sphere within
    `max_iters`.
  - U-shape with concavity → concavity filled in.
  - Disconnected components → not merged when far apart.
- `NoOpFillerTest` — identity.

**Commit:** `Add PocketGrid data class and morph-closing fill strategy`

---

## Phase 4 — `PocketGridBuilder` (orchestration)

**Goal:** End-to-end grid generation + per-pocket assignment + fill.

**Changes:**
- `PocketGridBuilder.groovy`:
  - `static PocketGrid build(Protein protein, List<? extends Pocket> pockets, Params params)`
  - Steps:
    1. Call the new sampler from Phase 2 →
       `Atoms allPoints` of kept lattice points + their lattice
       coordinates. Store both in the resulting `PocketGrid`.
    2. Build a KdTree on `allPoints` (`allPoints.withKdTree()`) — cheap
       once, reused by callers downstream.
    3. For each pocket `p`:
       - `p.surfaceAtoms.withKdTreeConditional()` (small set, KdTree
         built on demand).
       - Iterate `allPoints` once; for each point at index `i`, keep
         `i` in the **raw shell** set if
         `p.surfaceAtoms.nearestDist(allPoints.list[i]) <= params.pocket_grid_assign_cutoff`.
         O(|allPoints| × log|surfaceAtoms|) per pocket.
       - Pass the raw shell set + `latticeIndex` to
         `filler.fill(...)` → final per-pocket index set.
    4. Aggregate into `PocketGrid.pocketToPointIndices`; derive
       `assignedIndices` as the union.
  - Filler selection: dispatch on `params.pocket_grid_fill` enum value.
  - All `@CompileStatic` + `@Slf4j`.

**Tests:**
- `PocketGridBuilderTest`:
  - 1fbl.pdb fixture (small, fast). Predict pockets via existing
    `PrankFacade`; build grid; assert:
    - `allPoints` count is reasonable for the bounding box (sanity check).
    - Each pocket has a non-empty point set after fill.
    - Multi-pocket overlap can occur (count of `(point, pocket)` pairs
      > count of distinct points).
- Edge case: protein with 0 predicted pockets → `PocketGrid` with
  `allPoints` non-empty but `pocketToPointIndices` empty.

**Commit:** `Add PocketGridBuilder orchestrating grid + assignment + fill`

---

## Phase 5 — Descriptors infrastructure + initial 4

**Goal:** Pluggable descriptors with default `["volume"]`.

**Changes:**
- `descriptors/PocketGridContext.groovy` — data class: `pocket`, `protein`,
  `gridPointsForPocket`, `pocketGrid`, `params`.
- `descriptors/PocketDescriptor.groovy` — interface:
  ```groovy
  String name()
  ColumnType columnType()   // INT or DOUBLE
  double compute(PocketGridContext ctx)
  ```
  (Return type `double` — INT descriptors cast at write time, mirroring
  TableData's int-as-double convention.)
- `descriptors/PocketDescriptorRegistry.groovy`:
  - `static Map<String, PocketDescriptor> REGISTRY` — populated at
    classload with the 4 shipped descriptors.
  - `static PocketDescriptor get(String name)` — throws `PrankException`
    on unknown.
  - `static Set<String> knownNames()`.
- `VolumeDescriptor.groovy` — `count(gridPoints) × spacing³`.
- `SphericityDescriptor.groovy` — bounding-sphere variant. **Centroid is
  the centroid of the pocket's assigned grid points**, not
  `pocket.centroid` (which is derived from surfaceAtoms and would give
  misleading numbers for asymmetric pockets):
  - `gridCentroid = mean(p for p in ctx.gridPointsForPocket)`
  - `r = max(dist(p, gridCentroid))`
  - `V_sphere = (4/3) · π · r³`
  - `result = V_pocket / V_sphere` (≤ 1 by construction; clamp is
    defensive)
- `NumResiduesDescriptor.groovy` — `pocket.residues.size()`.
- `NumSurfaceAtomsDescriptor.groovy` — `pocket.surfaceAtoms.count`.

**Tests:**
- Per-descriptor unit tests using a synthetic small `PocketGridContext`:
  - Volume: 8 grid cells @ 1Å spacing → V = 8 Å³.
  - Sphericity: solid sphere of N cells → sphericity ≈ 1.0 (within
    tolerance for lattice quantization); flat disc → sphericity << 1.
  - num_residues / num_surface_atoms: stub pockets.
- `PocketDescriptorRegistryTest` — known names resolve; unknown throws.

**Commit:** `Add pocket descriptor framework with 4 initial descriptors`

---

## Phase 6 — Export-data classes + exporters

**Goal:** Bridge `PocketGrid` and descriptor computations to `TableExporter`.

**Changes:**
- `PocketGridExportData.groovy` (implements `TableData`):
  - Constructor takes `PocketGrid` and `boolean includeUnassigned`.
  - Materializes long-format rows during construction (point-pocket pairs);
    sort by `(pocket, x, y, z)`.
  - Columns: `x`, `y`, `z` (DOUBLE), `pocket` (INT).
- `PocketDescriptorsExportData.groovy` (implements `TableData`):
  - Constructor takes pockets, descriptor results, `boolean includeProbability`.
  - Columns: `name` (STRING — uses Phase 1 refactor), `rank` (INT),
    `score` (DOUBLE), `probability` (DOUBLE, conditional),
    `center_x/y/z` (DOUBLE), then one column per descriptor (INT or
    DOUBLE per the descriptor's `columnType()`).
- `PocketGridExporter.groovy`:
  - `static void tryExport(PocketGrid grid, String outdir, String label, Params params)`
  - Gated by `params.export_pocket_grid`; uses `params.pocket_grid_format`.
  - Writes `{outdir}/{label}_pocket_grid.{format}`.
- `PocketDescriptorsExporter.groovy`:
  - `static void tryExport(List<? extends Pocket> pockets, PocketGrid grid, Protein protein, Params params, String outdir, String label)`
  - Derives `includeProbability` from the data itself:
    `pockets.any { !Double.isNaN(it.probaTP) }`. No extra parameter
    threaded through the wiring.
  - Iterates `params.pocket_descriptors`, computes each, builds
    `PocketDescriptorsExportData`, writes to file.

**Tests:**
- `PocketGridExportDataTest` — assert row count, sort order, column types
  on a synthetic `PocketGrid`.
- `PocketDescriptorsExportDataTest` — STRING column round-trips through
  CSV correctly (depends on Phase 1).
- Integration smoke: small fixture, export to all 7 formats, re-read with
  the same reader paths used by `PointExportDataTest`.

**Commit:** `Add pocket grid and descriptors exporters`

---

## Phase 7 — PyMOL renderer + PDB sidecar

**Goal:** Visualization of the grid in PyMOL.

**Changes:**
- New util in `PocketGridPymolRenderer.groovy`:
  - `static void render(PocketGrid grid, String outdir, String label, Params params)`
  - Writes:
    1. `{outdir}/visualizations/data/{label}_pocket_grid.pdb.gz` — one
       HETATM per `(point, pocket)` pair; pocket rank in residue-sequence
       column (cols 23-26); element column = `H` (or `D` for dummy).
       Mirrors `PredictionVisualizer.writeLabeledPointsPdb:44-56`.
    2. `{outdir}/visualizations/{label}_pocket_grid.pml`:
       - `load data/{label}_pocket_grid.pdb.gz, pocket_grid`
       - Per pocket rank N:
         - `create pocket_grid_<N>, pocket_grid and resi <N>`
         - `color <hex>, pocket_grid_<N>` (color via
           `PredictionVisualizer.generatePocketColors(numPockets)`)
       - `show spheres, pocket_grid_*`; `set sphere_scale, 0.3`
       - `delete pocket_grid` (drop the bulk object).
- All paths via `Futils` for cross-platform safety.

**Tests:**
- `PocketGridPymolRendererTest` — synthetic small `PocketGrid` (3 pockets,
  ~20 points each); assert output files exist; spot-check PML contains
  `load`, `create pocket_grid_1`, `color`, `show spheres`.
- Sanity: PDB output gzip-decompresses to valid HETATM records.

**Commit:** `Add PocketGridPymolRenderer with PDB sidecar`

---

## Phase 8 — Params + Main-startup validation

**Goal:** All 12 new params wired and validated.

**Changes:**
- `Params.groovy` — add 12 `@RuntimeParam` fields with javadoc, defaults
  per spec table. Place near `export_points` / `export_points_format`.
- `Main.groovy` — extend the existing param-validation block (around
  `:142-153`, same pattern used by cofactors):
  - `pocket_grid_format` ∈ allowed enumeration.
  - `pocket_grid_fill` ∈ {`morph_closing`, `convex_hull`, `none`}.
  - Every name in `pocket_descriptors` ∈ `PocketDescriptorRegistry.knownNames()`.
  - If `export_pocket_grid_pml` and `!export_pocket_grid` → throw
    `PrankException("export_pocket_grid_pml requires export_pocket_grid=true")`.

**Tests:**
- `ParamsTest` — defaults match spec.
- `MainTest` (or wherever cofactor validation is tested) — each of the 4
  validation failures triggers a fail-fast with a clear message.

**Commit:** `Add pocket grid params and startup validation`

---

## Phase 9 — Wire into routines

**Goal:** Call the new pipeline from prediction routines.

**Changes:**
- `PredictPocketsRoutine.groovy`:
  - After score transformation and the existing
    `PointsExporter.tryExportPoints(...)` call, insert:
    ```groovy
    PocketGrid grid = null
    if (params.export_pocket_grid || params.export_pocket_descriptors || params.export_pocket_grid_pml) {
        grid = PocketGridBuilder.build(item.protein, prediction.pockets, params)
    }
    PocketGridExporter.tryExport(grid, outdir, item.label, params)
    PocketDescriptorsExporter.tryExport(prediction.pockets, grid, item.protein, params, outdir, item.label)
    if (params.visualizations && params.export_pocket_grid_pml) {
        PocketGridPymolRenderer.render(grid, outdir, item.label, params)
    }
    ```
- `RescorePocketsRoutine.groovy` — identical hook at the analogous point.
- Order is critical: build → grid file → descriptors (needs grid for
  volume) → PML (needs grid).

**Tests:**
- `PredictPocketsRoutineTest` (extend existing) — run a small prediction
  with `-export_pocket_grid 1 -export_pocket_descriptors 1
  -export_pocket_grid_pml 1` on 1fbl.pdb; verify all four output files
  appear at the right paths.

**Commit:** `Wire pocket grid/descriptors/PML into prediction routines`

---

## Phase 10 — Documentation

**Goal:** User-facing docs.

**Changes:**
- New `documentation/export-pocket-grid.md`:
  - Sections: Overview, Output file format (long format, sort order,
    formats), Algorithm summary (grid generation, assignment, fill),
    Params table, CLI examples, PyMOL visualization, Notes.
  - Mirrors the structure of `documentation/export-points.md`.
- New `documentation/export-pocket-descriptors.md`:
  - Sections: Overview, Output file format, Descriptor catalog
    (volume, sphericity, num_residues, num_surface_atoms — with
    formulas), Extensibility (how to add a new descriptor), Params
    relevant to descriptors.
- `documentation/export-points.md` — append a brief "See also" block at
  the end pointing to the two new docs.
- `README.md` — single bullet in "What's new" for 2.7 (or whenever this
  ships) referencing the two new docs.

**Tests:** none (docs only).

**Commit:** `Document pocket grid and descriptors export`

---

## Phase 11 — Smoke test on real data

**Goal:** End-to-end on real proteins; eyeball outputs.

**Changes:** none.

**Verification (manual):**
- Run on `distro/test_data/1fbl.pdb` with `-export_pocket_grid 1
  -export_pocket_descriptors 1 -export_pocket_grid_pml 1`.
- Verify:
  - Grid CSV row counts and centroid statistics look right (small protein
    → maybe 5k-15k assigned point-rows).
  - Descriptors CSV — volume in 50-2000 Å³ range per pocket; sphericity
    in [0, 1]; residue/atom counts non-zero.
  - PyMOL: open the PML; visually confirm grid points cluster near
    predicted pockets, colored consistently with the main pocket PML.
- Run on one of the SwinSite test proteins (1tjw_A) for cross-method
  sanity.
- No regressions in existing SAS-points export.

**Commit:** none (or "Smoke test results: …" in a project log under `local/`).

---

## Risks / clarifications

Notes from the plan review that don't require code changes but are worth
flagging:

- **Sphericity clamp is redundant** — `V_pocket ≤ V_bounding_sphere`
  always (covering sphere by construction). The `[0, 1]` clamp is purely
  defensive; keep it.
- **Heavy Phase 4 integration test** — `PocketGridBuilderTest` uses
  `PrankFacade` to predict pockets, which is slow. Keep the integration
  test but also add a fast unit test that constructs `Pocket` instances
  manually with a synthetic `surfaceAtoms` set.
- **Empty `pocket_descriptors`** — `-pocket_descriptors ""` (empty list)
  is supported: descriptors file emits only the base columns
  (`name, rank, score, [probability,] center_x/y/z`). Add a regression
  test in Phase 6.
- **PDB residue-sequence column** is 4 chars (cols 23-26) → pockets are
  capped at rank 9999 in the PML output. Real pockets stay well under
  100; document the limit in the PML renderer's javadoc.
- **CSV string quoting** added in Phase 1 fires only for STRING columns.
  Existing DOUBLE/INT writes stay unquoted — no CSV-format drift for
  SAS-points export. Mention this in the Phase 1 commit message.

## Out-of-scope (followups noted in spec)

- Per-residue descriptors.
- `convex_hull` filler real implementation.
- Pocket overlap matrix output file.
- Long-format SAS-points export.
