# Exporting Pocket Descriptors

> [!WARNING]
> **Preview in 2.6.** Behavior, parameters, and output schema should be
> considered subject to change before this graduates (parameter
> names/defaults, the default descriptor set, output column order, and
> similar). To stay forward-compatible:
> parse by column **name**, and pass `-pocket_descriptors` explicitly.
> Feedback welcome via GitHub issues.

Per-pocket geometric, shape, and electrostatic descriptors (volume,
sphericity, residue counts, net charge, dipole magnitude, etc.) written
to a tabular file alongside any `predict` or `rescore` run when
`-export_pocket_descriptors` is on.

> [!NOTE]
> **Cost note.** The shape descriptors (`volume`, `sphericity`,
> `radius_of_gyration`, `num_grid_points`, `principal_moments`) read
> the pocket grid, so selecting any of them triggers the full grid
> build (lattice + assignment + shape fill) even with
> `-export_pocket_grid 0`. The `-export_pocket_grid` flag only
> suppresses the per-protein grid file, not the computation. The
> grid-free descriptors (`num_residues`, `num_surface_atoms`,
> `pocket_net_charge`, `pocket_charge_polarity`,
> `pocket_dipole_magnitude`) operate on `pocket.surfaceAtoms` and the
> derived residue list only; selecting only those with
> `-export_pocket_grid 0` skips the grid build entirely.

## Quick start

```bash
# Default: every registered descriptor: num_residues, num_surface_atoms,
# num_grid_points, volume, sphericity, radius_of_gyration, principal_moments,
# pocket_net_charge, pocket_charge_polarity, pocket_dipole_magnitude
prank predict -f protein.pdb -export_pocket_descriptors 1

# Narrow set + tighter grid for more accurate volume/sphericity
prank predict dataset.ds -export_pocket_descriptors 1 \
    -pocket_descriptors "volume,sphericity" \
    -pocket_grid_spacing 0.75

# Rescoring path (e.g. on a Fpocket .ds dataset) also supports it
prank rescore fpocket.ds -export_pocket_descriptors 1
```

## Output format

One row per predicted pocket.

| Column | Type | Notes |
|---|---|---|
| `name` | string | `pocket.name` (e.g. `pocket1`) |
| `rank` | i32 | 1-based pocket rank |
| `score` | f64 | Raw P2Rank pocket score |
| `probability` | f64 | Calibrated probability from the score transformer. **Column is omitted entirely** when no transformer ran. |
| `center_x`, `center_y`, `center_z` | f64 | Pocket centroid coordinates |
| *(one or more columns per requested descriptor)* | f64 / i32 | See descriptor catalog below |

Descriptor columns appear in the order given on the command line via
`-pocket_descriptors`. Most descriptors emit a single column whose
header is the descriptor name; multi-column descriptors emit N columns
prefixed with `"{name}."` (e.g. `principal_moments.lambda1`,
`principal_moments.lambda2`, `principal_moments.lambda3`).

### Non-finite values and degenerate pockets

- Shape descriptors (`volume`, `radius_of_gyration`, `principal_moments`,
  `sphericity`) return **0** for degenerate pockets (empty or single-point).
  Use the `num_grid_points` / `num_surface_atoms` columns to distinguish a
  real "0" from a "we couldn't compute".
- **`NaN` floats** are written as the literal token `NaN` in CSV and as the
  IEEE-754 NaN bit pattern in Arrow / Parquet. pandas / pyarrow / numpy parse
  this back as NaN without special handling.
- **Infinities** are written as `Infinity` / `-Infinity` in CSV.
- **Non-finite or out-of-range values in INT columns** (`rank`,
  `num_residues`, `num_surface_atoms`, `num_grid_points`) raise an
  `ArithmeticException` at write time. Descriptors must never produce
  non-finite values for integer columns; the strict check surfaces such
  bugs early.

## Descriptor catalog

