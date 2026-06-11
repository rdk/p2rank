# Profiling parallel batch predict runs, and why scale changes the answer

This note covers how to profile **long, multi-threaded** `prank predict` runs (e.g.
`-threads 16` over a whole dataset) and records empirical findings on the
`surface_strategy` x `rf_flatten_target` x JVM choices measured on holo4k in June 2026.

It is the batch/throughput companion to
[`jvm-performance-tuning.md`](../documentation/dev/jvm-performance-tuning.md), which covers
the opposite regime: a single small-protein run, where fixed JVM/startup overhead
dominates.

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

---

## 6. Fine-grained CPU profile of the fastest combination

A JFR `settings=profile` recording of the fastest cell (HotSpot +
`packed_distinct_v4` + `Int16LeafSoaLegacyFlatBinaryForest`) on **full holo4k, 16
threads** (31.5 s app time, 15,019 CPU samples, 8,599 allocation samples). Each sample
is attributed to its leaf-most p2rank/library subsystem.

| subsystem | % CPU | dominant method(s) |
|---|---|---|
| **forest RF scoring** | **57.2 %** | `Int16LeafSoaLegacyFlatBinaryForest.leafIndex` (57 % in this one method) |
| kdtree neighbor query | 14.4 % | `KdTree3D.countWithinRadius / nearestSqrDist / findWithinRadius / quickselect` |
| surface generation | 10.6 % | `Vectorized256WeightedDedupFusedOcclusionScan.collect`, SIMD neighbor list |
| Groovy dynamic | 6.5 % | `compareEqual`, `UnmodifiableMap.get`, boxing, indy fallback |
| PDB parse (BioJava) | 5.2 % | `PDBFileParser`, `AtomImpl.getX` |
| feature extraction | 4.4 % | `PrankFeatureExtractor.calcSasFeatVectorFromAtomVectors` |
| logging/IO | 1.5 % | `ResidueLabelings.fmt` -> `printf` |

CPU efficiency was ~1.0 (16 threads saturated) and GC ~1 % (304 ms / 22 pauses), so
neither parallel scaling nor GC is a lever.

> [!WARNING]
> This corrects the coarse subset bucketing in section 2, which reported "surface
> ~44-56 %". That number merged the kdtree (`cz.siret.prank.geom.kdtree`) into "surface".
> Separated and at true full scale, **RF leaf-traversal is the overwhelming bottleneck
> (57 %)**; surface generation is only ~11 %. Forest share is higher on full holo4k than
> the 200-subset because it scales with SAS-point count, and the full set includes the
> large multi-chain structures the subset under-sampled.

### 6a. The 57 % hotspot: `leafIndex`

Called `numTrees x numSASpoints` times via `predictForBatch`. It is the textbook RF
inference bottleneck: a pointer-chasing loop with a gather
(`instanceAttributes[attributeIndex[node]]`), random-access loads into
`splitPoint`/`childLeft`/`childRight`, and a data-dependent, unpredictable branch, so it
is memory-latency- and branch-misprediction-bound. The descent stays in `double` (only
the leaves are int16-quantized). Batch prediction is already on, and Int16-leaf is
already the fastest faithful variant measured, so the remaining headroom is in the
descent itself. This is dependency-side work tracked in the FasterForest sister repo (see
the optimization brief in `local/dev/`).

### 6b. p2rank-side quick wins (no dependency change, ~4-5 % combined, low risk)

- **`GenericHeader.getColIndex` (~1.2 %)**: resolves a feature column name to an index via
  `Map<String,Integer>.get(name)` per call. Resolve name -> `int` index once, then index
  by int in the per-point loop.
- **`Struct.isHydrogenAtom` (~1.2 %)**: `Element.H == atom.element` and `atom.name[1]=='H'`
  route through Groovy `compareEqual` per atom. Use identity/primitive compares (or Java).
- **`ResidueLabelings.fmt` (~1 %)**: per-residue `printf` formatting for output. Use a
  cheaper formatter and/or only when residue-label files are actually written.
