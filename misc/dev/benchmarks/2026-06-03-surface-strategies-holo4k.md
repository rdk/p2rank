# Surface Strategy Benchmark (holo4k)

> [!NOTE]
> Intermediate / scratch benchmark, not a landmark reference. Captured during
> development of the distinct-point surfaces; numbers and conclusions may be
> superseded by later runs. Lives under `misc/dev/benchmarks/` (scratch), not the
> curated `documentation/dev/`.

Benchmark and equivalence verification of all p2rank solvent-accessible-surface
(SAS) generation strategies, run on the full holo4k dataset. Covers the two new
surfaces (`packed_distinct_v2`, `float_distinct`) against the previous best
(`packed_distinct`) and the production baselines (`cdk`, `faster`, `packed`).

- Date: 2026-06-03
- Command: `prank analyze surface-strategies` (see [Reproduction](#reproduction))
- Library: `faster-molecular-surface` 1.5

## TL;DR

- `packed_distinct_v2` is the new champion for exact surfaces: bit-exact to
  `packed_distinct`, never slower, and ~18% faster at tessellation level 4.
- At p2rank's production tessellation (level 2) the three distinct-point
  strategies are effectively tied: the V2/float SIMD-verdict optimization
  targets the level-4 scan hot spot, which barely exists at level 2.
- `float_distinct` (single-precision occlusion verdict) adds only ~1% over V2
  at level 4 and nothing at level 2, while giving up bit-exactness. Not worth
  the approximation for general use.
- All distinct-point strategies produce ~5.7x fewer points than the standard
  surfaces and need no downstream sparsification, yet remain area-exact (V2,
  packed_distinct, faster_distinct) or area-approximate within ~1.4e-5
  (float_distinct).

## Environment

| | |
|---|---|
| CPU | AMD Ryzen 9 9950X, 16 cores / 32 threads |
| RAM | 182 GiB |
| JVM | Oracle GraalVM 25.0.2+10.1 (build 25.0.2+10-LTS-jvmci-b01) |
| Top-tier JIT | Graal (JVMCI), default on this build, eagerly initialized (`-XX:+EagerJVMCI`) |
| Vector API | `jdk.incubator.vector` enabled (256-bit lanes used by the SIMD scans) |
| Heap / GC | `-Xmx128G`, Parallel GC, compact object headers |
| Threads | 16 (`-threads 16`, matches physical core count) |
| Dataset | holo4k: 4009 proteins, 15,671,915 protein atoms |
| Solvent radius | 1.6 |

> [!IMPORTANT]
> This is a GraalVM result. The SIMD occlusion scans use the Vector API, and
> Graal's vectorized-intrinsic codegen differs from HotSpot C2. The relative
> ordering should hold, but absolute throughput and the size of the V2/float win
> can differ on a stock HotSpot JDK.

## Strategies under test

| id | class | exact? | needs sparsification? | notes |
|---|---|---|---|---|
| `cdk` | PatchedCdkNumericalSurface | yes | yes | CDK NumericalSurface + metal vdW fallback |
| `faster` | FasterNumericalSurface | yes | yes | current production default |
| `packed` | PackedNumericalSurface | yes (bit-exact to faster) | yes | flat store + zero-copy point delivery |
| `faster_distinct` | DistinctFasterNumericalSurface | yes (area) | no | faster pipeline, one point per distinct direction |
| `packed_distinct` | DistinctPackedNumericalSurface | yes (area) | no | packed engine, distinct directions (previous best) |
| `packed_distinct_v2` | DistinctPackedNumericalSurfaceV2 | yes (area, bit-exact to packed_distinct) | no | SIMD weighted dedup + right-sized store |
| `float_distinct` | FloatNumericalSurface | approximate (~1.4e-5) | no | V2 pipeline, single-precision occlusion verdict (8 SIMD lanes) |

The standard surfaces (`cdk`, `faster`, `packed`) emit the full icosahedral
tessellation, where each direction repeats with multiplicity ~5.71 (shared
triangle vertices). p2rank then removes those coincident duplicates with a
0.05 A greedy sparsification pass. The distinct-point surfaces emit one point
per surviving distinct direction directly (weighting the area by the
multiplicity), so they produce the post-sparsification point set with no
sparsification step.

## Methodology

`analyze surface-strategies` does the following, per dataset:

1. Preload all proteins to CDK containers once (structure I/O excluded from timing).
2. Warm-up: an untimed parallel pass (up to 500 proteins) per strategy, so the
   JIT is fully compiled and process caches are populated before measuring.
3. Measured pass per strategy: build the surface for every protein at `-threads`
   parallelism, timing surface generation and the subsequent sparsification per
   protein. Reports wall time, per-surface time distribution, point counts, and
   throughput.
4. Equality pass: per protein, compare every strategy's points against a
   reference (`faster`) in atom-major order: exact (binary) and within epsilon.

Column meanings:

- `wall_s`: total wall-clock for the measured pass over all 4009 proteins.
- `surf_*_ms`: per-protein surface-generation time (mean / median / p95 / max).
- `sparsify_ms`: per-protein sparsification time (the harness times it on every
  strategy for comparison, even though the distinct strategies skip it in real runs).
- `avg_points`: mean raw surface points per protein (before sparsification).
- `avg_sparse_points`: mean points after sparsification.
- `reduce_%`: sparsification reduction (`1 - sparse/raw`).
- `Matoms_per_s`: throughput, total atoms / wall_s.

> [!NOTE]
> `sparsify_ms` is included in neither a fair "production" total for the distinct
> strategies (they skip it) nor excluded from the standard ones (they require it).
> To compare end-to-end production cost, add `sparsify_ms` to the standard rows
> and ignore it on the distinct rows.

## Results: tessellation level 2 (production default)

### Benchmark

| strategy | wall_s | surf_mean | surf_med | surf_p95 | surf_max | sparsify_ms | avg_pts | avg_sparse | reduce_% | Matoms/s |
|---|---|---|---|---|---|---|---|---|---|---|
| cdk | 23.113 | 88.02 | 64.42 | 232.05 | 624.57 | 3.97 | 37824 | 6619 | 82.5 | 0.678 |
| faster | 17.311 | 65.18 | 47.05 | 174.36 | 424.39 | 3.77 | 37824 | 6619 | 82.5 | 0.905 |
| packed | 4.320 | 13.41 | 9.96 | 35.39 | 83.14 | 3.79 | 37824 | 6619 | 82.5 | 3.627 |
| faster_distinct | 7.843 | 29.71 | 21.30 | 79.49 | 224.46 | 1.50 | 6619 | 6619 | 0.0 | 1.998 |
| packed_distinct | 1.884 | 6.11 | 4.44 | 16.05 | 52.88 | 1.39 | 6619 | 6619 | 0.0 | 8.319 |
| **packed_distinct_v2** | **1.838** | 5.94 | 4.41 | 15.60 | 32.99 | 1.38 | 6619 | 6619 | 0.0 | **8.526** |
| float_distinct | 1.863 | 6.01 | 4.33 | 15.97 | 44.40 | 1.40 | 6619 | 6619 | 0.0 | 8.411 |

### Equality vs `faster` (epsilon = 1e-6 A)

| strategy | compared | binary_equal | within_eps | count_mismatch | max_abs_diff_A |
|---|---|---|---|---|---|
| cdk | 4009 | 4009 | 4009 | 0 | 0.0 |
| packed | 4009 | 4009 | 4009 | 0 | 0.0 |
| faster_distinct | 4009 | 0 | 0 | 4009 | 0.0 |
| packed_distinct | 4009 | 0 | 0 | 4009 | 0.0 |
| packed_distinct_v2 | 4009 | 0 | 0 | 4009 | 0.0 |
| float_distinct | 4009 | 0 | 0 | 4009 | 0.0 |

## Results: tessellation level 4

### Benchmark

| strategy | wall_s | surf_mean | surf_med | surf_p95 | surf_max | sparsify_ms | avg_pts | avg_sparse | reduce_% | Matoms/s |
|---|---|---|---|---|---|---|---|---|---|---|
| cdk | 205.756 | 742.44 | 549.61 | 1961.1 | 4500.9 | 75.74 | 605236 | 101158 | 83.3 | 0.076 |
| faster | 178.228 | 635.49 | 471.69 | 1673.4 | 3838.8 | 73.45 | 605236 | 101158 | 83.3 | 0.088 |
| packed | 37.289 | 52.32 | 39.41 | 132.75 | 255.95 | 96.03 | 605236 | 101158 | 83.3 | 0.420 |
| faster_distinct | 36.748 | 111.38 | 81.67 | 296.33 | 633.17 | 34.59 | 101188 | 101158 | 0.0 | 0.426 |
| packed_distinct | 18.585 | 19.10 | 13.97 | 49.46 | 130.94 | 54.85 | 101188 | 101158 | 0.0 | 0.843 |
| **packed_distinct_v2** | 15.782 | 17.02 | 12.65 | 43.66 | 91.19 | 45.75 | 101188 | 101158 | 0.0 | 0.993 |
| **float_distinct** | **15.580** | 16.83 | 12.58 | 43.06 | 87.32 | 45.16 | 101188 | 101158 | 0.0 | **1.006** |

### Equality vs `faster` (epsilon = 1e-6 A)

| strategy | compared | binary_equal | within_eps | count_mismatch | max_abs_diff_A |
|---|---|---|---|---|---|
| cdk | 4009 | 4009 | 4009 | 0 | 0.0 |
| packed | 4009 | 4009 | 4009 | 0 | 0.0 |
| faster_distinct | 4009 | 0 | 0 | 4009 | 0.0 |
| packed_distinct | 4009 | 0 | 0 | 4009 | 0.0 |
| packed_distinct_v2 | 4009 | 0 | 0 | 4009 | 0.0 |
| float_distinct | 4009 | 0 | 0 | 4009 | 0.0 |

## Analysis

### Speedup vs the production default (`faster`), by wall time

| strategy | tess=2 | tess=4 |
|---|---|---|
| packed | 4.0x | 4.8x |
| faster_distinct | 2.2x | 4.9x |
| packed_distinct | 9.2x | 9.6x |
| packed_distinct_v2 | 9.4x | 11.3x |
| float_distinct | 9.3x | 11.4x |

### packed_distinct_v2 vs packed_distinct (the previous best)

| | tess=2 | tess=4 |
|---|---|---|
| wall_s | 1.884 -> 1.838 (2.5% faster) | 18.585 -> 15.782 (17.8% faster) |
| Matoms/s | 8.319 -> 8.526 (+2.5%) | 0.843 -> 0.993 (+17.8%) |
| point set | bit-exact (same 6619) | bit-exact (same 101188) |

V2's win scales with tessellation level because its SIMD weighted-dedup scan
attacks the occlusion test, which dominates CPU only when there are many
tessellation directions (level 4 has ~5.7x more raw points than level 2). At
level 2 the per-protein work is too small for the scan to dominate, so V2 and
`packed_distinct` are within noise.

### float_distinct

The single-precision verdict (8 SIMD lanes instead of 4) buys only ~1% over V2
at level 4 (15.580 vs 15.782 s) and nothing at level 2. Point positions and
areas are still computed in double, but a few tessellation points within float
epsilon of the occlusion boundary flip survival, so the surface is approximate
(area within ~1.4e-5 relative, a few points out of ~101k differ). The marginal
speed gain does not justify the loss of bit-exactness for general use.

### Point reduction and sparsification

The distinct strategies emit ~5.71x fewer points at level 2 (37824 -> 6619) and
~5.98x fewer at level 4 (605236 -> 101188): exactly the post-sparsification
count of the standard surfaces (82.5% / 83.3% reduction). They produce that set
directly with no sparsification pass. End-to-end (compute + sparsify), the
distinct advantage over the standard strategies is therefore even larger than
the `wall_s` column alone (which here also times an unnecessary sparsify pass on
the distinct rows for comparison).

### Equality interpretation

`cdk` and `packed` are bit-identical to `faster` across all 4009 proteins
(`binary_equal = 4009`, `max_abs_diff = 0`). The distinct strategies show
`count_mismatch = 4009` by design: they carry ~5.7x fewer points, so the
comparison short-circuits on count (hence `max_abs_diff = 0`, not a coordinate
drift). Per-atom point-set equivalence of the distinct surfaces (distinct ==
de-duplicated original, both directions) and V2's bit-exactness to
`packed_distinct`, plus float's tolerance, are verified by the library's own
test suite, not by this dataset-level harness.

### Parallel scaling

At level 2, `par_speedup` (sum of per-surface time / wall time) is ~15.1-15.3x
for the slow strategies (cdk, faster: surface compute dominates, near-linear on
16 threads) and ~12.9-13.0x for the fast distinct strategies (per-protein work
is small enough that fixed overhead and memory bandwidth cap scaling). At level
4 the heavier per-surface work shifts the picture; `packed` shows lower
`par_speedup` (5.6x) because its sparsify cost grows with the larger raw point
count and is comparatively memory-bound.

## Conclusions

1. Adopt `packed_distinct_v2` as the preferred exact surface strategy. It is
   bit-exact to `packed_distinct`, never slower, and meaningfully faster at high
   tessellation. There is no scenario where it loses to `packed_distinct`.
2. At p2rank's production tessellation (level 2), `packed_distinct` and
   `packed_distinct_v2` are equivalent in speed. Either is ~9x faster than the
   `faster` default while producing the identical (sparsified) point set with no
   sparsification step.
3. Keep `float_distinct` available for throughput-critical, accuracy-tolerant
   batch jobs at high tessellation, but do not default to it: the ~1% gain does
   not justify approximation, and there is no gain at level 2.
4. The standard strategies (`cdk`, `faster`, `packed`) remain the bit-exact
   references; `packed` should be preferred over `faster`/`cdk` whenever the full
   (non-distinct) point set is required, being ~4x faster and bit-identical.

## Reproduction

```bash
# from the p2rank repo root, on GraalVM 25 (full Graal JIT via ./prank.sh + local-env.sh)
JAVA_HOME=~/.sdkman/candidates/java/25.0.2-graal \
  ./prank.sh analyze surface-strategies <datasets>/holo4k.ds \
  -threads 16 -tessellation 2 -o /tmp/surf_strat_tess2

JAVA_HOME=~/.sdkman/candidates/java/25.0.2-graal \
  ./prank.sh analyze surface-strategies <datasets>/holo4k.ds \
  -threads 16 -tessellation 4 -o /tmp/surf_strat_tess4
```

Outputs: `surface_strategies.csv` (benchmark) and `surface_equality.csv`
(equivalence) in the `-o` directory.

The `testsets.sh` helpers run this across the major datasets:

- `surface_strategies` -- benchmark + equality on chen11, fptrain, coach420, joined, holo4k
- `surface_density` -- per-strategy point-density / redundancy stats on the same datasets
- `surfaces` -- both of the above
- `surfaces_fast` -- both, on fptrain only (quick ~30s smoke check)
