# Exporting the Pocket Grid

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

# Include unassigned points (debugging the grid generator)
prank predict -f protein.pdb -export_pocket_grid 1 -pocket_grid_include_unassigned 1
```

## Algorithm

The grid is built around predicted pockets, not the whole protein —
both bounds of the sampled lattice are driven by per-pocket SAS points
(the surface-accessible sampling points that P2Rank scores). Pockets
that don't expose `sasPoints` (the field defaults to null for non-Prank
pocket subclasses) contribute nothing; if no pocket has SAS points, an
empty grid is produced with a warning.

1. **Grid generation.** Lattice points are sampled inside the bounding
   box of the union of `pocket.sasPoints` across every predicted pocket,
   expanded by `pocket_grid_max_dist` in each direction. Lattice edge is
   `pocket_grid_spacing`.
2. **Filtering.** A point is **kept** if both:
   - it lies within `pocket_grid_max_dist` of some pocket's SAS point
     (outer bound — pocket-vicinity-only grid), and
   - its distance to the nearest protein/cofactor atom is at least
     `vdw_radius(nearest) + pocket_grid_atom_buffer` (inner bound —
     keep grid points out of physical atom volume).

   Per-atom VdW radii come from CDK's `Elements` enum, with Krypton's
   2.02 Å as fallback for the handful of metals that have a null radius
   in CDK.
3. **Per-pocket assignment.** For each predicted pocket, the *raw shell*
   is the set of kept points within `pocket_grid_assign_cutoff` of any
   of the pocket's `sasPoints`.
4. **Shape fill** (`-pocket_grid_fill`):
   - `morph_closing` (default) — iterative 26-neighborhood dilation;
     promotes candidate cells whose filled-neighbor count reaches
     `pocket_grid_fill_min_neighbors`, up to `pocket_grid_fill_max_iters`
     iterations.
   - `none` — keep the raw shell exactly.
5. **Multi-pocket membership.** A grid point may belong to more than one
   pocket; each `(point, pocket)` membership produces its own row.

## Output format

Long format. One row per `(point, pocket)` pair.

| Column | Type | Description |
|---|---|---|
| `x`, `y`, `z` | f64 | Grid point coordinate (Å) |
| `pocket` | i32 | Pocket rank this row belongs to (1-based). `0` only when `-pocket_grid_include_unassigned` is on. |

Rows are sorted by `pocket` ascending, then by `x`, `y`, `z` ascending.
Pocket `0` (if enabled) goes last — readers that only care about
assigned points can stop early.

## Parameters

| Parameter | Default | Notes |
|---|---|---|
| `export_pocket_grid` | `false` | Master gate for the grid file |
| `vis_pocket_grid` | `false` | Also render grid-overlay scripts for every renderer in `-vis_renderers` (PyMOL `.pml` and/or ChimeraX `.cxc`). Requires `export_pocket_grid=true`. |
| `pocket_grid_format` | `csv.gz` | One of `csv`, `csv.gz`, `csv.zst`, `arrow`, `arrow.gz`, `arrow.zst`, `parquet` |
| `pocket_grid_include_unassigned` | `false` | Write `pocket=0` rows for points outside every pocket |
| `pocket_grid_spacing` | `1.2` Å | Lattice edge. Volume scales with this³ |
| `pocket_grid_max_dist` | `4.0` Å | Outer bound — drop points farther than this from any **pocket SAS point** (not from the protein as a whole) |
| `pocket_grid_atom_buffer` | `1.0` Å | Inner bound — drop points where `dist(nearest atom) < vdw(nearest) + buffer` |
| `pocket_grid_assign_cutoff` | `2.5` Å | Membership cutoff vs. `pocket.sasPoints` |
| `pocket_grid_assigner` | `kdtree` | Range-query strategy: `kdtree`, `voxel_hash`. `kdtree` is typically faster for fine grids (small `pocket_grid_spacing`); `voxel_hash` is typically faster for coarse grids. Both produce identical results. |
| `pocket_grid_fill` | `morph_closing` | Shape strategy: `morph_closing`, `none` |
| `pocket_grid_fill_min_neighbors` | `4` | `morph_closing` only — neighbor count threshold |
| `pocket_grid_fill_max_iters` | `10` | `morph_closing` only — iteration cap |
| `vis_pocket_grid_volume_radius` | `1.02` Å (= `0.85 × spacing`) | Visualization-only — sphere radius around each grid point in the PML's vdW-radius volumetric layer (`pocket_vol_N`). At the default value, neighbors overlap in every direction (axes, 2D and 3D diagonals) so the surface renders as a clean continuous blob per pocket. Going much below `~spacing/2` leaves spheres too disconnected for PyMOL's surface algorithm — most of the mesh falls below the rendering threshold and looks like missing surface. |
| `vis_pocket_grid_gaussian_iso` | `0.5` | Visualization-only — iso-surface threshold for the Gaussian-density layer (`pocket_gauss_N`). Lower = looser surface farther from points; higher = tighter surface around densest regions. |

## PyMOL visualization

When `-vis_pocket_grid 1` is set (in addition to
`-export_pocket_grid 1`), extra files are produced under
`visualizations/`:

| File | Contents |
|---|---|
| `data/{name}_pocket_grid.pdb.gz` | One HETATM per `(point, pocket)` pair; pocket rank stored in the residue-sequence column |
| `{name}_pocket_grid.pml` | PyMOL overlay script (emitted when `pymol` is in `-vis_renderers`). Starts with `@{name}_pymol.pml` — inheriting the entire standard visualization (protein surface, ligands, cofactors, SAS points, pocket centroids, per-pocket surface coloring) — then adds **four togglable layers per pocket**: `pocket_grid_N` (discrete grid points as spheres, **on by default**), `pocket_vol_N` (translucent vdW-radius surface union, **on by default**, grouped under `pocket_vol_all` for one-click toggle), `pocket_gauss_N` (Gaussian-density iso-surface — smooth blob, off), and `pocket_hull_N` (convex-hull wireframe, off, requires scipy). All layers share the standard per-pocket palette so they line up visually with `surf_pocket_N`. |
| `{name}_pocket_grid.cxc` | ChimeraX overlay script (emitted when `chimerax` is in `-vis_renderers`). **Tested with ChimeraX 1.11+.** The vdW surface uses a small non-zero `probeRadius` (0.4 Å) because ChimeraX SES crashes on `probeRadius 0` with a numpy broadcast error on every version we've tested (1.8 through 1.12rc) — SES is fundamentally defined with a positive probe, so this is a permanent workaround. The visible surface radius in ChimeraX is therefore `vis_pocket_grid_volume_radius + 0.4 Å` (slightly larger than the same surface in PyMOL, which honors `solvent_radius=0`). Mirrors the PyMOL overlay otherwise: opens `{name}_chimerax.cxc` to inherit the standard scene, then loads the same grid PDB and adds **two togglable layers** on model `#99`: discrete spheres (on) and vdW-radius molecular surface (on, ~20% translucent). The PyMOL overlay's Gaussian-iso and convex-hull layers are PyMOL-only — ChimeraX cxc is command-only (no inline Python), and `volume gaussian` returns an auto-IDed model the script can't style afterward. Power users can build the gaussian blob manually after opening the cxc with `volume gaussian #99 sDev 1.0 step 0.5`. |

