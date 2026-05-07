# SwinSite predictions

Predicted using SwinSite (3D Swin-Transformer voxel-density binding-site
predictor; https://doi.org/10.1021/acs.jcim.5c02734).

Per-protein directory holds two mol2 files per detected pocket:

  - `pocket{N}_score_{S:.4f}.mol2`  protein atoms within 4.5 A of the pocket grid
  - `grid{N}_score_{S:.4f}.mol2`    raw voxel points (~1.5 A spacing, type Du)

`SwinSiteLoader` reads only `grid*.mol2`. Pockets are re-ranked by score
descending after parsing, so on-disk file index N does not correspond to
the loader's pocket rank.

## Fixtures

- `1tjw_A/` — 6 pockets, top score 0.2778. From SwinSite's `test_protein_only`
  example. The single `pocket0_*.mol2` is included as a representative
  sample of the format the loader discards.
- `1atlA/` — 3 pockets with non-monotonic original scores (0.7288, 0.0664,
  0.3433). Exercises the score-descending rerank path. From a SwinSite
  run on the coach420 dataset.

The full set used by `SwinSiteLoaderTest` and `swinsite.ds` lives under
`distro/test_data/predictions/swinsite/` (1tjw_A, 1a26A, 1a2kC, 1afkA,
1atlA, 1bqoB).
