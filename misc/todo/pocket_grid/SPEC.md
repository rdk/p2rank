# Spec — Pocket grid points export + per-pocket descriptors

Status: spec, not plan. Author decisions captured in two rounds:

- **Initial 6 Qs:** (1) long-format grid CSV, (2) morph-closing proxy with
  strategy switch, (3) defaults OK, (4) separate descriptors file,
  (5) no standalone command, (6) initial descriptor menu accepted.
- **20-audit cross-check vs. code:** see below; all 20 decisions are applied
  in this revision.

## Goals

Two new opt-in outputs, both produced by any `predict` or `rescore` run,
plus an optional PyMOL visualization:

1. **`{outdir}/{name}_pocket_grid.{format}`** — regular 3D grid of points
   covering the empty space around the protein, in **long format**: one row
   per `(point, pocket)` pair. By default only **assigned** points are
   written (one or more rows per point, one per pocket they belong to).
   Unassigned points (`pocket = 0`) can be opted in with
   `pocket_grid_include_unassigned`.
2. **`{outdir}/{name}_pocket_descriptors.{format}`** — one row per predicted
   pocket with score, rank, centroid, and an extensible list of
   geometric/chemical descriptors (volume from grid-point count, plus
   others).
3. **`{outdir}/visualizations/{name}_pocket_grid.pml`** — optional PyMOL
   visualization, produced by a new renderer.

Both data files reuse the existing `TableExporter` (csv / csv.gz / csv.zst /
arrow / arrow.gz / arrow.zst / parquet), matching the SAS-points export
pattern documented at `documentation/export-points.md`. Decoupled from the
prediction algorithm: P2Rank still scores SAS points exactly as today; the
grid is a post-prediction geometric overlay used only for descriptor
computation.

## Prerequisite refactor

