# Exporting SAS Points with Feature Vectors

Export SAS points with feature vectors and (optionally) predicted ligandability scores.

## Commands

There are two ways to export SAS points:

### `export-points` - standalone export (no model needed)

```bash
prank export-points -f protein.pdb
prank export-points -f protein.pdb -export_points_format parquet
prank export-points dataset.ds     -export_points_format arrow.zst
```

The `export-points` command calculates SAS surface points with feature vectors and exports them directly - **no model is loaded and no prediction is made**.
This means the output does **not** contain a `score` column, but you are free to use any custom feature setup via `-features` and `-extra_features` parameters.

### `predict` / `rescore` - export alongside prediction

```bash
prank predict -f protein.pdb -export_points 1
prank predict -f protein.pdb -export_points 1 -export_points_format csv.gz
prank predict -f protein.pdb -export_points 1 -export_points_format arrow
prank predict -f protein.pdb -export_points 1 -export_points_format parquet
prank predict dataset.ds     -export_points 1 -export_points_format arrow.zst
```

The `rescore` command also supports export (pocket points only):
```bash
prank rescore joined-fpocket.ds -export_points 1 -export_points_format arrow.zst
```

With `predict`/`rescore`, the output includes a `score` column with predicted ligandability.
However, because prediction relies on a pre-trained model that expects a particular set and order of features,
you **cannot** customize the feature setup (changing `-features` or `-extra_features` would break the model).

### Which command to use?

| | `export-points` | `predict -export_points 1` |
|---|---|---|
| Custom feature setup | Yes | No (must match the model) |
| Predicted `score` column | No | Yes |
| Requires a model | No | Yes |

## Output

For each protein file, a `{protein_file}_points.{format}` file is generated:

| Column | Description |
|--------|-------------|
| `x`, `y`, `z` | SAS point coordinates |
| `score` | Predicted ligandability [0-1] (`predict`/`rescore` only) |
| `feature1`, ... | Feature values based on effective feature setup (`-features`, `-extra_features`) |

Example - `predict -export_points 1` (CSV):
```csv
x,y,z,score,chem.hydrophobic,chem.aromatic,protrusion,...
12.3456,23.4567,34.5678,0.8234,0.5123,-0.2345,15.0000,...
```

Example - `export-points` (CSV):
```csv
x,y,z,chem.hydrophobic,chem.aromatic,protrusion,...
12.3456,23.4567,34.5678,0.5123,-0.2345,15.0000,...
```

## Parameters

| Parameter | Default | Values |
|-----------|---------|--------|
| `export_points` | `false` | `true` / `false` |
| `export_points_format` | `csv` | `csv`, `csv.gz`, `csv.zst`, `arrow`, `arrow.gz`, `arrow.zst`, `parquet` |

**Arrow format** preserves full double precision. Offers faster loading and lower memory usage compared to CSV.

**Parquet format** is a columnar storage format widely supported by data analysis tools (pandas, polars, DuckDB, Spark). Uses SNAPPY compression internally.

## Format Recommendations

| Use Case | Recommended Format |
|----------|-------------------|
| Smallest file size | `csv.zst` or `arrow.zst` |
| Python/R analysis | `parquet` or `csv.gz` |
| Streaming/pipes | `arrow` (uncompressed) |
| Maximum compatibility | `csv` |

## Notes

- `export-points` and `predict` export all SAS points; `rescore` exports only pocket points
- `export-points` does not require `-export_points 1` - exporting is always on
- CSV format uses 7 decimal places for all numeric values
- Arrow uses IPC streaming format with 64-bit floats
- Parquet uses SNAPPY compression (not configurable)
- Zstd compression uses level 16 for good compression ratio
- Export is disabled when using `-output_only_stats 1`

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

**Python (Parquet):**
~~~python
import pandas as pd
df = pd.read_parquet('protein_points.parquet')

# Or with PyArrow directly
import pyarrow.parquet as pq
table = pq.read_table('protein_points.parquet')
df = table.to_pandas()
~~~

**Python (Polars):**
~~~python
import polars as pl

# Parquet (fastest)
df = pl.read_parquet('protein_points.parquet')

# CSV with compression
df = pl.read_csv('protein_points.csv.gz')

# Filter high-scoring points
high_score = df.filter(pl.col('score') > 0.5)
~~~