To view: `pymol {name}_pocket_grid.pml` from the `visualizations/`
directory (the `@`-include and the `load data/...` line both use
relative paths). Because the grid PML delegates everything except the
grid spheres + volume to the main pml, any change to `PymolRenderer`
(palette, ligand styling, cofactor handling, …) is picked up
automatically — no need to keep the two scripts in sync by hand.

**Layer toggle.** Each pocket gets two independent objects in the PyMOL
right-panel object tree — click the eye icon to show/hide each layer
on its own. Or use commands:

| Command | Effect |
|---|---|
| `disable pocket_vol_all`   | Hide the vdW-radius surface for every pocket (group, single click) |
| `enable pocket_vol_all`    | Show the vdW-radius surface again (group, single click) |
| `enable pocket_gauss_all`  | Show the smooth Gaussian-density iso-surface for every pocket |
| `enable pocket_hull_all`   | Show the convex-hull wireframe for every pocket (scipy required) |
| `disable pocket_grid_all`  | Hide the discrete-sphere layer (group, single click) |
| `enable pocket_vol_2` / `disable pocket_vol_2` | Toggle just pocket 2's surface |

The volume surface is rendered as a vdW-style surface (solvent probe = 0)
of radius `vis_pocket_grid_volume_radius` (Å, default 1.02 ≈ `0.85 ×
default spacing`) around each grid point. The default sits just above
the 3D-diagonal merge threshold (`spacing × √3 / 2 ≈ 0.87 × spacing`),
so neighbors overlap in every direction and the surface reads as one
clean continuous blob per pocket. Going much below `~spacing/2` leaves
the spheres too disconnected for PyMOL's surface algorithm — most of
the mesh drops below the rendering threshold and the surface appears
"missing." The volume is slightly translucent by
default; the protein-transparency override (below) is what makes the
volume visible from outside.

The overlay also sets `transparency, 0.5` on the inherited protein
surface and `show cartoon, protein` so the volumetric pocket layer
(and the inner pocket cavity) is visible through it while the protein
still reads as a proper structure (matches the default ChimeraX feel).
These overrides are local to the grid pml — the standalone
`{name}_pymol.pml` keeps the protein opaque, surface-only.

Pocket ranks are capped at 9999 by the PDB residue column width — not
a real concern for protein pockets (typically < 100). The PML respects
the master `-visualizations` switch; if visualizations are off
globally, the grid PML is skipped as well. It also assumes `pymol` is
in `-vis_renderers` (default) — without it the main pml is never
written and the `@`-include fails at PyMOL load time.

## See also

- [`export-pocket-descriptors.md`](export-pocket-descriptors.md) —
  per-pocket geometric descriptors written to a sibling file. Most
  descriptors are grid-derived and trigger this same grid build even
  with `-export_pocket_grid 0`; the exceptions are `num_residues` and
  `num_surface_atoms` (no grid needed).
- [`export-points.md`](export-points.md) — SAS-points export (the
  closest analogue for surface-only data)