**`TableData` and the three writers (`writeCsv`/`writeArrow`/`writeParquet`)
must be extended to support a `STRING` column type** (audit #1). Currently
`TableData` only accepts `DOUBLE` and `INT`
(`src/main/groovy/cz/siret/prank/program/routines/predict/output/TableData.groovy:13-15`).
Without this, the descriptors file's `name` column cannot be written.

Scope of the refactor:
- Add `ColumnType.STRING` and a `String[] getStringColumn(int)` (or boxed
  `Object` access path) to `TableData`.
- Extend `writeCsv` to emit strings with proper CSV quoting (escape `,`,
  `"`, newlines per RFC 4180).
- Extend `writeArrow` to use `VarCharVector` for string columns.
- Extend `writeParquet` to use `BINARY` (UTF8) primitive type for string
  columns.
- Update `PointExportData` to declare its columns via the new type system
  (no functional change for SAS-point export — no strings used today).

## Algorithms

### Grid generation (once per protein)

1. Build a KdTree over `protein.proteinAtoms`
   (`protein.proteinAtoms.withKdTreeConditional()`). Note: when
   `CofactorHandler` is enabled, cofactor atoms are already merged into
   `proteinAtoms` (`Protein.groovy:571-583`) — no separate union step
   needed (audit #4).
2. Bounding box around `protein.proteinAtoms`, expanded by
   `pocket_grid_max_dist` in every direction (reuses
   `Box.aroundAtoms(...).withMargin(...)`).
3. Walk a regular cubic lattice with edge `pocket_grid_spacing` inside the
   box (reuses `GridGenerator.forBox(box, edge)`).
4. Per-atom VdW radius via CDK `Elements` (audit #2). Reuse the same
   accessor pattern as `PatchedCdkNumericalSurface` — when CDK returns
   `null` for an element, fall back to the Krypton proxy (2.02 Å), matching
   the existing null-VdW workaround. Implemented as a small helper
   `VdwRadiusTable.get(Atom) → double`.
5. For each lattice point:
   - **drop** if `min_dist_to_proteinAtoms < vdw_radius(nearest_atom) + pocket_grid_atom_buffer`
     — overlaps the protein;
   - **drop** if `min_dist_to_proteinAtoms > pocket_grid_max_dist` — too
     far from the surface;
   - **keep** otherwise.

**Implementation note** (audit #3): extend
`GridGenerator.sampleGridPointsAroundAtoms` (`GridGenerator.java:157-172`)
to accept both `minDist` (semantically per-atom: VdW + buffer) and
`maxDist`. The current method already does the `maxDist` side; the
extension is the per-atom-VdW exclusion check.

### Per-pocket assignment (multi-valued)

1. For each pocket `p`, take all kept grid points within
   `pocket_grid_assign_cutoff` of any atom in `p.surfaceAtoms`. That's the
   *raw shell* — analogous to `SwinSiteLoader`'s `cutoutShell` at
   `SwinSiteLoader.groovy:92-100`.
2. **Shape fill** (pluggable via `pocket_grid_fill`, runs **per-pocket** —
   each pocket's raw shell is dilated independently, audit #6):
   - `morph_closing` (default): morphological closing on the lattice. Mark
     any unassigned lattice cell whose 6-/18-/26-neighborhood contains
     ≥ `pocket_grid_fill_min_neighbors` already-assigned cells; iterate
     until stable or `pocket_grid_fill_max_iters` reached. Integer-grid
     native, no extra deps.
   - `convex_hull`: build the 3D convex hull of the raw shell (Quickhull or
     equivalent — TBD at plan time); include every lattice point inside.
     Exact; pulls a hull dependency.
   - `none`: keep the raw shell exactly.

   The `PocketShapeFiller` strategy interface (see Extensibility) makes
   adding alternatives a single-file change.
3. A grid point may belong to multiple pockets. In the output file each
   `(point, pocket)` membership is a separate row.

### Descriptor computation

After assignment, for each pocket and each name in `pocket_descriptors`,
look up the registered `PocketDescriptor` and compute. See "Initial
descriptor menu" below.

## New params (additions to `Params.groovy`)

All carry `@RuntimeParam` (audit #7) — runtime / output concerns, not
training.

Allowed values for `pocket_grid_format` (audit #8, enumerated explicitly to
avoid drift): `csv`, `csv.gz`, `csv.zst`, `arrow`, `arrow.gz`, `arrow.zst`,
`parquet`.

| Param | Default | Notes |
|---|---|---|
| `export_pocket_grid` | `false` | gate for the grid-points file |
| `export_pocket_descriptors` | `false` | gate for the descriptors file |
| `export_pocket_grid_pml` | `false` | gate for the PyMOL visualization; requires `export_pocket_grid=true` (fail-fast otherwise, audit #16) |
| `pocket_grid_format` | `"csv"` | one of the enumerated values above |
| `pocket_grid_include_unassigned` | `false` | include `pocket = 0` rows in the grid file |
| `pocket_grid_spacing` | `1.0` (Å) | lattice edge; volume scales with this³ |
| `pocket_grid_max_dist` | `6.0` (Å) | upper bound: nearest-atom distance to keep a grid point |
| `pocket_grid_atom_buffer` | `0.5` (Å) | additive buffer on per-atom VdW exclusion: keep if `dist > vdw_radius(atom) + buffer` (audit #9) |
| `pocket_grid_assign_cutoff` | `4.5` (Å) | membership cutoff vs. `pocket.surfaceAtoms`; matches `SwinSiteLoader.SURFACE_ATOMS_CUTOFF` |
| `pocket_grid_fill` | `"morph_closing"` | one of `morph_closing`, `convex_hull`, `none` |
| `pocket_grid_fill_min_neighbors` | `3` | morph_closing only — neighbor count threshold |
| `pocket_grid_fill_max_iters` | `5` | morph_closing only — guard against runaway dilation |
| `pocket_descriptors` | `["volume"]` | list-param; each name selects a registered descriptor |

**Validation** (audit #10): unknown values in `pocket_descriptors`,
`pocket_grid_fill`, and `pocket_grid_format`, plus the
`export_pocket_grid_pml ⇒ export_pocket_grid` invariant, are checked at
Main startup. Same pattern as the cofactor validation at
`Main.groovy:142-153`.

## Output schemas

### `{name}_pocket_grid.{format}` (long format)

| Column | Type | Description |
|---|---|---|
| `x`, `y`, `z` | f64 | grid point coordinate |
| `pocket` | i32 | pocket rank this row belongs to; `0` only present if `pocket_grid_include_unassigned` is on |

**Sort order** (audit #5): rows sorted by `pocket` asc, then `x` asc,
`y` asc, `z` asc. `pocket=0` (if enabled) goes last so readers that only
care about assigned points can stop early. Deterministic and reproducible
across runs.

### `{name}_pocket_descriptors.{format}`

Base columns (always present), then one column per name in
`pocket_descriptors`:

| Column | Type | Source |
|---|---|---|
| `name` | string | `pocket.name` (requires `TableData` STRING support, prerequisite refactor) |
| `rank` | i32 | `pocket.rank` |
| `score` | f64 | `pocket.score` |
| `probability` | f64 | from score transformer; **column omitted entirely** when no transformer ran |
| `center_x`, `center_y`, `center_z` | f64 | `pocket.centroid` |
| `<descriptor>` | f64 / i32 | one per requested descriptor |

**`probability` column inclusion** (audit #19): controlled by a constructor
flag on the export-data class, mirroring `PointExportData.includeScore`
(`PointExportData.groovy:47-48`). Schema is fixed at construction; no
runtime branching on row write.

## Initial descriptor menu

Shipped registry:

| Name | Output | Definition |
|---|---|---|
| `volume` | f64 (Å³) | `\|assigned grid points\| × pocket_grid_spacing³` |
| `sphericity` | f64 in [0, 1] | `V_pocket / V_bounding_sphere`, where `V_bounding_sphere = (4/3)π · r³` with `r = max(\|p − centroid\|)` over the pocket's grid points. Quantization-free; 1 = perfect sphere. (audit #18 — replaces the boundary-area formula) |
| `num_residues` | i32 | `pocket.residues.size()` (reuses existing accessor, audit #17) |
| `num_surface_atoms` | i32 | `pocket.surfaceAtoms.count` |

`volume` is the default value of `pocket_descriptors`. Others must be opted
in by name.

## Extensibility

All new Groovy classes carry `@CompileStatic` and `@Slf4j` per repo
convention (audit #20).

```
src/main/groovy/cz/siret/prank/program/routines/predict/output/descriptors/
  ├── PocketDescriptor.groovy          # interface: String name(); Object compute(PocketGridContext ctx)
  ├── PocketDescriptorRegistry.groovy  # name → factory; selection from Params.pocket_descriptors
  ├── VolumeDescriptor.groovy
  ├── SphericityDescriptor.groovy
  ├── NumResiduesDescriptor.groovy
  └── NumSurfaceAtomsDescriptor.groovy

src/main/groovy/cz/siret/prank/program/routines/predict/output/grid/
  ├── PocketGrid.groovy                # data: kept points + per-pocket assignment map
  ├── PocketGridBuilder.groovy         # generation + assignment + fill orchestration
  ├── VdwRadiusTable.groovy            # Atom → double, via CDK Elements + Krypton fallback
  └── fill/
        ├── PocketShapeFiller.groovy   # interface: Set<Point> fill(rawShell, allPoints, params)
        ├── MorphologicalCloser.groovy
        ├── ConvexHullFiller.groovy    # may be stub initially
        └── NoOpFiller.groovy
```

`PocketGridContext` exposes: the per-pocket grid-point set, the global
grid, the pocket, the protein, and `Params`. Adding a descriptor = drop one
file in `descriptors/` + register the name. Adding a fill strategy = drop
one file in `fill/` + extend the enum.

## Pocket grid visualization

Output:
- `{outdir}/visualizations/data/{name}_pocket_grid.pdb.gz` — one HETATM per
  grid point; pocket rank stored in the residue-sequence column (mirrors
  `writeLabeledPointsPdb` at `PredictionVisualizer.groovy:44-56`); generated
  in long format (one HETATM per `(point, pocket)` pair so PyMOL can split
  by residue).
- `{outdir}/visualizations/{name}_pocket_grid.pml` — small PyMOL script
  that `load`s the PDB and colors by residue.

This **PDB-sidecar approach** (audit #11) replaces the earlier inline
`pseudoatom`-per-point design — at ~20k–100k grid points the inline
approach would take seconds-to-minutes for PyMOL to load.

**Renderer:**
`src/main/groovy/cz/siret/prank/program/visualization/renderers/PocketGridPymolRenderer.groovy`,
parallel to `PymolRenderer` / `ChimeraXRenderer`. Takes the in-memory
`PocketGrid` (not the CSV file — the grid is already in memory and the PDB
sidecar is derived from it, audit #15 makes the format constraint moot).

**Colors:** reuse `PredictionVisualizer.generatePocketColors(numPockets)`
(`PredictionVisualizer.groovy:38`) so the grid PML matches the main pocket
PML palette (audit #13).

**Layout in the PML:**
- `load .../data/{name}_pocket_grid.pdb.gz, pocket_grid`
- Per pocket: `create pocket_grid_<rank>, pocket_grid and resi <rank>` and
  `color <hex>, pocket_grid_<rank>`.
- `show spheres, pocket_grid_*` with small `sphere_scale` (e.g. 0.3).

**Path layout** (audit #12): data files (`_pocket_grid.{fmt}`,
`_pocket_descriptors.{fmt}`) at the root of `outdir`, matching the SAS
points export. Visualization artifacts (`_pocket_grid.pdb.gz`,
`_pocket_grid.pml`) under `visualizations/` / `visualizations/data/`,
matching the existing main-PML layout.

**Master visualization switch** (audit #14): respects `visualizations=false`
— if visualizations are globally off, the grid PML + PDB sidecar are
skipped even when `export_pocket_grid_pml=true`. Single off-switch for ALL
viz.

**Independence from `vis_renderers`:** the new renderer has its own gate
(`export_pocket_grid_pml`) and does *not* tie into the
`["pymol", "chimerax"]` renderer list. The grid PML is a power-user output
that shouldn't be implicit. Easy to revisit if usage patterns argue
otherwise.

## CLI examples

```bash
# grid + default descriptors (just volume), parquet
prank predict -f protein.pdb -export_pocket_grid 1 -export_pocket_descriptors 1 \
    -pocket_grid_format parquet

# custom descriptor list + tighter grid
prank predict dataset.ds -export_pocket_descriptors 1 \
    -pocket_descriptors "volume,sphericity,num_residues,num_surface_atoms" \
    -pocket_grid_spacing 0.75 -pocket_grid_max_dist 5

# rescore with grid export, arrow.zst
prank rescore fpocket.ds -export_pocket_grid 1 -pocket_grid_format arrow.zst

# switch fill strategy (e.g. for ablation studies)
prank predict -f protein.pdb -export_pocket_grid 1 -pocket_grid_fill none

# grid CSV + PyMOL visualization
prank predict -f protein.pdb -export_pocket_grid 1 -export_pocket_grid_pml 1

# also keep the unassigned envelope (e.g. for debugging the grid generator)
prank predict -f protein.pdb -export_pocket_grid 1 -pocket_grid_include_unassigned 1
```

## Files touched (preview, plan will refine)

New:
- `descriptors/` and `grid/` packages as above
- `PocketGridExporter.groovy` + `PocketDescriptorsExporter.groovy` next to
  `PointsExporter.groovy`
- `PocketGridExportData` / `PocketDescriptorsExportData` data classes next
  to `PointExportData.groovy`
- `PocketGridPymolRenderer.groovy` under `program/visualization/renderers/`
- Tests next to each new class
- **`documentation/export-pocket-grid.md`** — user-facing how-to for the
  grid file: algorithm summary, sort order, params, format options, CLI
  examples, PyMOL visualization details
- **`documentation/export-pocket-descriptors.md`** — descriptors file
  format, descriptor catalog with formulas, extensibility for adding new
  descriptors

Modified:
- `Params.groovy` — 11 new `@RuntimeParam` fields (table above)
- `Main.groovy` — startup validation hooks for `pocket_descriptors`,
  `pocket_grid_fill`, `pocket_grid_format`, and the
  `export_pocket_grid_pml ⇒ export_pocket_grid` invariant
- `PredictPocketsRoutine.groovy` + `RescorePocketsRoutine.groovy` — wire
  the new exporters and renderer at the same hook point as
  `PointsExporter.tryExportPoints`
- `TableData.groovy` + `TableExporter.groovy` + `PointExportData.groovy` —
  STRING column-type support (prerequisite refactor)
- `GridGenerator.java` — extend `sampleGridPointsAroundAtoms` to accept a
  per-atom minDist (VdW + buffer) alongside the existing maxDist
- `documentation/export-points.md` — cross-reference the two new docs from
  the "See also" section

Not touched:
- `PredictionSummary.toCSV()` / `predictions.csv` schema — descriptors live
  in their own file.
- `PocketStats.realVolumeApprox` — keep as-is; SwinSite still uses it. The
  new grid-volume is independent.

## Scope notes

- Cofactor atoms participate in the bounding box and the VdW exclusion via
  their inclusion in `protein.proteinAtoms`. They do **not** affect
  `pocket.surfaceAtoms` membership for assignment — the existing pocket
  surface-atom set defines membership.
- Outputs are computed *after* score transformation so `probability` is
  available when applicable.
- `breaking-changes.md` (2.7 or whenever this ships) gets a bullet for the
  new param family and the new output files.

## Followups / not in this spec

- Per-residue descriptors (different file, different aggregation).
- Pocket overlap matrix (cheap byproduct of the long-format grid file —
  group-by `pocket` and intersect, or compute eagerly and dump as
  `{name}_pocket_overlap.csv`).
- Long-format SAS-points export (parallel change, separate spec).
- Real-3D-hull `convex_hull` filler (initial ship may stub it).
