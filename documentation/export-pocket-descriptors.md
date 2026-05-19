# Exporting Pocket Descriptors

Per-pocket geometric/chemical descriptors (volume, sphericity, residue
counts, etc.) written to a tabular file alongside any `predict` or
`rescore` run when `-export_pocket_descriptors` is on.

> **Cost note.** Most descriptors (`volume`, `sphericity`,
> `radius_of_gyration`, `num_grid_points`) are derived from the pocket
> grid, so selecting any of them triggers the full grid build (lattice
> generation + per-pocket assignment + shape fill) even with
> `-export_pocket_grid 0` — `-export_pocket_grid 0` only suppresses the
> per-protein grid file, not the computation. `num_residues` and
> `num_surface_atoms` do **not** need the grid; selecting only those two
> with `-export_pocket_grid 0` skips the grid build entirely (a near
> zero-cost descriptors export).

## Quick start

```bash
# Default: every shipped descriptor (num_residues, num_surface_atoms,
# num_grid_points, volume, sphericity, radius_of_gyration, principal_moments)
prank predict -f protein.pdb -export_pocket_descriptors 1

# Narrow set + tighter grid for more accurate volume/sphericity
prank predict dataset.ds -export_pocket_descriptors 1 \
    -pocket_descriptors "volume,sphericity" \
    -pocket_grid_spacing 0.75

# Rescoring path also supports it
prank rescore fpocket.ds -export_pocket_descriptors 1
```

## Output format

One row per predicted pocket.

| Column | Type | Notes |
|---|---|---|
| `name` | string | `pocket.name` (e.g. `pocket.1`) |
| `rank` | i32 | 1-based pocket rank |
| `score` | f64 | Raw P2Rank pocket score |
| `probability` | f64 | Calibrated probability from the score transformer. **Column is omitted entirely** when no transformer ran |
| `center_x`, `center_y`, `center_z` | f64 | Pocket centroid coordinates |
| *(one or more columns per requested descriptor)* | f64 / i32 | See descriptor catalog below |

Descriptor columns appear in the order given on the command line via
`-pocket_descriptors`. Most descriptors emit a single column whose header is
the descriptor name; multi-column descriptors emit N columns prefixed with
`"{name}."` (e.g. `principal_moments.lambda1`, `principal_moments.lambda2`,
`principal_moments.lambda3`).

## Descriptor catalog

| Name | Columns | Definition |
|---|---|---|
| `volume` | 1 × f64 | Pocket volume in **Å³**: `\|assigned grid points\| × pocket_grid_spacing³`. Accuracy scales with the lattice spacing (smaller `pocket_grid_spacing` → finer estimate). |
| `sphericity` | 1 × f64 ∈ [0, 1] | `V_pocket / V_bounding_sphere`. Bounding sphere is centered at the **centroid of the pocket's grid points** (not `pocket.centroid` which is atom-derived); radius is the max distance from that centroid. Quantization-free. 1 = perfect sphere; ≪ 1 = elongated / irregular. |
| `radius_of_gyration` | 1 × f64 | Radius of gyration in **Å**: `sqrt(mean(\|r_i - r_cm\|²))` over the pocket's grid points (equal weights). Absolute spatial extent — pairs well with `sphericity`, which only captures compactness. `0` for empty / single-point pockets. |
| `num_residues` | 1 × i32 | Number of distinct residues touching the pocket (reuses `Pocket.getResidues()`). |
| `num_surface_atoms` | 1 × i32 | Size of `pocket.surfaceAtoms`. |
| `num_grid_points` | 1 × i32 | Total grid points assigned to the pocket (cardinality of the BitSet after shape fill). Raw count complement to `volume`. |
| `principal_moments` | 3 × f64 | Three eigenvalues of the pocket grid points' gyration tensor (equal-weight PCA), sorted descending: `principal_moments.lambda1` ≥ `lambda2` ≥ `lambda3`. Unit Å². Shape signature: λ₁≈λ₂≈λ₃ → sphere; λ₁≫λ₂,λ₃ → rod; λ₁≈λ₂≫λ₃ → disk. Sum equals `radius_of_gyration²`. `0`s for pockets with <2 grid points. |

