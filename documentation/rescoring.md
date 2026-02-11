# Rescoring Predictions from Other Methods

P2Rank can rescore pocket predictions from other binding site prediction tools,
re-ranking their pockets using its own ML model.

## Quick Start

```bash
prank rescore test_data/fpocket.ds                            # rescore fpocket predictions
prank rescore test_data/pocketeer.ds -o rescore_pocketeer     # rescore pocketeer, output to specific dir
prank eval-rescore test_data/fpocket.ds                       # rescore and evaluate against known ligands
prank fpocket-rescore test_data/basic.ds                      # run fpocket and rescore in one step
prank rescore test_data/pocketeer.ds -c rescore_2024          # use new experimental rescoring model
```

## Commands

| Command | Description                                                                                                                    |
|---------|--------------------------------------------------------------------------------------------------------------------------------|
| `prank rescore <dataset.ds>` | Rescore predictions and output re-ranked pockets.                                                                              |
| `prank eval-rescore <dataset.ds>` | Rescore and evaluate against known ligands.                                                                                    |
| `prank fpocket-rescore <dataset.ds>` | Run Fpocket on proteins, then rescore.<br> Convenience shortcut that can be used as a drop-in replacement for `prank predict`. |

## Supported Methods

| Method | `PREDICTION_METHOD` | Prediction column points to | Links |
|--------|--------------------|-----------------------------|-------|
| Fpocket | `fpocket` | Fpocket output file (`.pdb`/`.cif`) | [GitHub](https://github.com/Discngine/fpocket), [paper](https://doi.org/10.1186/1471-2105-10-168) |
| Pocketeer | `pocketeer` | `pockets.json` file | [GitHub](https://github.com/cch1999/pocketeer) |
| PUResNetV2.0 | `puresnet` | Directory with `*.pkt.pdb` files | [GitHub](https://github.com/jivankandel/PUResNetV2.0), [paper](https://doi.org/10.1186/s13321-024-00865-6) |
| ConCavity | `concavity` | `*_pocket.pdb` grid file | [project page](https://compbio.cs.princeton.edu/concavity/), [paper](https://doi.org/10.1371/journal.pcbi.1000585) |
| SiteHound | `sitehound` | `*_summary.dat` file | [paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC2703923/) |
| DeepSite | `deepsite` | Results PDB file | [paper](https://doi.org/10.1093/bioinformatics/btx350) |
| MetaPocket2 | `metapocket2` | PDB file with MPT residues | [paper](https://academic.oup.com/bioinformatics/article/27/15/2083/402380) |
| LISE | `lise` | PDB file with HETATM records | [paper](https://academic.oup.com/nar/article/41/W1/W292/1094035) |
| P2Rank | `p2rank` | `*_predictions.csv` file | [GitHub](https://github.com/rdk/p2rank), [paper](https://doi.org/10.1186/s13321-018-0285-8) |

## Dataset File Format

A dataset file (`.ds`) tells P2Rank which prediction method was used, and lists
pairs of prediction output files and their corresponding protein structures.

```text
# Lines starting with # are comments

PARAM.PREDICTION_METHOD=<method>

HEADER: prediction protein

path/to/prediction_output  path/to/protein.pdb
```

**Required elements:**
- `PARAM.PREDICTION_METHOD` -- name of the prediction method (see table above)
- `HEADER:` line -- defines column order (must include `prediction` and `protein`)
- Data rows -- whitespace-separated paths (relative to the `.ds` file location)

The `protein` column should point to the structure that was used as input to the
prediction tool. For `eval-rescore`, the protein must contain ligands (to compute
evaluation metrics). For plain `rescore`, ligands are not needed.

The column order in `HEADER:` is flexible -- `prediction protein` or
`protein prediction` are both valid.

## Examples

### Rescoring Fpocket predictions

`my_fpocket.ds`:
```text
PARAM.PREDICTION_METHOD=fpocket

HEADER: prediction protein

fpocket_output/1abc_out/1abc_out.pdb  structures/1abc.pdb
fpocket_output/2xyz_out/2xyz_out.pdb  structures/2xyz.pdb
```

```bash
prank rescore my_fpocket.ds
```

### Rescoring Pocketeer predictions

`my_pocketeer.ds`:
```text
PARAM.PREDICTION_METHOD=pocketeer

HEADER: prediction protein

pocketeer_output/1abc/pockets.json  structures/1abc.pdb
pocketeer_output/2xyz/pockets.json  structures/2xyz.cif
```

```bash
prank rescore my_pocketeer.ds
```

### Evaluating rescoring quality

Use `eval-rescore` with liganated proteins to compare the original ranking
against the rescored ranking. This works with any supported method.

`my_eval.ds`:
```text
PARAM.PREDICTION_METHOD=fpocket

HEADER: prediction protein

fpocket_output/1abc_out/1abc_out.pdb  liganated/1abc.pdb
fpocket_output/2xyz_out/2xyz_out.pdb  liganated/2xyz.pdb
```

```bash
prank eval-rescore my_eval.ds
```

This outputs evaluation metrics (DCA, DSO success rates, etc.) showing whether
rescoring improved pocket ranking.

## Output

For each protein, two files are generated in the output directory:

| File | Contents |
|------|----------|
| `{name}_rescored.csv` | Re-ranked pockets with new scores |
| `{name}_predictions.csv` | Pocket details (scores, centers, residues, surface atoms) |

The `_rescored.csv` contains columns:

| Column | Description |
|--------|-------------|
| `name` | Pocket name |
| `score` | New score assigned by P2Rank |
| `rank` | New rank (after rescoring) |
| `old_rank` | Original rank from the prediction method |

PyMOL visualization files are also generated by default (disable with `-visualizations 0`).

## Parameters

Override parameters on the command line with `-param value`.
A few commonly used parameters:

```bash
prank rescore dataset.ds -o output_dir -threads 4 -visualizations 0
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `-o` | auto-generated | Explicit output directory (overrides default) |
| `-threads` | all CPUs | Number of parallel threads |
| `-visualizations` | `true` | Generate PyMOL visualization files |
| `-fail_fast` | `false` | Stop on first error |
| `-model` | `default_rescore` | ML model to use for rescoring |

## Experimental Rescoring Model (`rescore_2024`)

An alternative rescoring model is available via `-c rescore_2024`. It uses a different feature set
that does not depend on B-factor, making it suitable for AlphaFold models, NMR, and cryo-EM structures.

```bash
prank rescore fpocket.ds -c rescore_2024
prank fpocket-rescore test.ds -c rescore_2024
prank eval-rescore fpocket.ds -c rescore_2024
```

This model shows promising results but has not been fully evaluated yet.

