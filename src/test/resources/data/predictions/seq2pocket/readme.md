# Seq2Pocket predictions

Predicted using Seq2Pocket (sequence-conditioned pocket predictor, Skrhak
et al. 2026, biorxiv 10.64898/2026.01.28.702257).

Per-protein directory holds `<ID>_predictions.txt`, a semicolon-delimited
CSV with one ranked pocket per body line:
`name;rank;score;residue_ids;atom_ids`. `Seq2PocketLoader` reads this file
only; sibling `_residues.txt` and `_meta.json` (when present in real runs)
are ignored.

## Fixtures

- `1a26A/` — verbatim copy of a real prediction (4 pockets, top score
  0.9256). Mirrors `distro/test_data/predictions/seq2pocket/1a26A/` so the
  loader can be exercised from an arbitrary path, not just the distro tree.
- `1a26A_unsorted/` — synthetic file with the same 4 pockets reordered
  (1,2,3,4 → 3,1,4,2). Exercises the defensive sort-desc-by-score path.
- `1a26A_headeronly/` — header line only, no body lines. Mirrors what real
  Seq2Pocket emits for a protein with zero predicted pockets.

The full distro fixture set used by `Seq2PocketLoaderTest` and the example
dataset lives under `distro/test_data/predictions/seq2pocket/` (1a26A,
1a2kC, 1afkA, 1atlA, 1bqoB).
