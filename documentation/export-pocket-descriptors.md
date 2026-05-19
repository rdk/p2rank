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
# Default: every shipped descriptor (volume, sphericity, radius_of_gyration,
# num_residues, num_surface_atoms, num_grid_points)
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
| *(one column per requested descriptor)* | f64 / i32 | See descriptor catalog below |

Descriptor columns appear in the order given on the command line via
`-pocket_descriptors`.

## Descriptor catalog

| Name | Output | Definition |
|---|---|---|
| `volume` | f64 | Pocket volume in **Å³**: `\|assigned grid points\| × pocket_grid_spacing³`. Accuracy scales with the lattice spacing (smaller `pocket_grid_spacing` → finer estimate). |
| `sphericity` | f64 ∈ [0, 1] | `V_pocket / V_bounding_sphere`. Bounding sphere is centered at the **centroid of the pocket's grid points** (not `pocket.centroid` which is atom-derived); radius is the max distance from that centroid. Quantization-free. 1 = perfect sphere; ≪ 1 = elongated / irregular. |
| `radius_of_gyration` | f64 | Radius of gyration in **Å**: `sqrt(mean(\|r_i - r_cm\|²))` over the pocket's grid points (equal weights). Absolute spatial extent — pairs well with `sphericity`, which only captures compactness. `0` for empty / single-point pockets. |
| `num_residues` | i32 | Number of distinct residues touching the pocket (reuses `Pocket.getResidues()`). |
| `num_surface_atoms` | i32 | Size of `pocket.surfaceAtoms`. |
| `num_grid_points` | i32 | Total grid points assigned to the pocket (cardinality of the BitSet after shape fill). Raw count complement to `volume`. |

`-pocket_descriptors` defaults to **all of the above** — they share the
pocket-grid input, so adding more is essentially free once the grid is
built. To narrow the set, list the wanted names comma-separated.
Unknown names cause a fail-fast error at startup with the list of
registered names.

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
   String name();
   ColumnType columnType();
   double compute(PocketGridContext ctx);
   boolean needsGrid();   // default true — override to false if compute()
                          // doesn't read ctx.grid() or ctx.gridPointIndices()
   ```
   `PocketGridContext` exposes `pocket`, `protein`, `grid`, and the
   per-pocket `gridPointIndices` set. If your `compute()` only reads
   `ctx.pocket()` (i.e., domain fields like `surfaceAtoms` or `residues`),
   override `needsGrid()` to return `false` — that lets the orchestrator
   skip the full grid build when only grid-free descriptors are selected.
   Leaving it at the default-true on a truly grid-free descriptor is not
   incorrect, but forces a wasted build per protein.

2. Register the implementation in `PocketDescriptorRegistry`'s static
   initializer (Java; no auto-discovery).

3. Users can opt into it by name via
   `-pocket_descriptors "volume,my_new_descriptor"`.

4. **To include it in the default output**, also add the name to the
   `pocket_descriptors` default list in `Params.groovy`. The default is
   declared explicitly rather than derived from `Registry.knownNames()`
   so that adding a descriptor doesn't silently change every existing
   user's output schema — that's intentional; skip step 4 if the new
   descriptor is opt-in only.

INT descriptors return their value as a `double` that the writer
downcasts at output time, matching the existing `TableData` convention.
Implementations must guarantee the value fits in i32 (no concern in
practice for pocket-grid counts).

## See also

- [`export-pocket-grid.md`](export-pocket-grid.md) — the underlying
  grid that volume/sphericity are computed against
- [`export-points.md`](export-points.md) — SAS-points export
