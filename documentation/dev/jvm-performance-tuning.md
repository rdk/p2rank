# Optimizing p2rank performance on the JVM

A practical tutorial for making `prank predict` start and run faster by tuning the
JVM. It is aimed at developers and power users who run p2rank a lot (single proteins
interactively, or batches of many structures).

The headline finding: for typical prediction runs, **most of the wall-clock time is
JVM overhead, not p2rank compute**. So the biggest wins come from JVM startup tuning,
not from changing p2rank code.

> [!TIP]
> If you just want the fast launcher and not the theory, jump to
> [The ready-made launcher: `prank_burst`](#3-the-ready-made-launcher-prank_burst).

---

## 1. Where the time actually goes

A single `prank predict -f some.pdb` run on a small protein spends roughly:

| phase | share | depends on protein size? |
|---|---|---|
| JVM + Groovy runtime startup | ~25% | no |
| Config parse (`default.groovy` via GroovyShell) | ~20% | no |
| RF model deserialization | ~25% | no |
| First-protein work incl. JIT warmup | ~25% | a little |
| **Actual pocket-prediction compute** | **~5%** | yes |

> [!NOTE]
> These shares are reference figures from one machine and shift with hardware, protein
> size and JDK. Re-measure the phase timings on your box with
> `misc/test-scripts/predict_bench.sh --phases` (it prints per-line wall-clock timestamps;
> derive the shares by differencing them). The exact percentages matter less than
> the takeaway: compute is a small slice, fixed overhead dominates.

In other words a small-protein run is **~95% fixed overhead**. That overhead is paid
**once per JVM process**, so the single most effective optimization is not a flag at
all:

> [!IMPORTANT]
> **Process many proteins in one JVM.** Pass a dataset file (`prank predict proteins.ds`)
> instead of launching `prank predict -f` once per protein. The ~1.3s fixed cost is then
> amortized across the whole dataset (measured ~18-23x faster per protein on a batch).
> Dataset paths are absolute or relative to the `.ds` file, not the working directory.
> Within that one JVM, `-threads N` controls how many proteins are processed in parallel
> (default `nCPU+1`); the batch is processed by a fixed thread pool of that size.

Everything below optimizes the *fixed overhead* and the *per-protein compute* for cases
where you cannot batch (interactive single-file runs) or want batches to finish sooner.

For the compute itself, profiling (JFR) shows the hot spots are, in order: SAS surface
generation, random-forest classification, and k-d-tree spatial queries. Those are p2rank
code, out of scope for this JVM tutorial, but see `misc/test-scripts/predict_bench.sh --profile`.

---

## 2. The four JVM levers

All measurements below are relative, from a reference run on an idle 32-core box with a
2 GB heap. Reproduce them on your own hardware with the scripts in section 6.

### Lever 1: Class-Data Sharing (CDS / AppCDS) -- the big universal win

On every launch the JVM normally reads, parses, verifies and links ~6500 classes
(Groovy runtime, BioJava, parquet/arrow, p2rank). **AppCDS** writes that parsed/verified
class metadata to an archive file once, then memory-maps it on subsequent launches,
skipping the parse/verify work.

- Impact: **about -16% to -20% wall time**, on every JVM tested.
- Mechanism: dynamic archive, built with `-XX:ArchiveClassesAtExit=app.jsa`, then used
  with `-XX:SharedArchiveFile=app.jsa`. Available on **all JDK 17+**.
- It caches class *metadata*, not compiled machine code.

### Lever 2: JIT compilation tier -- big, but JVM-version-dependent

p2rank prediction runs are short. The C2 (server) optimizing compiler spends CPU
compiling hot methods to fast native code, but a prediction run usually finishes before
that investment pays off. Restricting to the C1 (client) compiler with
`-XX:TieredStopAtLevel=1` warms up far faster.

- Impact: large on GraalVM (its optimizing compiler is the heaviest to initialize, ~-18%), moderate on
  HotSpot 17/25/26 (~-5% to -9%).
- Measured to win for every prediction workload up to **300 proteins in one JVM** -- C2
  never amortized within a prediction run at that scale.

> [!WARNING]
> Two exceptions. (1) **JDK 21**: C1-only gives **no benefit** there (full tiered +
> ParallelGC is actually fastest) -- a version-specific quirk, not present on 17/25/26.
> (2) **Very large batches or training/eval** (thousands of proteins, long-running
> iterative jobs): C2 throughput eventually wins, so keep full tiered for those.

### Lever 3: Garbage collector -- minor

- Ranking (mean): **ParallelGC** (best) < G1 < SerialGC, about a 4% spread.
- ParallelGC is throughput-oriented and cheap to start, a good default for both single
  and batch prediction. Not worth agonizing over.

### Lever 4: Heap size -- keep it small, and below 32 GB

- p2rank prediction is not memory-hungry: even a 12k-atom protein peaks near ~1 GB RSS.
  `-Xmx2048m` is plenty for prediction.

> [!TIP]
> To reproduce the ~1 GB figure, measure peak resident set, not heap: on Linux run
> `/usr/bin/time -v distro/prank predict -f some.pdb` and read "Maximum resident set size".

- **Critical interaction with CDS:** a heap >= 32 GB disables compressed oops, which
  changes which base CDS archive the JVM needs. Most JDK distributions do **not** ship
  the matching archive, so a large heap **silently disables CDS entirely** (see the
  pitfall in section 7). Keep the prediction heap well under 32 GB.

### Bonus lever: AOT cache (Project Leyden, JDK 24+ only)

The AOT cache is the modern successor to AppCDS. It stores loaded **and linked** classes,
and on JDK 25+ also method profiles so the JIT starts optimizing immediately.

- Impact: a further ~3-7% over AppCDS on JDK 25/26.
- One-step creation (JDK 25+): `-XX:AOTCacheOutput=app.aot`, then use `-XX:AOTCache=app.aot`.
- Not available before JDK 24, so treat it as an opt-in bonus on recent JVMs, not a baseline.

---

## 3. The ready-made launcher: `prank_burst`

`distro/prank_burst` is a drop-in replacement for `distro/prank` that applies the above
automatically and adapts to the running JDK.

What it does:
- Builds an AppCDS archive on first run (`bin/p2rank-appcds.jsa`), reuses it after, and
  rebuilds it when `p2rank.jar` changes.
- Uses ParallelGC.
- Uses C1-only JIT, **except on JDK 21** (where it keeps full tiered).
- Keeps the heap at 2 GB so compressed oops + CDS stay enabled.
- Silences benign `-Xlog:cds` module-mismatch noise.

Usage:

```bash
distro/prank_burst predict -f distro/test_data/1fbl.pdb      # first run builds the archive
distro/prank_burst predict -f distro/test_data/1fbl.pdb      # subsequent runs are faster
PRANK_FULL_JIT=1 distro/prank_burst predict distro/test_data/basic.ds    # full C2 for huge batches / training
JAVA_OPTS=-Xmx4g distro/prank_burst predict -f distro/test_data/1fbl.pdb # bigger heap (keep < 32 GB)
```

Measured result vs the stock `distro/prank`: roughly **-33% on single proteins** and
**-37% on a 30-protein batch**, with identical predictions.

> [!NOTE]
> CDS, JIT tier and GC never change p2rank's output. They only change timing. Always
> diff `_predictions.csv` after any tuning change to confirm this on your build.

> [!NOTE]
> **On Windows.** `prank_burst` is a bash script: it runs under Git Bash / MSYS (it
> detects `$OSTYPE=msys*` and switches the classpath separator to `;`), not in `cmd.exe`.
> There is no `prank_burst.bat`. If you launch via the native `distro/prank.bat`, it only
> sets the compatibility flags (such as heap, `--add-opens`, `--enable-native-access`), not the
> speedups. To get them, add these JVM-portable flags to `JAVA_OPTS` in `prank.bat`:
> `-XX:+UseParallelGC`, `-XX:TieredStopAtLevel=1` (omit on JDK 21), and AppCDS via
> `-XX:ArchiveClassesAtExit=<path>` on the first run then `-XX:SharedArchiveFile=<path>`
> after. See the flag list in section 8.

---

## 4. Choosing a JVM (17 through 26)

p2rank is compiled for Java 17 bytecode and runs on 17+. Capabilities by version:

| JDK | dynamic AppCDS | AOT cache | notes for p2rank |
|---|:---:|:---:|---|
| 17 (LTS) | yes | no | C1-only helps; fine baseline |
| 21 (LTS) | yes | no | **C1-only is a no-op here** -- keep full tiered |
| 24 | yes | yes | AOT cache appears (2-step) |
| 25 (LTS) | yes | yes | one-step AOT cache + method profiles; co-fastest |
| 26 | yes | yes | ~= JDK 25, no regression |

Vendor notes:
- **Oracle HotSpot 25/26 are the fastest** for optimized prediction and ship the full set
  of base CDS archives.
- **GraalVM** has the slowest *stock* startup (its compiler is heavy to init) but optimizes
  to nearly tie HotSpot. Since `prank_burst` uses C1-only, GraalVM's optimizing compiler
  is never engaged for prediction -- there is no reason to prefer it for the prediction path.
- **GraalVM ships only the default base CDS archive**, so the >=32 GB-heap CDS pitfall bites
  GraalVM hardest (Oracle HotSpot ships more archive variants).

If you can choose: **Oracle HotSpot 25 (LTS)** is the recommended JVM for the prediction path.

---

## 5. Two regimes: prediction vs training

The optimal config differs by *command class*, not by single-vs-batch:

| | prediction (`predict`, `rescore`) | training / eval (`traineval`, `crossval`, `eval-predict`) |
|---|---|---|
| JIT | C1-only (except JDK 21) | full tiered C2 (`PRANK_FULL_JIT=1`) |
| Heap | 2 GB | large, may exceed 32 GB |
| CDS | yes (keeps oops on) | often unavailable at >= 32 GB heap; use compact object headers for footprint instead |
| GC | ParallelGC | ParallelGC |
| dominant cost | startup + model load | the ML compute itself |

So a single protein and a 300-protein prediction batch want the **same** config; only
long-running training jobs want the throughput-oriented column.

---

## 6. Benchmark it yourself

Three scripts under `misc/test-scripts/` (run `./gradlew assemble` first; they use the
distro jar):

```bash
# Modes on the current JVM (stock-vs-faster comparison, JFR profile, phase breakdown):
misc/test-scripts/predict_bench.sh --compare       # stock vs prank_burst
misc/test-scripts/predict_bench.sh --profile       # JFR hot-method profile
misc/test-scripts/predict_bench.sh --phases        # startup phase breakdown

# Compare chosen JVMs (also checks the reference protein's predictions match across them):
misc/test-scripts/predict_bench_jres.sh 25.0.2-oracle 21.0.10-oracle

# Full tuning matrix (CDS x JIT x GC, + AOT where supported) across JVMs:
misc/test-scripts/predict_bench_matrix.sh --aot 26.0.1-oracle 25.0.2-oracle 21.0.10-oracle
misc/test-scripts/predict_bench_matrix.sh --list   # what JVMs are installed
```

The matrix script auto-detects per-JVM capabilities and skips configs a JVM cannot run
(e.g. AOT on < 25; the script uses the one-step `-XX:AOTCacheOutput`, which needs JDK 25+,
so JDK 24's two-step AOT cache is not exercised). Missing JVMs are reported and skipped,
never fatal; with no JVM
argument it benchmarks the current `java`. Add a new JVM simply by passing its SDKMAN
name or a path to its JAVA_HOME.

> [!TIP]
> Run benchmarks on an idle machine and ignore the first (warmup) iteration. Absolute
> numbers drift with load; the relative ranking between configs is the reliable output.

---

## 7. Pitfalls and gotchas

> [!WARNING]
> **A heap >= 32 GB silently disables CDS.** Heaps that large turn off compressed oops, so
> the JVM looks for a `*_nocoops*` base archive variant that many distributions (notably
> GraalVM) do not ship. The base archive fails to map and `-XX:ArchiveClassesAtExit` then
> reports "unsupported". This is the most common way to accidentally lose the biggest win.
> Symptom to check: run with `-Xlog:cds` and look for "Loading static archive failed".

> [!WARNING]
> **The AppCDS archive is JDK-specific, but only rebuilt on jar change.** `prank_burst`
> rebuilds `bin/p2rank-appcds.jsa` when `p2rank.jar` changes, not when you switch JDK or
> `JAVA_HOME`. Archives are tied to the exact JVM, so a stale archive from another JDK
> fails to map and you silently fall back to full class loading (CDS failures are non-fatal).
> After changing JVM, delete `distro/bin/p2rank-appcds.jsa` to force a rebuild. Likewise the
> archive path is fixed under the install's `bin/`, so a **read-only or shared install**
> cannot create it on first run and silently pays full startup cost every time. Use the
> confirmation check below to detect both cases.

> [!NOTE]
> **Confirming CDS is actually active.** Absence of a failure message does not prove the
> archive is being used. To positively confirm, run with `-Xlog:class+load=info` and check
> that p2rank and Groovy classes report `source: shared objects file`, for example:
>
> ```bash
> JAVA_OPTS="-Xlog:class+load=info" distro/prank_burst predict -f distro/test_data/1fbl.pdb \
>   | grep -E "(cz\.siret|groovy\.).*shared objects file" | head
> ```
>
> Do this on a *reuse* run, not the archive-building run: `prank_burst` rebuilds the archive
> whenever `p2rank.jar` changes (and on first run), and during a build run those classes load
> from the jar, not from the archive. This check is independent of `-Xlog:cds=off`: that flag
> only silences the `cds` log tag, it does not suppress `class+load` output.

> [!WARNING]
> **`-Xlog:aot=off` is only valid on JDK 24+.** The `aot` log tag does not exist on 17/21,
> where passing it aborts JVM startup. Only add AOT-related flags after checking the major
> version. (The matrix script handles this by gating AOT behind a version check; `prank_burst` sidesteps it entirely by not using AOT.)

> [!WARNING]
> **JDK major-version parsing.** A naive `sed 's/.*"\([0-9]*\).*/\1/'` is greedy and matches
> through the closing quote of `"21.0.10"`, returning an empty string on modern LTS version
> lines like `java version "21.0.10" 2026-01-20 LTS`. Use `sed -E 's/.*version "([0-9]+).*/\1/'`
> (anchored on the unique `version "`). The same bug previously made the stock `prank` /
> `prank.sh` launchers' Java-23+ flag gate silently never fire (`prank_burst` shipped with
> the fixed parse).

> [!NOTE]
> **Benign CDS log noise.** A dynamic archive layers on the JDK's base archive, which was
> built without p2rank's `--add-opens` / `--enable-native-access` flags, so the JVM logs
> error-level "Disabling optimized module handling" lines. CDS still loads and class data is
> still shared: the messages are cosmetic. `prank_burst` routes them off with `-Xlog:cds=off`.

> [!NOTE]
> **Compact object headers** (`-XX:+UseCompactObjectHeaders`, JDK 24 experimental, 25
> production) reduce memory but encode the object-header layout into the CDS archive, so an
> archive built with one header mode cannot be used with the other. They mainly help large
> training jobs (memory footprint), not the 2 GB prediction path, and can conflict with CDS
> on distributions that lack the matching archive variant.

---

## 8. Quick reference

Recommended flags for the **prediction** path (what `prank_burst` applies):

```
-Xmx2048m
-XX:+UseParallelGC
-XX:TieredStopAtLevel=1            # omit on JDK 21
-XX:SharedArchiveFile=<app.jsa>    # built once via -XX:ArchiveClassesAtExit
-Xlog:cds=off                      # cosmetic noise suppression
--add-modules jdk.incubator.vector # SIMD surface scan; added only if the JVM ships the module
```

> [!NOTE]
> `prank_burst` does not use the AOT cache. On JDK 24+ you can swap AppCDS for the
> AOT cache manually with `-XX:AOTCache=<app.aot>` instead of `-XX:SharedArchiveFile`
> (creation is two-step on 24, one-step on 25+; see the bonus lever in section 2). It is
> an opt-in further ~3-7%, not part of the `prank_burst` baseline.

Recommended flags for the **training / large-batch** path:

```
-Xmx<large>                        # size to the workload
-XX:+UseParallelGC
# full tiered C2 (do NOT set TieredStopAtLevel)
# CDS optional; if heap >= 32 GB it is unavailable, consider -XX:+UseCompactObjectHeaders
```

Priority order if you only do some of this: **(1) batch with a `.ds` file**, then
**(2) AppCDS**, then **(3) C1-only JIT (except JDK 21)**, then (4) ParallelGC. GC and AOT
are last-few-percent tweaks.

---

## 9. See also

This doc covers the single-protein / startup-bound regime. For profiling **long,
multi-threaded batch runs** (throughput, CPU efficiency, concurrency-correct phase
attribution) and measured findings on `surface_strategy` / `rf_flatten_target` / JVM
choice at full-dataset scale, see
[`batch-profiling-and-scale-effects.md`](../../misc/batch-profiling-and-scale-effects.md).
