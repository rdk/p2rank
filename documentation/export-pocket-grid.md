# Exporting the Pocket Grid

> [!WARNING]
> **Preview in 2.6.** Behavior, parameters, and output schema should be
> considered subject to change before this graduates (parameter
> names/defaults, the default set of per-grid-point descriptors, output
> column order, and similar). To
> stay forward-compatible: parse by column **name**, and pass
> `-pocket_grid_point_descriptors` explicitly. Feedback welcome via
> GitHub issues.

Export a regular 3D grid of points covering the empty space around the
protein, with per-pocket assignment. Produced alongside any
`predict` or `rescore` run when `-export_pocket_grid` is on.

## Quick start

```bash
prank predict -f protein.pdb -export_pocket_grid 1
prank predict -f protein.pdb -export_pocket_grid 1 -pocket_grid_format parquet
prank rescore  fpocket.ds    -export_pocket_grid 1 -pocket_grid_format arrow.zst

# Also produce PyMOL/ChimeraX visualization overlays
prank predict -f protein.pdb -export_pocket_grid 1 -vis_pocket_grid 1

# Include unassigned points (pocket=0) in the tabular file — e.g. to see the
# full atom-driven lattice, including the outer shell outside every pocket
prank predict -f protein.pdb -export_pocket_grid 1 -pocket_grid_include_unassigned 1
```

## Algorithm

The grid is sampled as a shell around the whole protein; per-pocket
assignment then restricts which points belong to which pocket. Most kept
points stay unassigned unless you export them with
`pocket_grid_include_unassigned` (see below).

1. **Grid generation.** Lattice points are sampled inside the bounding
   box of the protein (+ cofactor) atoms, expanded by
   `pocket_grid_max_dist` in each direction. Lattice edge is
   `pocket_grid_spacing`.
2. **Filtering.** A point is **kept** if both, measured against the
   nearest protein/cofactor atom:
   - it lies within `pocket_grid_max_dist` of that atom (outer bound:
     shell around the protein), and
   - its distance to that atom is at least
     `vdw_radius(nearest) + pocket_grid_atom_buffer` (inner bound:
     keep grid points out of physical atom volume).

   Per-atom VdW radii come from CDK's `Elements` enum, with a 2.02 Å
   fallback for the handful of metals that have a null radius in CDK.

   > [!NOTE]
   > `pocket_grid_max_dist` is measured from protein **atoms**, so the
   > sampled lattice covers a shell around the entire protein, not just the
   > predicted pockets. Widening it grows the grid quickly (volume scales
   > with the shell thickness). The pocket-local subset is the union of the
   > per-pocket raw shells from step 3.
3. **Per-pocket assignment.** For each predicted pocket, the *raw shell*
   is the set of kept points within `pocket_grid_assign_cutoff` of any
   of the pocket's `sasPoints` (the surface-accessible sampling points
   P2Rank scores). Pockets that expose no `sasPoints` get an empty
   assignment.
4. **Shape fill** (`-pocket_grid_fill`):
   - `closing` (default): true morphological closing (dilate by
     `pocket_grid_fill_close_radius` lattice layers, then erode by the same).
     Fills enclosed holes and concavities up to `~2 * radius` cells wide without
     advancing the pocket's outer boundary, so it solidifies the cavity without
     ballooning into solvent or neighbouring pockets.
   - `morph_closing`: iterative 26-neighborhood conditional dilation (no erode);
     promotes candidate cells whose filled-neighbor count reaches
     `pocket_grid_fill_min_neighbors`, up to `pocket_grid_fill_max_iters`
     iterations.
   - `none`: keep the raw shell exactly.

   > [!WARNING]
   > `morph_closing` with a low `pocket_grid_fill_min_neighbors` (a flat dilation
   > front presents 9 filled neighbors, so anything below ~10 never stops) grows
   > each pocket outward until `pocket_grid_fill_max_iters`, which inflates the
   > volume descriptor and makes pockets engulf one another. The default is now
   > `closing`; if you select `morph_closing`, keep `min_neighbors` >= 10 (the
   > default), the tightest value that still stops the runaway.
