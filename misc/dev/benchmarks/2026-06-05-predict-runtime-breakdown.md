# `prank predict` Runtime Breakdown (single file)

> [!NOTE]
> Intermediate / scratch findings, not a landmark reference. Captured while adding
> the runtime-breakdown tooling. Numbers come from single cold runs on one machine
> and drift with load: treat the sub-phase PROPORTIONS as the signal, not the
> absolute seconds. Lives under `misc/dev/benchmarks/` (scratch), not the curated
> `documentation/dev/`.

Where wall-clock time goes in a single `prank predict -f <one.pdb>` run. For a
typical single file, roughly 93% is fixed overhead (JVM startup, Groovy config
compile, model deserialization, JIT warmup) and only ~7% is actual pocket-finding.

- Date: 2026-06-05
- JRE: Oracle HotSpot 25.0.3 (`25.0.3-oracle`)
- Protein: `distro/test_data/clean/1t7qa.pdb` (small, 136 KB, 1728 atoms, 3588 SAS points)
- Launcher: `distro/prank` (stock), with `distro/prank_faster` cross-checked
- Tool: `misc/test-scripts/predict_breakdown.sh` (see [Reproduction](#reproduction))

## TL;DR

- ~93% of a single-file run is fixed overhead; actual pocket prediction is ~7%
  (clustering + RF scoring ~1.5%).
- The single biggest avoidable cost is the **C2 JIT compiler**: stock HotSpot
  burns ~15.7 s of C2 compiler CPU (on background threads) during a ~9 s run and
  never pays it back. `distro/prank_faster` (C1-only via `-XX:TieredStopAtLevel=1`)
  cuts total compiler CPU from ~20.6 s to ~3.6 s. This is why it wins, not GC.
- **Model load is 90% deserialization, 10% decompression.** The 22 MB `model.zst`
  decompresses in ~0.24 s; the ~2.0 s cost is `ObjectInputStream` rebuilding the
  random-forest object graph. A flatter model format would help far more than
  better compression.
- **Config parse (~2.2 s) is the Groovy compiler**, not the config: parsing one
  `default.groovy` pulls in ANTLR + `org.codehaus.groovy` and compiles at runtime.
- Startup (~2.6 s) is application class-loading, not the JVM (bare `java -version`
  is 0.06 s): Groovy runtime (loaded immediately because `Main` is Groovy), then
  Log4j2 (~1.0 s, gates the banner), then Jackson/JAXB.

## Top-level phases (stock `distro/prank`, ~9.3 s total)

| Phase | ~Time | Share | What dominates |
|-------|------:|------:|----------------|
| A. startup (JVM + classload) | ~2.6 s | ~28% | application class-loading; JVM itself is only 0.15 s |
| B. config parse | ~2.2 s | ~23% | Groovy compiler compiling `default.groovy` at runtime |
| C. model load | ~2.3 s | ~25% | zstd decompress (~0.24 s) + ObjectInputStream deserialize (~2.0 s) |
| D1. PDB parse | ~0.33 s | ~4% | BioJava |
| D2. surface | ~0.63 s | ~7% | FasterMolecularSurface accessible surface |
| D3. features | ~0.64 s | ~7% | per-SAS-point feature vectors (kd-tree heavy) |
| E. prediction | ~0.13 s | ~1.5% | clustering + random-forest scoring |
| F. output | ~0.52 s | ~5% | CSV / points / visualization files |

## Expanded sub-phases (class-load waves + subsystem first-load)

Classpath context: 135 jars, 86 MB on `distro/bin/lib` (biggest: weka-dev 9.5 MB,
groovy 7.9 MB, zstd-jni 7.4 MB, parquet-column 3.3 MB, guava 3.0 MB).

| Phase / sub-phase | ~Time | Evidence |
|-------------------|------:|----------|
| A1 JVM Create VM | 0.15 s | `-Xlog:startuptime`; module init 0.09 s |
| A2a JVM core + Groovy runtime | ~0.15-1.0 s | 873 java.* classes in first 0.25 s; p2rank `Main` at 0.166 s, Groovy runtime at 0.192 s |
| A2b Log4j2 + commons | ~1.0-1.3 s | log4j2 first class at 0.959 s; org.apache surge (386) at 1.0-1.25 s. Banner waits on logging |
| A2c Jackson + JAXB | ~1.3-2.4 s | com.fasterxml (132) at 1.25-1.5 s; com.sun (145) at 1.5-1.75 s |
| B1 ANTLR parse | ~2.75-3.0 s | groovyjarjarantlr4 (47) |
| B2 codehaus compile | ~3.0-4.0 s | org.codehaus.groovy waves (~300 classes) |
| B3 Guava + eval | ~4.25-4.7 s | guava first at 2.43 s, surge of 152 at 4.25-4.5 s |
| C1 zstd decompress (22 MB) | ~0.24 s (~10%) | `zstd -dc` wall 0.23-0.25 s |
| C2 deserialize RF graph | ~2.0 s (~90%) | pulls weka (first at 4.645 s) + guava |

Class-load census for one run: 6714 classes total, bucketed ~3800 in startup,
~1560 in config parse, ~400 in model load, ~930 in work.

## Cross-cutting costs (overlap all phases, background threads)

These run concurrently with the app (so they do not add to wall time linearly),
but they consume CPU and explain the launcher difference:

| | stock `distro/prank` | `distro/prank_faster` |
|---|---|---|
| JIT compiler CPU (`-XX:+CITime`) | ~20.6 s (C1 4.8 s + C2 15.7 s + OSR) | 3.6 s (C1-only) |
| GC (`-Xlog:gc`) | 273 ms / 16 GCs (G1) | 212 ms / 5 GCs (ParallelGC) |

> [!IMPORTANT]
> The ~15.7 s of C2 compiler CPU on a ~9 s run is the empirical backing for the
> C1-only choice in `distro/prank_faster`. C2's optimizing compilation never pays
> back its own cost on a workload this short.

## Where the leverage is (ranked)

1. C2 JIT (~16 s CPU, mostly wasted) -> already addressed by `prank_faster`
   C1-only. Biggest single win, no code change.
2. Groovy config compile (~2.2 s + compiler class loading) -> precompile or cache
   the compiled `default.groovy`; also removes ANTLR + codehaus from startup.
3. Model deserialize (~2.0 s) -> the Java-serialization format is the cost, not
   zstd. A flatter / mmap-able model format would help; amortizes to zero in batch.
4. Eager class loading (Log4j2, Jackson) -> what AppCDS caches; lazy-init would
   trim cold starts.

## Second data point: large protein + `prank_faster`

The base table above is the small / stock case (where fixed overhead dominates).
For contrast, a large protein on the optimized launcher, where actual compute
finally matters. NOTE: this changes TWO variables at once (protein size AND
launcher), so attribute carefully: the launcher explains the A/B/JIT collapse,
the protein size explains the D/F growth.

- Protein: `distro/test_data/1AHP.pdb` (1.07 MB, 12830 atoms, 17403 SAS points)
- Launcher: `distro/prank_faster` (AppCDS + C1-only JIT + ParallelGC)
- Total: ~10.2 s
- Command: `./misc/test-scripts/predict_breakdown.sh distro/test_data/1AHP.pdb -l distro/prank_faster --deep`

| Phase | small `1t7qa` / stock | large `1AHP` / faster |
|-------|---:|---:|
| A. startup | 28% (2.60 s) | 13.6% (1.39 s) |
| B. config parse | 22.5% (2.08 s) | 11.4% (1.17 s) |
| C. model load | 24.4% (2.26 s) | 27.7% (2.83 s) <- #1 |
| D1. PDB parse | 4.2% (0.39 s) | 8.4% (0.85 s) |
| D2. surface | 6.0% (0.55 s) | 3.7% (0.38 s) |
| D3. features | 6.6% (0.61 s) | 17.9% (1.83 s) <- #2 |
| E. prediction | 1.1% (0.11 s) | 2.8% (0.29 s) |
| F. output | 5.2% (0.48 s) | 13.4% (1.37 s) |
| JIT compiler CPU | ~21.7 s (C1+C2) | 4.1 s (C1-only) |
| GC | 250 ms / 15 | 174 ms / 4 |

What it shows:

- Fixed overhead (A+B+C) drops from ~75% to ~53%; actual compute (D+E+F) rises
  from ~23% to ~46%. On a real-world-sized protein the algorithm finally matters.
- Model load (C, ~2.8 s) becomes the #1 phase: a flat cost paid regardless of
  protein, still ~90% ObjectInputStream deserialize (zstd is only 0.25 s).
- Feature extraction (D3, ~1.8 s) becomes the #2 phase and is the real per-protein
  compute, scaling with SAS-point count (kd-tree heavy).
- `prank_faster` delivers as predicted: AppCDS shrinks startup+config (banner at
  1.25 s vs 2.40 s) and C1-only JIT cuts compiler CPU from ~21.7 s to 4.1 s.
- New subsystem visible on the large run: `org.glassfish` (302 classes at ~5.5 s,
  JAXB/JSON-B), pulled in during the work phase.

Rule of thumb: optimize startup (AppCDS/config) for single small files; the model
deserialize (C) and feature extraction (D3) are where time goes for large proteins
and batches.

## Related finding (fixed)

During this investigation we found a redundant second structure reparse in the
output phase: `PredictPocketsRoutine` passed `item.protein` (which re-reads from
disk, because the `predict` command runs uncached) instead of the already-loaded
`pair.protein`. Fixed in commit `9ef94a92`; the breakdown's milestone timeline now
shows a single `loading protein`.

## Reproduction

```bash
./gradlew assemble

# top-level table + class-load census + startup probes:
./misc/test-scripts/predict_breakdown.sh                       # small default protein, stock
./misc/test-scripts/predict_bench.sh --breakdown               # same, via the bench script
./misc/test-scripts/predict_breakdown.sh -l distro/prank_faster distro/test_data/2W83.pdb

# expanded probes used for this note (ad hoc):
JAVA_OPTS="-Xlog:class+load=info:file=cl.txt:uptime,tags" distro/prank predict -f distro/test_data/clean/1t7qa.pdb -o /tmp/o   # class-load timeline
JAVA_OPTS="-Xlog:startuptime"  distro/prank predict -f ... -o /tmp/o                                                            # JVM Create VM
JAVA_OPTS="-XX:+CITime -Xlog:gc" distro/prank predict -f ... -o /tmp/o 2>&1 | grep -iE "compilation time|Pause"                 # JIT + GC
zstd -dc distro/models/default/model.zst > /dev/null                                                                            # pure decompress wall
```

See also: `documentation/dev/jvm-performance-tuning.md` (curated prose),
`.claude/skills/predict-runtime-breakdown/SKILL.md` (the skill that drives this).
