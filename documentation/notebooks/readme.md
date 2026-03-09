# P2Rank Notebooks

Example Jupyter notebooks for working with P2Rank output.

## Notebooks

- **analyze_p2rank_output.ipynb** - Load and explore `_predictions.csv` and `_residues.csv` output files using pandas.

## Requirements

```bash
pip install pandas matplotlib jupyter
```

## Running

```bash
cd documentation/notebooks
jupyter notebook
```

Then open `analyze_p2rank_output.ipynb`.

The notebook uses example output in `p2rank_output/predict_1fbl/` which is included in this directory.