`-pocket_descriptors` defaults to **all of the above**. The grid-derived
scalar descriptors share the same pocket-grid input, so adding or removing
them costs essentially nothing once the grid is built. `principal_moments`
adds a small 3×3 eigendecomposition per pocket — also negligible relative
to the grid build itself. To narrow the set, list the wanted names
comma-separated. Unknown names cause a fail-fast error at startup with
the list of registered names.

## Parameters

The descriptors file shares all of the pocket-grid params (the grid is
built once and reused). The descriptor-specific knobs are:

| Parameter | Default | Notes |
|---|---|---|
| `export_pocket_descriptors` | `false` | Master gate |
| `pocket_descriptors` | all shipped descriptors | List of descriptor names to compute. See catalog above. |
| `pocket_grid_format` | `csv.gz` | Same allowed values as the grid file |

The grid generator's params (`pocket_grid_spacing`, `_max_dist`,
`_atom_buffer`, `_assign_cutoff`, `_fill`, `_fill_*`) directly affect
the `volume` and `sphericity` descriptors — see
[`export-pocket-grid.md`](export-pocket-grid.md).

## Adding a new descriptor

Implementations live under
`src/main/groovy/cz/siret/prank/program/routines/predict/output/descriptors/`.

1. Implement the `PocketDescriptor` interface:
   ```java
   String name();                          // CLI token and multi-column header prefix
   List<String> columnNames();             // sub-names; scalar entry IGNORED at output
   List<ColumnType> columnTypes();         // parallel to columnNames()
   double[] compute(PocketGridContext);    // same length as columnNames()
   boolean needsGrid();                    // default true; override to false if compute()
                                           // doesn't read ctx.grid() or ctx.gridPointIndices()
   ```
   `PocketGridContext` exposes `pocket`, `protein`, `grid`, and the
   per-pocket `gridPointIndices` set. If your `compute()` only reads
   `ctx.pocket()` (i.e., domain fields like `surfaceAtoms` or `residues`),
   override `needsGrid()` to return `false` — that lets the orchestrator
   skip the full grid build when only grid-free descriptors are selected.

   For **scalar** descriptors (one column), extend `AbstractScalarPocketDescriptor`
   instead of implementing the interface directly — it boils the boilerplate down
   to `name()`, `scalarType()`, and `computeScalar(ctx)`. Of the seven shipped
   descriptors, six use this adapter; `principal_moments` (multi-column) implements
   `PocketDescriptor` directly.

   For **multi-column** descriptors (e.g. `principal_moments` with three
   eigenvalues from a single decomposition), implement `PocketDescriptor`
   directly; output column headers are `"{name()}.{columnNames()[i]}"`.

2. Register the implementation in `PocketDescriptorRegistry`'s static
   initializer (Java; no auto-discovery). The registry rejects descriptors
   that declare duplicate `columnNames` at registration time.

3. Users can opt into it by name via
   `-pocket_descriptors "volume,my_new_descriptor"`.

4. **To include it in the default output**, also add the name to the
   `pocket_descriptors` default list in `Params.groovy`. The default is
   declared explicitly rather than derived from `Registry.knownNames()`
   so that adding a descriptor doesn't silently change every existing
   user's output schema — that's intentional; skip step 4 if the new
   descriptor is opt-in only.

INT columns return their value as a `double` that the writer downcasts at
output time, matching the existing `TableData` convention. Implementations
must guarantee the value fits in i32.

## See also

- [`export-pocket-grid.md`](export-pocket-grid.md) — the underlying
  grid that volume/sphericity are computed against
- [`export-points.md`](export-points.md) — SAS-points export
