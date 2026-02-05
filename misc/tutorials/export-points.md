# Exporting SAS Points with Feature Vectors

Export SAS points with feature vectors and ligandability scores.

## Usage

```bash
prank predict -f protein.pdb -export_points 1
prank predict -f protein.pdb -export_points 1 -export_points_format csv.gz
prank predict -f protein.pdb -export_points 1 -export_points_format arrow
prank predict dataset.ds     -export_points 1 -export_points_format arrow.zst
```

The `rescore` command also supports export (pocket points only):
```bash
prank rescore joined-fpocket.ds -export_points 1 -export_points_format arrow.zst
```

## Output

For each protein file, a `{protein_file}_points.{format}` file is generated:

| Column | Description                                                                                          |
|--------|------------------------------------------------------------------------------------------------------|
| `x`, `y`, `z` | SAS point coordinates                                                                                |
| `score` | Predicted ligandability [0-1]                                                                        |
| `feature1`, ... | Feature values calculated by P2Rank based on effective feature setup (`-features`,`-extra_features`) |

Example (CSV):
```csv
x,y,z,score,chem.hydrophobic,chem.aromatic,protrusion,...
12.3456,23.4567,34.5678,0.8234,0.5123,-0.2345,15.0000,...
```

## Parameters

| Parameter | Default | Values |
|-----------|---------|--------|
| `export_points` | `false` | `true` / `false` |
| `export_points_format` | `csv` | `csv`, `csv.gz`, `csv.zst`, `arrow`, `arrow.gz`, `arrow.zst` |

**Arrow format** preserves precision. Theoretically it offers faster loading and lower memory usage compared to CSV. 

## Notes

- `predict` exports all SAS points; `rescore` exports only pocket points
- CSV format uses 7 decimal places for all numeric values
- Arrow uses IPC streaming format with 64-bit floats, allowing direct reading from compressed streams
- Export is disabled when using `-output_only_stats 1`
- `.csv.gz` files are often smaller than `.arrow.gz` files in practice

## Example Analysis

**Python (CSV):**
~~~python
import pandas as pd
df = pd.read_csv('protein_points.csv.gz')
high_score = df[df['score'] > 0.5]
print(df.describe())
~~~

**Python (Arrow):**
~~~python
import pyarrow as pa

# Uncompressed
df = pa.ipc.open_stream('protein_points.arrow').read_pandas()

# Gzip-compressed - streaming format allows direct reading
import gzip
with gzip.open('protein_points.arrow.gz', 'rb') as f:
    df = pa.ipc.open_stream(f).read_pandas()

# Zstd-compressed
import zstandard as zstd
with open('protein_points.arrow.zst', 'rb') as f:
    df = pa.ipc.open_stream(zstd.ZstdDecompressor().stream_reader(f)).read_pandas()
~~~
