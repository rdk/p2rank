# Test and benchmark scripts

Helper scripts for running p2rank test suites and performance benchmarks from a
development checkout.

> [!IMPORTANT]
> Run these from the repo root, and build the distro first: most scripts use
> `./prank.sh` or `distro/prank*`, which run `distro/bin/p2rank.jar`.
> ```bash
> ./gradlew assemble
> ```

## Scripts

| script | what it does |
|---|---|
| `testsets.sh` | Named test routines (predict, eval, conservation, surfaces, ...). Run one or more sequentially, with optional params forwarded to every p2rank call (see [below](#testsetssh)). |
| `surface_bench_jres.sh` | Benchmark `analyze surface-strategies` across JVMs x threading modes (see below). |
| `predict_bench_jres.sh` | Compare `prank predict` wall time across chosen JREs, and verify predictions are identical across them. |
| `predict_bench_matrix.sh` | Predict benchmark over a matrix of configurations. |
| `predict_bench.sh` | Single-config predict timing. |
| `pocket_grid_features_bench.sh`, `pocket_grid_dataset_bench.sh` | Pocket-grid feature / dataset benchmarks. |
| `kdtree-benchmark.sh` | KD-tree micro-benchmark. |
| `eval_new_features.sh`, `eval_session_features.sh` | Feature-evaluation runs. |
| `standard-benchmarks.sh`, `benchmark.sh` | Higher-level benchmark drivers. |
| `bench-common.sh` | Shared helpers sourced by the `*_bench_jres.sh` scripts (JVM resolution, labels, usage). Not run directly. |

## testsets.sh

Runs named test routines, each a bash function in the script (`quick`, `basic`,
`predict`, `conservation`, `surface_strategies`, ...).

```bash
./misc/test-scripts/testsets.sh <routine> [args...] [<routine> [args...]] ... [p2rank params...]
```

(`./tests.sh` at the repo root is a thin wrapper that forwards to this script.)

- **Multiple routines** run sequentially, in the order listed:
  ```bash
  ./tests.sh quick basic predict
  ```
- **Extra p2rank params** are appended to every p2rank invocation, overriding the
  per-command defaults (p2rank takes the last value). The routine list ends at the
  first `-`-prefixed token, so no separator is needed:
  ```bash
  ./tests.sh quick basic -threads 8 -fail_fast 0
  ```
  They may also come from the `EXTRA` env var, which composes with the CLI ones:
  ```bash
  EXTRA="-log_cases 1" ./tests.sh quick -threads 4
  ```
- A few routines take **positional args** right after the routine name (an optional
  dataset list, or `config label`):
  ```bash
  ./tests.sh surface_strategies coach420.ds holo4k.ds -threads 16
  ./tests.sh traineval_config config/test-default MyLabel
  ```

> [!NOTE]
> The routine list is split from the p2rank params at the first token starting
> with `-`. So a routine's positional arg must not start with `-` (it would be
> read as a param) and must not coincide with a routine name (it would start a new
> routine). Dataset names (`*.ds`) and `config/...` paths are always safe; only
> free-form labels need care. An explicit `--` may terminate the routine list but
> is normally unnecessary.

## surface_bench_jres.sh

Benchmarks `prank analyze surface-strategies` (all surface strategies: `cdk`,
`faster`, `packed`, `faster_distinct`, `packed_distinct`, `packed_distinct_v2`,
`packed_distinct_v3`, `float_distinct`) on one dataset, under every `(JVM x threads)` combination, and
writes a combined `summary.csv` (wall time, per-surface time, point counts,
sparsification reduction, throughput) plus the per-run CSVs and logs.

It works on both HotSpot and GraalVM: it pre-sets
`JAVA_OPTS=-XX:+UnlockExperimentalVMOptions` so `local-env.sh`'s GraalVM-oriented
flags are accepted on stock HotSpot too (the compiler is unchanged: HotSpot stays
C2, GraalVM stays Graal).

### Example

```bash
cd /path/to/p2rank          # repo root; run `./gradlew assemble` first

./misc/test-scripts/surface_bench_jres.sh \
  -d fptrain.ds \                                  # dataset: a .ds name (resolved via the config's
                                                   #   dataset_base_dir) or a path to a .ds / structure file
  -j "25.0.2-oracle 25.0.2-graal 26.0.1-oracle 26.ea.13-graal" \
                                                   # jvms: space/comma list; each an SDKMAN candidate
                                                   #   name or a JAVA_HOME path. Here: C2 vs Graal on JDK 25 and 26
  -t "1 16" \                                      # thread modes to test (default "1 16")
  -T 4 \                                           # tessellation level (optional; default: from --config)
  -c config/test-default \                         # p2rank config (optional; default config/test-default,
                                                   #   which sets dataset_base_dir + solvent_radius + tessellation)
  -o /tmp/surface_bench_fptrain                    # output dir (optional; default: a fresh /tmp/surface_bench_jres.XXXX)
```

The run above is 4 JVMs x 2 thread-modes = 8 runs, each benchmarking all 7
strategies on fptrain (222 proteins).

> [!TIP]
> Minimal form (defaults: threads "1 16", tessellation from config, fresh /tmp output dir):
> ```bash
> ./misc/test-scripts/surface_bench_jres.sh -d fptrain.ds -j "25.0.2-graal 26.0.1-oracle"
> ```
> List installable/installed SDKMAN JVMs with `--list`; full options with `--help`.

> [!NOTE]
> Run on an idle machine. The relative ranking across JVMs/threads is the reliable
> output; absolute numbers drift with load.
