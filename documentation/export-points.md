# Exporting SAS Points with Feature Vectors

Export SAS points with feature vectors and (optionally) predicted ligandability scores and pocket assignments.

## Commands

There are two ways to export SAS points:

### `export-points` - standalone export (no model needed)

```bash
prank export-points -f protein.pdb
prank export-points -f protein.pdb -export_points_format parquet
prank export-points dataset.ds     -export_points_format arrow.zst
```

> [!NOTE]
> The `export-points` command calculates SAS surface points with feature vectors and exports them directly: **no model is loaded and no prediction is made**.
> This means the output does **not** contain `score` or `pocket` columns, but you are free to use any custom feature setup via `-features` and `-extra_features` parameters.

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

With `predict`/`rescore`, the output includes a `score` column with predicted ligandability and a `pocket` column with the predicted pocket rank (`0` if the point is not assigned to any pocket).
> [!WARNING]
> Because prediction relies on a pre-trained model that expects a particular set and order of features,
> you **cannot** customize the feature setup (changing `-features` or `-extra_features` would break the model).

### Which command to use?

| | `export-points` | `predict -export_points 1` |
|---|---|---|
| Custom feature setup | Yes | No (must match the model) |
| Predicted `score` and `pocket` columns | No | Yes |
| Requires a model | No | Yes |

## Output

For each protein file, a `{protein_file}_points.{format}` file is generated:

| Column | Description |
|--------|-------------|
| `x`, `y`, `z` | SAS point coordinates |
| `score` | Predicted ligandability [0-1] (`predict`/`rescore` only) |
| `pocket` | Predicted pocket rank (1, 2, …); `0` = point not assigned to any pocket. Integer column, present in `predict`/`rescore` output, absent in standalone `export-points` |
| `feature1`, ... | Feature values based on effective feature setup (`-features`, `-extra_features`) |

Example - `predict -export_points 1` (CSV):
```csv
x,y,z,score,pocket,chem.hydrophobic,chem.aromatic,protrusion,...
12.3456,23.4567,34.5678,0.8234,1,0.5123,-0.2345,15.0000,...
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

> [!TIP]
> **Arrow format** preserves full double precision. Offers faster loading and lower memory usage compared to CSV.
>
> **Parquet format** is a columnar storage format widely supported by data analysis tools (pandas, polars, DuckDB, Spark). Uses SNAPPY compression internally.

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
- CSV format uses up to 7 decimal places for floating-point values; the `pocket` column is written as a plain integer
- Arrow uses IPC streaming format with 64-bit floats; the `pocket` column uses Int32
- Parquet uses SNAPPY compression (not configurable); the `pocket` column uses INT32
- Zstd compression uses level 16 for good compression ratio
- Export is disabled when using `-output_only_stats 1`
- `pocket` matches the `rank` column of `*_predictions.csv`. Boundary points that fall within the extended shells of two pockets (controlled by `extended_pocket_cutoff`) are labeled with the **best** (lowest) rank they belong to.

### Non-finite values

- **`NaN` floats** are written verbatim as the literal token `NaN` in CSV, and as the standard IEEE-754 NaN bit pattern in Arrow / Parquet. pandas / pyarrow / numpy parse this back as NaN without special handling.
- **Infinities** are written as `Infinity` / `-Infinity` in CSV.
- **Non-finite or out-of-range values in INT columns** (e.g. `pocket`) raise an `ArithmeticException` at write time. Features should never produce non-finite values for integer columns; the strict check surfaces such bugs early.

## Example Analysis

**Python (CSV):**
```python
import pandas as pd
df = pd.read_csv('protein_points.csv.gz')
high_score = df[df['score'] > 0.5]
print(df.describe())

# Per-pocket aggregated descriptors (predict / rescore output only):
pocket_descriptors = df[df['pocket'] > 0].groupby('pocket').mean()
```

**Python (Arrow):**
```python
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
```

**Python (Parquet):**
```python
import pandas as pd
df = pd.read_parquet('protein_points.parquet')

# Or with PyArrow directly
import pyarrow.parquet as pq
table = pq.read_table('protein_points.parquet')
df = table.to_pandas()
```

**Python (Polars):**
```python
import polars as pl

# Parquet (fastest)
df = pl.read_parquet('protein_points.parquet')

# CSV with compression
df = pl.read_csv('protein_points.csv.gz')

# Filter high-scoring points
high_score = df.filter(pl.col('score') > 0.5)
```

## See also

- [`export-pocket-grid.md`](export-pocket-grid.md): 3D grid of points
  covering empty space around the protein, tagged by predicted pocket
- [`export-pocket-descriptors.md`](export-pocket-descriptors.md):
  per-pocket geometric descriptors (volume, sphericity, ...)
