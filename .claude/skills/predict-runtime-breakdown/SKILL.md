---
name: predict-runtime-breakdown
description: Produce a detailed phase-by-phase runtime breakdown of a single `prank predict` run (JVM boot, class-loading, Groovy config compile, model load, PDB parse, surface, feature extraction, prediction, output) on the current JRE. Use when asked where p2rank prediction time goes, to profile startup/runtime overhead, to find what to optimize, or to compare launchers/JVMs at sub-phase granularity.
---

# P2Rank predict runtime breakdown

Decompose where a single `prank predict` run spends wall-clock time. For typical
single-file prediction most of the time is fixed overhead (JVM startup, Groovy config
compile, model deserialization, JIT warmup), not pocket-finding, so a flat "it took N
seconds" hides the real cost structure. This skill produces the per-phase table.

## How to run

The reproducible tool is committed at `misc/test-scripts/predict_breakdown.sh`. It runs
against the distro launchers, so build first:

```bash
./gradlew assemble
./misc/test-scripts/predict_breakdown.sh                         # small default protein, stock launcher
./misc/test-scripts/predict_breakdown.sh distro/test_data/2W83.pdb   # a specific (larger) protein
./misc/test-scripts/predict_breakdown.sh -l distro/prank_burst      # see the CDS + C1-JIT launcher
./misc/test-scripts/predict_breakdown.sh --deep                      # + classpath, class-load histogram, subsystem first-load, zstd/deserialize split, JIT/GC
```

It is also exposed as a mode of the main bench script, alongside `--phases`/`--profile`:

```bash
./misc/test-scripts/predict_bench.sh --breakdown                      # same, via predict_bench.sh
./misc/test-scripts/predict_bench.sh --breakdown -l distro/prank_burst distro/test_data/2W83.pdb
```

To compare JREs, switch the active JVM (e.g. via SDKMAN `JAVA_HOME`) and re-run; the
script always reports the JRE it used in its header.

## What the script does (and how to reproduce by hand if needed)

It combines five probes into one report:

1. **Bare JVM boot** -- `/usr/bin/time java -version`. Establishes the floor (~0.06 s);
   proves the "startup" phase is application class-loading, not the JVM itself.
2. **JVM Create VM** -- `JAVA_OPTS="-Xlog:startuptime"`. The JVM-internal init cost,
   including module-system init (inflated by `--add-modules jdk.incubator.vector`).
3. **Timestamped run** -- a full per-line `perl -MTime::HiRes` timeline (the same
   technique as `predict_bench.sh --phases`, but unfiltered) whose milestone log lines
   give the phase boundaries.
4. **Phase table** -- deltas between those milestones, as seconds and % of total.
5. **Class-load census** -- `JAVA_OPTS="-Xlog:class+load=info:file=...:uptime,tags"`,
   bucketed by phase window and tallied by top-level package. Shows that class loading
   dominates startup and the Groovy compiler dominates config parse.

Plus model-dir facts (`du`/`find` on `distro/models/default`) and the raw milestone
timeline as evidence.

## The phases (what each milestone means)

| Phase | Milestone span | What dominates |
|-------|----------------|----------------|
| A. startup | process start -> "loading default config" | JVM boot (~0.15 s) + application class-loading (Groovy runtime, Log4j2, BioJava/Arrow/Parquet, classpath `bin/lib/*` scan) |
| B. config parse | "loading default config" -> "predicting pockets for proteins" | the Groovy **compiler** (`org.codehaus.groovy` + `groovyjarjarantlr4`) compiling `default.groovy` at runtime |
| C. model load | "Loading model from directory" -> "effectively enabled features" | zstd-decompress of the ~22 MB `model.zst` + `ObjectInputStream` RF deserialize |
| D1. PDB parse | "loading protein" -> "structure atoms" | BioJava structure parsing |
| D2. surface | "structure atoms" -> "SAS points" | FasterMolecularSurface accessible-surface computation |
| D3. features | "SAS points" -> "PREDICTING POCKETS" | per-SAS-point feature vectors (kd-tree heavy) |
| E. prediction | "PREDICTING POCKETS" -> "pocket 1 -" | clustering + random-forest scoring of SAS points |
| F. output | "pocket 1 -" -> "predicting pockets finished" | writing CSV / points / visualization files, copying input |

JIT warmup is not a separate phase; first-time-interpreted code is smeared across A/B/D.

## How to present results

Show the phase table (seconds + share) and call out the largest targets. Typical finding
on a small protein with stock `distro/prank`: roughly A ~27%, B ~22%, C ~25%, D ~17%,
E ~1%, F ~5%, i.e. ~93% fixed overhead, ~7% actual prediction. The biggest single lever
is A+B (class loading + runtime Groovy config compile), which is what AppCDS in
`distro/prank_burst` attacks.

## Caveats (state these)

- Numbers are from ONE cold run and drift with machine load. The sub-phase **proportions**
  are the stable signal, not the absolute seconds. Re-run a few times before fine claims.
- Phase boundaries are derived from log milestones, so this is a best-effort decomposition,
  not instrumentation inside the code. The script also prints the raw timeline for
  verification.
- Use a small protein to make fixed overhead easiest to read; use a large one (e.g.
  `1AHP.pdb`) to see phases C-F grow relative to startup.

## Related

- `misc/test-scripts/predict_bench.sh` -- wall-time tables, `--compare`, `--phases`, `--profile` (JFR hot methods).
- `misc/test-scripts/predict_bench_jres.sh` -- cross-JRE timing comparison.
- `documentation/dev/jvm-performance-tuning.md` -- the prose analysis this skill automates.
- `misc/dev/benchmarks/2026-06-05-predict-runtime-breakdown.md` -- captured findings, including the expanded sub-phase decomposition and the JIT/GC cross-cutting costs.
