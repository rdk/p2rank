# Profiling parallel batch predict runs, and why scale changes the answer

This note covers how to profile **long, multi-threaded** `prank predict` runs (e.g.
`-threads 16` over a whole dataset) and records empirical findings on the
`surface_strategy` x `rf_flatten_target` x JVM choices measured on holo4k in June 2026.

It is the batch/throughput companion to
[`jvm-performance-tuning.md`](jvm-performance-tuning.md), which covers the opposite
regime: a single small-protein run, where fixed JVM/startup overhead dominates.

> [!IMPORTANT]
> The headline lesson: **profile rankings are scale dependent**. A single-protein or
> small-subset profile can rank surface strategies, flatten targets and even JVMs in the
> wrong order. Only a full-dataset, multi-threaded, steady-state run gives rankings you
> can trust for production. Concrete reversals are documented below.

---

## 1. Use the right tool for parallel runs

`misc/test-scripts/predict_breakdown.sh` (and `predict_bench.sh --phases`) decompose a
run into phases by differencing per-line log timestamps. That works **only at 1 thread**.
At `-threads N > 1` it is invalid:

- **Interleaving**: N proteins process concurrently, so the
  `loading protein -> structure atoms -> SAS points` milestones from different workers
  interleave in the log and no longer form a parseable per-protein sequence.
- **Wall gaps are not phase cost**: the time between two log lines includes work the
  other threads did on other proteins. Under thread oversubscription, log timestamps
  measure contention, not phase duration.

For parallel runs use `misc/test-scripts/predict_profile_mt.sh` instead. It measures what
actually matters for a batch:

| metric | how | why |
|---|---|---|
| throughput | proteins/s from the app-internal "predicting pockets finished in N" time (excludes JVM boot), mean +/- sd over fresh-JVM reps | the real production number |
| CPU efficiency | `(user+sys CPU) / (wall * threads)` | < 1 reveals a serial bottleneck / contention / GC stalls; > 1 means background JIT/GC threads use spare cores |
| GC | total pause ms + count from `-Xlog:gc` | catch GC as a serial bottleneck |
| phase CPU | one JFR rep (`settings=profile`); every `ExecutionSample` across **all** threads attributed to the leaf-most p2rank/lib subsystem | sampling aggregates over threads, so it is concurrency correct (unlike log-gap timing) |

Example (one cell):

```bash
JAVA_HOME=.../25.0.3-oracle misc/test-scripts/predict_profile_mt.sh holo4k.ds \
  --threads 16 --reps 3 --jfr --no-warmup -- \
  -surface_strategy packed_distinct_v4 -rf_flatten 1 \
  -rf_flatten_target Int16LeafSoaLegacyFlatBinaryForest
```

> [!TIP]
> On a large dataset, warm the OS file cache once (a bulk read of all structures) and pass
> `--no-warmup`, rather than paying a full warmup run per cell. Switch JVMs by pointing
> `JAVA_HOME` at a different SDKMAN candidate; the script reports the JRE it used.

---

## 2. Where the CPU actually goes (16 threads, steady state)

JFR phase attribution on a 200-protein holo4k subset at 16 threads, both JVMs:

| subsystem | share of app CPU |
|---|---|
| surface (SAS generation) | 44 - 62 % |
| forest (RF point scoring) | 28 - 39 % |
| PDB/CIF parse (BioJava) | 7 - 14 % |
| feature extraction | 3 - 5 % |

So **surface generation plus forest scoring are ~85-90 % of app CPU**, which is exactly
what `surface_strategy` and `rf_flatten_target` control.

> [!WARNING]
> This inverts the single-thread log-milestone picture, which attributes the largest wall
> share to "feature extraction". That phase window in the serial breakdown lumps in work
> the CPU profiler correctly assigns to surface/forest. Trust the sampling profiler for
> parallel runs.

---

## 3. Findings on the full holo4k dataset (4009 proteins, 16 threads)

Measured on a 32-core box, p2rank 2.6-alpha.5, FasterForest 2.12.0,
faster-molecular-surface 1.8, Java 25.0.3 (Oracle GraalVM with libgraal, and Oracle
HotSpot). App-internal time, mean of 3 reps (sd <= 0.6 s):

| JVM | surface | flatten | app time (s) | proteins/s |
|---|---|---|---|---|
| HotSpot | packed_distinct_v4 | Int16LeafSoaLegacy | **32.0** | **125** |
| GraalVM | packed_distinct_v4 | Int16LeafSoaLegacy | 34.4 | 117 |
| HotSpot | faster | Int16LeafSoaLegacy | 43.9 | 91 |
| GraalVM | faster | Int16LeafSoaLegacy | 45.6 | 88 |