- **`PropertyTable.getValue` / autoboxing** in the feature path: prefer int-indexed lookups
  and primitive arrays over string-keyed maps and boxed `Integer`/`Double`.

### 6c. The 6.5 % "Groovy dynamic" slice in detail

These 977 samples are NOT missing `@CompileStatic` (the hot classes `Box`, `Cutils`,
`Struct`, `Residue` are all `@CompileStatic`). They are constructs that still emit
runtime/indy calls under `@CompileStatic`. By callsite:

| callsite | ~% | construct that defeats `@CompileStatic` | fix |
|---|---|---|---|
| `Struct.isHydrogenAtom` | 1.4 | `Element.H == atom.element` -> `compareEqual` (Groovy `==` is null-safe `.equals`, never identity, even when statically compiled) | `.is()` / primitive / `.equals` |
| `Box.<init>(List<Atom>)` | 1.1 | **per-element `invokedynamic cast` in `for(Atom a : atoms)`** (verified, see below) | indexed `get(i)` loop |
| `Cutils.mapList` | 0.7 | closure invoked dynamically + argument coercion | typed loop / direct dispatch |
| residue-labeling output (`ResidueLabelings.toCSV`, `ModelBasedResidueLabeler.aggregateScore`, `ResidueLabeling.add`) | 0.7 | dynamic `collect`, `<<` (`leftShift`), reflective `add` | cheapen / make conditional (it is an output path) |
| `ModelBasedRescorer.rescorePockets`, `Cutils.sum`, `Residue$Key.equals` | ~0.7 | reflective `CachedMethod.invoke`; `==` in map-key `equals` | static binding; primitive field compares |

Three root constructs, all surviving `@CompileStatic`: (1) `==` on non-primitives ->
`DefaultTypeTransformation.compareEqual`; (2) iteration/coercion of values typed as
`Object` -> `invokedynamic cast` via `CacheableCallSite`; (3) closures into `collect` and
`<<` string building in per-point / per-residue loops.

**Verified `Box.<init>` root cause (javap).** `for (Atom a : atoms)` over a `List<Atom>`
compiles (even under `@CompileStatic`) to an iterator whose `next()` returns `Object`,
followed by a Groovy runtime cast per element, not a JVM `checkcast`:

```
233: invokeinterface java/util/Iterator.next:()Ljava/lang/Object;
238: invokedynamic    #0:cast:(Ljava/lang/Object;)Lorg/biojava/nbio/structure/Atom;   // per atom!
```

(the same `cast` call site also coerces `atoms.first()` at the top of the constructor).
That `invokedynamic cast` is the `CacheableCallSite.getAndPut` seen in the profile, paid
once per atom of every bounding box built. Fix: iterate by index so the element type is
statically `Atom` (`for (int i=0;i<atoms.size();i++) { Atom a = atoms.get(i); ... }`) and
use `atoms.get(0)` instead of `atoms.first()`. Re-disassemble after the change to confirm
the `invokedynamic` is gone.

> [!NOTE]
> Realistic payoff is ~5-6 % if all are fixed, but it is genuinely death-by-a-thousand-cuts
> (no single >1.5 % win). `isHydrogenAtom` and `Box.<init>` are the easy, highest-value
> ones; the residue-labeling cost is largely an output path that may be made conditional.

### 6d. Reproduce the fine-grained profile

```bash
JFR=/tmp/h4k.jfr
JAVA_OPTS="-XX:StartFlightRecording=settings=profile,filename=$JFR,dumponexit=true" \
  ./prank.sh predict holo4k.ds -c config/test-default \
    -surface_strategy packed_distinct_v4 -rf_flatten 1 \
    -rf_flatten_target Int16LeafSoaLegacyFlatBinaryForest \
    -threads 16 -rf_threads 16 -r_threads 16 -cache_datasets 0 -log_to_console 0 -o /tmp/h4k_jfr
# leaf methods:   jfr print --events jdk.ExecutionSample --stack-depth 1 $JFR | ...
# subsystem split: attribute each sample to its leaf-most cz.siret/cz.cuni frame
```
