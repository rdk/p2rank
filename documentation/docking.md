# Preparing Docking Boxes from P2Rank Predictions

P2Rank predicts *where* a ligand is likely to bind, but it does not output a
ready-made docking search box. This page shows how to derive a box (a center
plus a size) for tools like AutoDock Vina, Glide, or GNINA from P2Rank output.

## Quick answer

- **Box center**: the pocket centroid (`center_x`, `center_y`, `center_z` in
  `*_predictions.csv`).
- **Box size**: the bounding box of the pocket's points, expanded by a margin
  of at least **half the longest dimension of the ligand** you intend to dock.
- P2Rank often marks only the hot core of a binding site, so err on the
  generous side and validate your box on a few complexes with known poses.

> [!NOTE]
> All P2Rank coordinates are in Angstrom and in the same frame as the input
> structure: there is no transformation, so a box built from P2Rank output
> drops straight into a docking tool that reads the same file.

## What P2Rank gives you to build a box

| Source | Produced by | Contents useful for a box |
|---|---|---|
| `*_predictions.csv` | always | Per-pocket centroid (`center_*`), `residue_ids`, `surf_atom_ids`, `sas_points` count |
| `visualizations/data/{name}_points.pdb.gz` | always | SAS point coordinates (score in the B-factor column, pocket rank in the residue-sequence column) |
| `{name}_points.{format}` | `-export_points 1` | Same SAS points as a clean table with a `pocket` column ([export-points.md](export-points.md)) |
| `{name}_pocket_grid.{format}` | `-export_pocket_grid 1` (2.6+) | A 3D lattice filling the pocket's empty space: the closest proxy to the dockable cavity ([export-pocket-grid.md](export-pocket-grid.md)) |
| `{name}_pocket_descriptors.{format}` | `-export_pocket_descriptors 1` (2.6+) | `volume`, `radius_of_gyration`, `principal_moments`: useful for sizing and for judging how elongated a pocket is ([export-pocket-descriptors.md](export-pocket-descriptors.md)) |

## Choosing a basis

| Basis | Pros | Cons |
|---|---|---|
| Centroid + fixed cube | Simplest; one number to tune | Ignores pocket shape and size; easy to make too small |
| **SAS-point bounding box + margin** | Traces the cavity mouth; works on any version | Surface sampling, not the cavity interior |
| Pocket-grid bounding box (2.6+) | Fills the empty cavity, the most faithful extent | Opt-in; only for `prank predict` pockets (loaded fpocket/etc. pockets have no SAS points) |

For most users the **SAS-point bounding box** is the recommended default. On
2.6+ the **pocket grid** is a good alternative when you want the extent of the
empty cavity rather than its surface.

## Recipe: box from SAS points

Export the points with the `pocket` column, then take the bounding box of the
pocket you care about.

```bash
prank predict -f protein.pdb -export_points 1 -export_points_format parquet
```

```python
import pandas as pd

pts = pd.read_parquet("test_output/predict_protein/protein.pdb_points.parquet")
pocket = pts[pts["pocket"] == 1]                 # top-ranked pocket; change as needed

mins = pocket[["x", "y", "z"]].min()
maxs = pocket[["x", "y", "z"]].max()
center = (mins + maxs) / 2
margin = 8.0                                      # >= half the ligand's longest dimension (Angstrom)
size = (maxs - mins) + 2 * margin

print("center_x, center_y, center_z =", center.round(3).tolist())
print("size_x,   size_y,   size_z   =", size.round(3).tolist())
```

The `center_*` and `size_*` values map directly onto AutoDock Vina's
`center_x/y/z` and `size_x/y/z` (both in Angstrom).

> [!TIP]
> If you only run plain `prank predict` (without `-export_points`), the same
> coordinates are already in `visualizations/data/{name}_points.pdb.gz`: the
> pocket rank is in the residue-sequence column and the score is in the
> B-factor column. `-export_points` just gives you a cleaner table to load.

## Recipe: box from the pocket grid (2.6+)

The grid covers the pocket's empty space, so its bounding box maps more
directly onto the volume a ligand can occupy.

```bash
prank predict -f protein.pdb -export_pocket_grid 1 -pocket_grid_format parquet
```

```python
import pandas as pd

grid = pd.read_parquet("test_output/predict_protein/protein.pdb_pocket_grid.parquet")
pocket = grid[grid["pocket"] == 1]

mins = pocket[["x", "y", "z"]].min()
maxs = pocket[["x", "y", "z"]].max()
center = (mins + maxs) / 2
margin = 4.0                                      # the grid already fills the cavity, so a smaller margin is usually enough
size = (maxs - mins) + 2 * margin
```

## Choosing the margin

- A good rule of thumb is a margin of **at least half the longest dimension of
  the ligand(s)** you are docking.
- P2Rank tends to flag the hotspot core of a site rather than its full extent,
  so a fixed cube centered on the centroid is easy to undersize for elongated
  ligands. Using the SAS-point or grid bounding box (which already follows the
  cavity) plus a margin compensates for this.
- Fragment screening can use smaller boxes; larger or more flexible ligands
  need more room.

> [!IMPORTANT]
> There is no universal margin. The reliable way to pick one is empirical: run
> P2Rank on a handful of complexes with known ligand poses, build boxes with
> your chosen method and margin, and check that each box actually contains the
> true pose. Adjust the method or margin and repeat.

## Caveats

- A **cubic** box (use `max(size_x, size_y, size_z)` for all three) is the
  simplest choice and is what some docking tools expect. An anisotropic box
  hugs the cavity more tightly but is not supported everywhere.
- For multi-pocket docking, loop over the pocket ranks (`pocket == 1`,
  `pocket == 2`, ...) and emit one box per pocket.
- Boundary points that fall within the extended shells of two pockets are
  labeled with the better (lower) rank, so a point belongs to exactly one
  pocket in the export (see [export-points.md](export-points.md)).

## See also

- [export-points.md](export-points.md): the SAS-point export used above.
- [export-pocket-grid.md](export-pocket-grid.md): the pocket-grid export.
- [export-pocket-descriptors.md](export-pocket-descriptors.md): per-pocket
  `volume` and shape descriptors, useful for sizing boxes.
- [user-guide.md](user-guide.md#63-tabular-data-exports): overview of the
  tabular exports.