### 3a. `packed_distinct_v4` is the best surface strategy on both JVMs

At full scale v4 beats the legacy `faster` engine by ~25-27 % on both JVMs (it produces
the de-duplicated distinct SAS set directly, with no coincident-duplicate points and no
sparsification pass). This confirms the shipped default.

### 3b. `Int16LeafSoaLegacyFlatBinaryForest` is the best flatten target

Across both JVMs and both surfaces (measured on the 200-protein subset over all three
faithful targets), the int16-quantized SoA legacy forest is fastest, and it visibly cuts
forest-scoring CPU (e.g. GraalVM + v4: RF share 36 % with `LegacyFlatBinaryForest` drops
to 28 % with the int16 target). Plain `SoaLegacyFlatBinaryForest` is about equal to
`LegacyFlatBinaryForest`: the SoA memory layout alone buys little, the int16 leaf
quantization (smaller footprint, better cache behaviour) is what helps. It is faithful
(ranking-equivalent to legacy) and safe with the default `pred_point_threshold`.

### 3c. HotSpot edges GraalVM, by a small margin at scale

HotSpot (C2) is faster than GraalVM (libgraal) on every cell, but the margin is small at
production scale: ~7 % (v4) and ~4 % (faster). GraalVM also runs at CPU efficiency
1.07-1.09 versus HotSpot's 1.00-1.01, i.e. the Graal compiler does ~15 % more compile
work on the spare cores; with cores to spare that is nearly free in wall time, which is
why GraalVM stays close but cannot get ahead.

> [!NOTE]
> `-XX:+EagerJVMCI` is **not** a tuning lever here. On Oracle GraalVM 25 it is already
> default-on (ergonomic) and the compiler is libgraal (`UseJVMCINativeLibrary=true`), so
> the explicit flag in `local-env.sh` is redundant. Toggling it
> (`PRANK_EAGER_JVMCI=0 ./prank.sh ...`) changes throughput by less than the run-to-run
> noise. Note also that running on the GraalVM JDK uses the Graal compiler as a **JIT**,
> not Native Image: the application is still compiled at runtime, only the compiler itself
> is precompiled (libgraal).

---

## 4. Scale dependence: two rankings that reversed

The same comparison on a 200-protein subset gave **different and partly wrong** rankings.
Comparing app time (Int16 cells), subset vs full:

| | subset (Graal vs HotSpot) | full (Graal vs HotSpot) |
|---|---|---|
| v4 | Graal +30 % slower | Graal +7 % slower |
| faster | Graal +17 % slower | Graal +4 % slower |

| | subset (v4 vs faster) | full (v4 vs faster) |
|---|---|---|
| HotSpot | v4 -4 % (v4 faster) | v4 -27 % (v4 faster) |
| GraalVM | v4 **+6 % (v4 SLOWER)** | v4 -24 % (v4 faster) |

Two artifacts of small scale:

1. **The GraalVM "JVM gap" was inflated.** At 200 proteins GraalVM looked 17-30 % slower;
   at 4009 it is only 4-7 % slower. Graal's heavier compile cost amortizes over ~20x more
   iterations, so most of the apparent gap was unpaid-back JIT warmup.
2. **A spurious surface "flip".** On the subset, `faster` appeared to beat `v4` on
   GraalVM (v4 +6 % slower): v4's SIMD Vector-API surface code had not gone fully hot in
   200 proteins on Graal. At full scale v4 wins by ~24 % on Graal too. There is no
   per-JVM surface preference; v4 is best everywhere.

> [!IMPORTANT]
> When comparing surface strategies, flatten targets or JVMs, run a **large** dataset
> (ideally full holo4k) past JIT warmup. Subset and single-protein numbers are fine for
> attribution (where does the time go) but unreliable for ranking close alternatives.

---

## 5. Reproduce

```bash
# build first (launchers use the distro jar, not freshly compiled test classes)
./gradlew assemble

# full-dataset cell, HotSpot
sdk use java 25.0.3-oracle
misc/test-scripts/predict_profile_mt.sh holo4k.ds --threads 16 --reps 3 --no-warmup -- \
  -surface_strategy packed_distinct_v4 -rf_flatten 1 \
  -rf_flatten_target Int16LeafSoaLegacyFlatBinaryForest

# same on GraalVM for the JVM contrast
sdk use java 25.0.3-graal
# ... repeat
```

The `quick_compare` / `quick_compare_distro` routines in
`misc/test-scripts/testsets.sh` run the full surface x flatten matrix end to end via the
ordinary launchers (wall-time only, no phase attribution).
