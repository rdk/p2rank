# Exporting SAS Points with Feature Vectors

Export Solvent Accessible Surface (SAS) points with their feature vectors and predicted ligandability scores using the `-export_points` parameter.

## Usage

```bash
prank predict -f protein.pdb -export_points 1
prank predict -f protein.pdb -export_points 1 -export_points_format csv.gz
prank predict dataset.ds     -export_points 1 -export_points_format csv.zst
```

The `rescore` command also supports export (pocket points only):
```bash
prank rescore fpocket.ds -export_points 1
```

## Output

For each protein file, a `{protein_file}_points.csv` (or `.csv.gz`/`.csv.zst`) is generated:

| Column | Description |
|--------|-------------|
| `x`, `y`, `z` | SAS point coordinates |
| `score` | Predicted ligandability (0-1) |
| `feature1`, ... | Feature values used by the model |

Example:
```csv
x,y,z,score,chem.hydrophobic,chem.aromatic,protrusion,...
12.3456,23.4567,34.5678,0.8234,0.5123,-0.2345,15.0000,...
```

## Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `export_points` | `false` | Enable point export |
| `export_points_format` | `csv` | Format: `csv`, `csv.gz`, or `csv.zst` |

## Notes

- `predict` exports all SAS points; `rescore` exports only pocket points
- All values use 7 decimal places
- Disabled when using `-output_only_stats 1`

## Example Analysis

```python
import pandas as pd
df = pd.read_csv('protein_points.csv.gz')
high_score = df[df['score'] > 0.5]
print(df.describe())
```