5. **Cross-pocket fill rule (always on).** A point added by *filling* (i.e.
   beyond a pocket's `pocket_grid_assign_cutoff`, so not in its raw shell) is
   dropped if it lies in another pocket's raw shell. Fill may expand into
   unclaimed space, but cannot swallow grid points that are within
   `pocket_grid_assign_cutoff` of a different pocket.
6. **Multi-pocket membership.** A grid point may still belong to more than one
   pocket via genuine within-cutoff sharing (it is in more than one pocket's raw
   shell); each `(point, pocket)` membership produces its own row.

## Output format

Long format. One row per `(point, pocket)` pair.

| Column | Type | Description |
|---|---|---|
| `x`, `y`, `z` | f64 | Grid point coordinate (Å) |
| `pocket` | i32 | Pocket rank this row belongs to (1-based). `0` only when `-pocket_grid_include_unassigned` is on (tabular export only). |
| *(per-point descriptor columns)* | f64 / i32 | Appended in `-pocket_grid_point_descriptors` order. See the per-grid-point descriptors section below. |

Rows are sorted by `pocket` ascending, then by `x`, `y`, `z` ascending.
Pocket `0` rows (if enabled) go last, so readers that only care about
assigned points can stop early.

### Non-finite values

- **`NaN` floats** are written as the literal token `NaN` in CSV, and as the
  standard IEEE-754 NaN bit pattern in Arrow / Parquet. pandas / pyarrow / numpy
  parse this back as NaN without special handling.
- **Infinities** are written as `Infinity` / `-Infinity` in CSV.
- **Non-finite or out-of-range values in INT columns** (e.g. `pocket`) raise an
  `ArithmeticException` at write time. The strict check surfaces such bugs early.

## Per-grid-point descriptors

Extra columns can be appended to each row via `-pocket_grid_point_descriptors`
(comma-separated names; default: all registered descriptors; see the catalog
below). Setting this knob without `-export_pocket_grid 1` is inert: the
per-grid-point compute only runs when the grid is being exported.
Multi-column descriptors get the header prefix `"{name}."`, same
convention as `-pocket_descriptors`.

> [!NOTE]
> **Cost note.** Per-grid-point descriptors run once per
> `(point, pocket)` row (often 10⁴–10⁵ times per protein), each touching
> a neighborhood of protein atoms. The runner memoises pocket-agnostic
> descriptors (volsite, volsite_smooth, electrostatics are all pocket-
> agnostic today) once per `pointIdx` so multi-pocket overlap doesn't
> multiply the cost. Pass a subset list to opt out of unwanted columns.

| Name | Columns | Description |
|---|---|---|
| `volsite` | 6 × i32 | Per-VolSite-pharmacophore indicator columns: `volsite.vsAromatic`, `volsite.vsCation`, `volsite.vsAnion`, `volsite.vsHydrophobic`, `volsite.vsAcceptor`, `volsite.vsDonor`. Each column is `1` if any protein atom carrying that pharmacophore type (per `VolSitePharmacophore`) lies within `-pocket_grid_volsite_radius` of the grid point, else `0`. |
| `volsite_smooth` | 6 × f64 | Gaussian-smoothed analogue of `volsite`. Each column is the sum of `exp(-r² / (2σ²))` over protein atoms carrying that pharmacophore type, where `σ = -pocket_grid_volsite_sigma`. Kernel truncated at `4σ`. Captures both proximity and atom count. |
| `electrostatics` | 5 × f64 | Coulomb-flavour scalars over protein atoms within `-electrostatics_radius` of the grid point, using AMBER ff14SB partial charges (with element-bucket fallback for HETATM atoms). Columns: `electrostatics.potential` = `Σ qᵢ/rᵢ` (signed, `e/Å`); `electrostatics.field_magnitude` = `‖Σ qᵢ·r⃗ᵢ/rᵢ³‖` (`e/Å²`); `electrostatics.positive` and `electrostatics.negative` = same potential restricted to cationic / anionic charges; `electrostatics.polarity` = `(pos − neg) / (pos + neg + ε)` ∈ [−1, 1] normalised polarity. All five share one neighbor walk via `CoulombKernel`. See [electrostatics implementation report](../misc/dev/ELECTROSTATICS_IMPLEMENTATION.md). |

Atom-level pharmacophore classification reuses the same `VolSitePharmacophore`
rules that drive the `volsite` per-atom feature in P2Rank's feature set:
a `1` in `volsite.vsCation` here corresponds to the same atom type that
would mark `vsCation=1` in `VolsiteFeature`. The electrostatics descriptor
reuses the same `PartialChargeTable` consumed by the SAS-level
`electrostatics` feature.

Descriptor params:

| Parameter | Default | Notes |
|---|---|---|
| `pocket_grid_point_descriptors` | `volsite, volsite_smooth, electrostatics` | List of names from `PocketGridPointDescriptorRegistry`. Validated at startup. Set to an empty list to skip per-point compute and emit only x/y/z/pocket. |
| `pocket_grid_volsite_radius` | `4.0` Å | Cutoff radius for the `volsite` indicator. Standard VolSite pharmacophore search distance. |
| `pocket_grid_volsite_sigma` | `2.0` Å | Gaussian σ for `volsite_smooth`. Kernel truncated at `4σ`. |
| `electrostatics_radius` | `6.0` Å | Cutoff radius for the `electrostatics` descriptor's Coulomb sum. |
| `electrostatics_min_r` | `1.5` Å | Lower clamp on `r` to avoid the 1/r singularity at vdW overlap. |

### Adding a new per-grid-point descriptor

Implementations live under
`src/main/groovy/cz/siret/prank/program/routines/predict/output/grid/descriptors/`.

1. Implement the `PocketGridPointDescriptor` interface (`name`, `columnNames`,
   `columnTypes`, `compute`). The shape mirrors the per-pocket
   `PocketDescriptor` (see
   [`export-pocket-descriptors.md`](export-pocket-descriptors.md#adding-a-new-descriptor)
   for the full recipe), with two differences:
   - **No `needsGrid()` method.** Every grid-point descriptor needs the grid
     by definition: the grid is the substrate that defines what a grid point
     is. The orchestrator always builds the grid when any grid-point
     descriptor is selected.
   - **No `AbstractScalarPocketDescriptor`-style adapter.** All three registered
     descriptors (`volsite`, `volsite_smooth`, `electrostatics`) are
     multi-column; if you add the first scalar grid-point descriptor and it's
     the only one, implement `columnNames()` as a single-element list and rely
     on the bare `name()` output convention. If a second arrives, factor out
     an adapter then.

2. Register in `PocketGridPointDescriptorRegistry`'s static initializer. The
   registry rejects descriptors with duplicate `columnNames` at registration
   time.

3. Users override the active set by name: `-pocket_grid_point_descriptors "volsite,my_new_descriptor"`.

4. To include the new descriptor in the default schema, add its name to the
   `pocket_grid_point_descriptors` default list in `Params.groovy`. Adding to
   the default IS a user-visible breaking change for anyone parsing the CSV
   by column index. Note it in [`breaking-changes.md`](../breaking-changes.md)
   and parse the output by column **name**, not by index.

## Parameters

| Parameter | Default | Notes |
|---|---|---|
| `export_pocket_grid` | `false` | Master gate for the grid file |
| `vis_pocket_grid` | `false` | Also render grid-overlay scripts for every renderer in `-vis_renderers` (PyMOL `.pml` and/or ChimeraX `.cxc`). Requires `export_pocket_grid=true`. |
| `pocket_grid_format` | `csv.gz` | One of `csv`, `csv.gz`, `csv.zst`, `arrow`, `arrow.gz`, `arrow.zst`, `parquet` |
| `pocket_grid_include_unassigned` | `false` | Write `pocket=0` rows for points outside every pocket. **Tabular export only** — the PyMOL/ChimeraX PDB sidecar always shows assigned points only. |
| `pocket_grid_spacing` | `1.2` Å | Lattice edge. Volume scales with this³ |
| `pocket_grid_max_dist` | `4.0` Å | Outer bound. Drop points farther than this from the **nearest protein/cofactor atom** (the grid is a shell around the whole protein). |
| `pocket_grid_atom_buffer` | `1.0` Å | Inner bound. Drop points where `dist(nearest atom) < vdw(nearest) + buffer`. |
| `pocket_grid_assign_cutoff` | `2.5` Å | Membership cutoff vs. `pocket.sasPoints` |
| `pocket_grid_assigner` | `kdtree` | Range-query strategy: `kdtree`, `voxel_hash`. `kdtree` is typically faster for fine grids (small `pocket_grid_spacing`); `voxel_hash` is typically faster for coarse grids. Both produce identical results. |
| `pocket_grid_fill` | `closing` | Shape strategy: `closing` (true dilate-then-erode), `morph_closing` (conditional dilation, no erode), `none`. |
| `pocket_grid_fill_close_radius` | `1` | `closing` only. Closing radius (dilate then erode by this many lattice layers); closes gaps up to `~2 * radius` cells. Keep small (1-2) to avoid bridging neighbouring pockets. |
| `pocket_grid_fill_min_neighbors` | `10` | `morph_closing` only. Filled-neighbor count (of 26) to promote a candidate; must exceed the 9 a flat front presents, so 10 is the tightest runaway-safe value (fills concavities, not flat surfaces). |
| `pocket_grid_fill_max_iters` | `10` | `morph_closing` only. Iteration cap (guard against runaway dilation). |
| `vis_pocket_grid_volume_radius` | `-1` (auto = `0.85 × spacing`, ≈ 1.02 Å at default spacing) | Visualization-only. Sphere radius around each grid point in the PML's vdW-radius volumetric layer (`pocket_vol_N`). `-1` is a sentinel meaning "scale with spacing"; any positive value overrides with an explicit Å. The auto-scaled value sits just above the 3D-diagonal merge threshold (`spacing × √3 / 2 ≈ 0.866 × spacing`), so neighbors overlap in every direction (axes, 2D and 3D diagonals) and the surface reads as one clean continuous blob per pocket. Going much below `~spacing/2` leaves spheres too disconnected for PyMOL's surface algorithm: most of the mesh falls below the rendering threshold and looks like missing surface. |
| `vis_pocket_grid_gaussian_iso` | `0.5` | Visualization-only. Iso-surface threshold for the Gaussian-density layer (`pocket_gauss_N`). Lower = looser surface farther from points; higher = tighter surface around densest regions. |

## PyMOL / ChimeraX visualization

When `-vis_pocket_grid 1` is set (in addition to
`-export_pocket_grid 1`), extra files are produced under
`visualizations/`.

### Files produced

| File | Contents |
|---|---|
| `data/{name}_pocket_grid.pdb.gz` | One HETATM per `(point, pocket)` pair; pocket rank stored in the residue-sequence column |
| `{name}_pocket_grid.pml` | PyMOL overlay script (emitted when `pymol` is in `-vis_renderers`). Starts with `@{name}_pymol.pml` to inherit the entire standard visualization (protein surface, ligands, cofactors, SAS points, pocket centroids, per-pocket surface coloring), then adds four togglable layers per pocket (see next section). All layers share the standard per-pocket palette so they line up visually with `surf_pocket_N`. |
| `{name}_pocket_grid.cxc` | ChimeraX overlay script (emitted when `chimerax` is in `-vis_renderers`). Mirrors the PyMOL overlay with two togglable layers instead of four. Opens `{name}_chimerax.cxc` to inherit the standard scene, then loads the same grid PDB. |

To view: `pymol {name}_pocket_grid.pml` from the `visualizations/`
directory (the `@`-include and the `load data/...` line both use
relative paths). Because the grid PML delegates everything except the
grid spheres + volume to the main pml, any change to `PymolRenderer`
(palette, ligand styling, cofactor handling, …) is picked up
automatically. No need to keep the two scripts in sync by hand.

### Layers and toggles

PyMOL layers per pocket:

| Layer | Default | Description |
|---|---|---|
| `pocket_grid_N` | on | Discrete grid points as spheres |
| `pocket_vol_N` | on | Translucent vdW-radius surface union. Grouped under `pocket_vol_all` for one-click toggle. Radius is `vis_pocket_grid_volume_radius`. |
| `pocket_gauss_N` | off | Gaussian-density iso-surface (smooth blob). Threshold is `vis_pocket_grid_gaussian_iso`. |
| `pocket_hull_N` | off | Convex-hull wireframe. Requires scipy. |

ChimeraX layers per pocket (tested with ChimeraX 1.11+):

| Layer | Default | Description |
|---|---|---|
| `#99.N` | on | Discrete spheres, split per pocket as `#99.1`, `#99.2`, … under parent model `#99` |
| `#100.N` | on | vdW-radius molecular surface, ~20% translucent, split as `#100.1`, `#100.2`, … under parent model `#100` |

The PyMOL overlay's Gaussian-iso and convex-hull layers are
PyMOL-only: ChimeraX cxc is command-only (no inline Python), and
`volume gaussian` returns an auto-IDed model the script can't style
afterward. Power users can build the gaussian blob manually after
opening the cxc with `volume gaussian #99 sDev 1.0 step 0.5`.

PyMOL commands for toggling layers:

| Command | Effect |
|---|---|
| `disable pocket_vol_all`   | Hide the vdW-radius surface for every pocket (group, single click) |
| `enable pocket_vol_all`    | Show the vdW-radius surface again (group, single click) |
| `enable pocket_gauss_all`  | Show the smooth Gaussian-density iso-surface for every pocket |
| `enable pocket_hull_all`   | Show the convex-hull wireframe for every pocket (scipy required) |
| `disable pocket_grid_all`  | Hide the discrete-sphere layer (group, single click) |
| `enable pocket_vol_2` / `disable pocket_vol_2` | Toggle just pocket 2's surface |

Or click the eye icon next to each object in PyMOL's right-panel
object tree.

### Renderer notes

**Translucency overrides.** The overlay sets `transparency, 0.5` on the
inherited protein surface and `show cartoon, protein` so the volumetric
pocket layer (and the inner pocket cavity) is visible through it while
the protein still reads as a proper structure (matches the default
ChimeraX feel). These overrides are local to the grid pml; the
standalone `{name}_pymol.pml` keeps the protein opaque, surface-only.

**ChimeraX `probeRadius` workaround.** The vdW surface uses a small
non-zero `probeRadius` (0.4 Å) because ChimeraX SES crashes on
`probeRadius 0` with a numpy broadcast error on every version we've
tested (1.8 through 1.12rc). SES is fundamentally defined with a
positive probe, so this is a permanent workaround. The visible surface
radius in ChimeraX is therefore `vis_pocket_grid_volume_radius + 0.4 Å`,
slightly larger than the same surface in PyMOL (which honors
`solvent_radius=0`).

**Pocket rank cap.** Pocket ranks are capped at 9999 by the PDB
residue column width. Not a real concern for protein pockets
(typically < 100).

**Master switches.** The PML respects the global `-visualizations`
switch; if visualizations are off globally, the grid PML is skipped as
well. It also assumes `pymol` is in `-vis_renderers` (default).
Without it the main pml is never written and the `@`-include fails at
PyMOL load time.

## See also

- [`export-pocket-descriptors.md`](export-pocket-descriptors.md):
  per-pocket geometric descriptors written to a sibling file. Most
  descriptors are grid-derived and trigger this same grid build even
  with `-export_pocket_grid 0`; the exceptions are `num_residues`,
  `num_surface_atoms`, `pocket_net_charge`, `pocket_charge_polarity`,
  and `pocket_dipole_magnitude` (no grid needed).
- [`export-points.md`](export-points.md): SAS-points export (the
  closest analogue for surface-only data).