| Name | Columns | Definition |
|---|---|---|
| `volume` | 1 × f64 | Pocket volume in **Å³**: `\|assigned grid points\| × pocket_grid_spacing³`. Accuracy scales with the lattice spacing (smaller `pocket_grid_spacing` → finer estimate). |
| `sphericity` | 1 × f64 ∈ [0, 1] | `V_pocket / V_bounding_sphere`. Bounding sphere is centered at the **centroid of the pocket's grid points** (not `pocket.centroid`, which is atom-derived); radius is the max distance from that centroid. Bounding sphere is continuous; volume numerator inherits the same lattice quantization as `volume`. 1 = perfect sphere; ≪ 1 = elongated / irregular. |
| `radius_of_gyration` | 1 × f64 | Radius of gyration in **Å**: `sqrt(mean(\|r_i - r_cm\|²))` over the pocket's grid points (equal weights). Absolute spatial extent; pairs well with `sphericity`, which only captures compactness. `0` for empty / single-point pockets. |
| `num_residues` | 1 × i32 | Number of distinct residues touching the pocket (reuses `Pocket.getResidues()`). |
| `num_surface_atoms` | 1 × i32 | Size of `pocket.surfaceAtoms`. |
| `num_grid_points` | 1 × i32 | Total grid points assigned to the pocket (cardinality of the BitSet after shape fill). Raw count complement to `volume`. |
| `principal_moments` | 3 × f64 | Three eigenvalues of the pocket grid points' gyration tensor (equal-weight PCA), sorted descending: `principal_moments.lambda1` ≥ `lambda2` ≥ `lambda3`. Unit Å². Shape signature: λ₁≈λ₂≈λ₃ → sphere; λ₁≫λ₂,λ₃ → rod; λ₁≈λ₂≫λ₃ → disk. Sum equals `radius_of_gyration²`. `0`s for pockets with <2 grid points. |
| `pocket_net_charge` | 1 × f64 | Sum of AMBER ff14SB partial charges of `pocket.surfaceAtoms`, in elementary charge units (`e`). Positive net = anion-binding site, negative = cation-binding, ≈ 0 = neutral / hydrophobic. Atoms outside the AMBER table get an element-bucket fallback. See [electrostatics implementation report](../misc/dev/ELECTROSTATICS_IMPLEMENTATION.md) for the cascade. |
| `pocket_charge_polarity` | 3 × f64 | `pocket_charge_polarity.positive` and `.negative` are the total cationic and anionic charge in `e` (the latter as a positive magnitude); `.ratio` = `(pos − neg) / (pos + neg + ε)` ∈ [−1, 1] is the normalised polarity. Distinguishes neutral pockets (low magnitudes) from bipolar pockets (large cancelling charges) that `pocket_net_charge` collapses to ≈ 0. |
| `pocket_dipole_magnitude` | 1 × f64 | Magnitude of the dipole moment of `pocket.surfaceAtoms` about their geometric centroid, in `e·Å`. Two pockets with identical `pocket_net_charge` can differ massively in dipole: bipolar pockets with opposing charge patches have a large dipole; uniformly neutral pockets have zero. |

`-pocket_descriptors` defaults to **all of the above**. The grid-derived
scalar descriptors share the same pocket-grid input, so adding or
removing them costs essentially nothing once the grid is built.
`principal_moments` adds a small 3×3 eigendecomposition per pocket;
the electrostatic descriptors share one walk over `pocket.surfaceAtoms`
via `PocketChargeStats`. All sub-millisecond per pocket. To narrow the
set, list the wanted names comma-separated. Unknown names cause a
fail-fast error at startup with the list of registered names.

## Parameters

The descriptors file shares all of the pocket-grid params (the grid is
built once and reused; see [`export-pocket-grid.md`](export-pocket-grid.md)
for the full list, including `-pocket_grid_format` which controls the
file format for both outputs). The descriptor-specific knobs are:

| Parameter | Default | Notes |
|---|---|---|
| `export_pocket_descriptors` | `false` | Master gate |
| `pocket_descriptors` | all registered descriptors | List of descriptor names to compute. See catalog above. |

The grid generator's params (`pocket_grid_spacing`, `_max_dist`,
`_atom_buffer`, `_assign_cutoff`, `_fill`, `_fill_*`) directly affect
the grid-derived descriptors (`volume`, `sphericity`,
`radius_of_gyration`, `num_grid_points`, `principal_moments`).

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
   `ctx.pocket()` (i.e., domain fields like `surfaceAtoms` or
   `residues`), override `needsGrid()` to return `false`. That lets the
   orchestrator skip the full grid build when only grid-free
   descriptors are selected.

   For **scalar** descriptors (one column), extend
   `AbstractScalarPocketDescriptor` instead of implementing the
   interface directly. It boils the boilerplate down to `name()`,
   `scalarType()`, and `computeScalar(ctx)`. Of the ten registered
   descriptors, eight use this adapter; `principal_moments` and
   `pocket_charge_polarity` (both multi-column) implement
   `PocketDescriptor` directly.

   For **multi-column** descriptors (e.g. `principal_moments` with
   three eigenvalues from a single decomposition), implement
   `PocketDescriptor` directly; output column headers are
   `"{name()}.{columnNames()[i]}"`.

2. Register the implementation in `PocketDescriptorRegistry`'s static
   initializer (Java; no auto-discovery). The registry rejects
   descriptors that declare duplicate `columnNames` at registration
   time.

3. Users can opt into it by name via
   `-pocket_descriptors "volume,my_new_descriptor"`.

4. **To include it in the default output**, also add the name to the
   `pocket_descriptors` default list in `Params.groovy`. The default is
   declared explicitly (rather than derived from
   `Registry.knownNames()`) so each addition to the default schema is a
   conscious choice. Adding to the default IS a user-visible breaking
   change for anyone parsing the output by column index. Two
   recommendations:
   - Parse the descriptors file by column **name**, not by column index.
   - When you add a descriptor to the default list, note it in
     [`breaking-changes.md`](../breaking-changes.md).

   Skip step 4 if the new descriptor is opt-in only.

INT columns return their value as a `double` that the writer downcasts
at output time, matching the existing `TableData` convention.
Implementations must guarantee the value fits in i32.

## See also

- [`export-pocket-grid.md`](export-pocket-grid.md): the underlying
  grid that volume/sphericity are computed against.
- [`export-points.md`](export-points.md): SAS-points export.
