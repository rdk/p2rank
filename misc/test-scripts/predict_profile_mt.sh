#!/usr/bin/env bash
#
# Multi-threaded STEADY-STATE profiler for `prank predict` on a DATASET, on the current JRE.
#
# This is the right tool for profiling LONG, parallel (e.g. -threads 16) runs and for
# comparing quick_compare matrix cells (surface_strategy x rf_flatten_target) and JVMs.
# It deliberately does NOT use the single-thread log-milestone phase deltas of
# predict_breakdown.sh: at >1 thread those deltas are meaningless (proteins process
# concurrently, log lines interleave, and wall-clock gaps include other threads' work).
#
# Instead it measures what actually matters for a parallel batch:
#   1. THROUGHPUT     proteins/sec from the app-internal "predicting pockets finished in N"
#                     time (excludes JVM boot), mean +/- sd over R fresh-JVM reps.
#   2. CPU EFFICIENCY (user+sys CPU-time)/(wall * threads) -- how busy the thread pool is;
#                     < 1 reveals a serial bottleneck, contention, GC stalls, or I/O.
#   3. GC            total pause ms + count + allocation, from -Xlog:gc.
#   4. PHASE CPU     ONE rep under JFR (settings=profile): every ExecutionSample across ALL
#                     worker threads is attributed to the leaf-most p2rank/lib subsystem
#                     (surface / features / forest / parse). This IS concurrency-correct --
#                     sampling aggregates over threads, unlike per-line log timing.
#
# Fixed startup/model-load/flatten cost is amortized to ~0 on a large dataset and is
# characterized separately by predict_breakdown.sh; this script targets steady state.
#
# Usage:
#   ./misc/test-scripts/predict_profile_mt.sh <dataset.ds> [opts] [-- <extra prank params>]
#
#   -r, --reps N        timed fresh-JVM reps (default 3, plus 1 untimed warmup)
#       --threads N      thread count to pass + use as the efficiency denominator (default 16)
#       --jfr            also run one JFR rep and print the phase-CPU attribution
#   -l, --launcher CMD   launcher (default: ./prank.sh)
#   -c CONFIG            config passed through (default: config/test-default)
#   everything after --  appended verbatim to the predict command (e.g. surface_strategy,
#                        rf_flatten, rf_flatten_target)
#
# Example (one quick_compare cell):
#   ./misc/test-scripts/predict_profile_mt.sh holo4k_subset200.ds --threads 16 --jfr -- \
#       -surface_strategy packed_distinct_v4 -rf_flatten 1 -rf_flatten_target Int16LeafSoaLegacyFlatBinaryForest
#
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

REPS=3; THREADS=16; JFR=0; WARMUP=1; LAUNCHER="./prank.sh"; CONFIG="config/test-default"; DATASET=""
EXTRA=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--reps) REPS="$2"; shift 2 ;;
        --threads) THREADS="$2"; shift 2 ;;
        --jfr)     JFR=1; shift ;;
        --no-warmup) WARMUP=0; shift ;;
        -l|--launcher) LAUNCHER="$2"; shift 2 ;;
        -c)        CONFIG="$2"; shift 2 ;;
        --)        shift; EXTRA=("$@"); break ;;
        -*)        echo "unknown opt: $1" >&2; exit 1 ;;
        *)         DATASET="$1"; shift ;;
    esac
done
[[ -n "$DATASET" ]] || { echo "need a dataset" >&2; exit 1; }

JAVACMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
OUT=$(mktemp -d /tmp/ppmt.XXXXXX); trap 'rm -rf "$OUT"' EXIT
TFLAGS=(-threads "$THREADS" -rf_threads "$THREADS" -r_threads "$THREADS")
RUN=($LAUNCHER predict "$DATASET" -c "$CONFIG" "${TFLAGS[@]}" -cache_datasets 0 -log_to_console 0 "${EXTRA[@]}")

echo "JRE:      $("$JAVACMD" -version 2>&1 | head -1) | $("$JAVACMD" -version 2>&1 | sed -n 3p | grep -oE 'GraalVM|HotSpot[^,]*')"
echo "Launcher: $LAUNCHER   threads: $THREADS   reps: $REPS"
echo "Dataset:  $DATASET    extra: ${EXTRA[*]:-(none)}"
echo ""

# protein count is read from the launcher's own "(n/size)" log lines per rep (NPROT).

# ---- warmup ----
if [[ $WARMUP -eq 1 ]]; then
    echo "warmup (discarded) ..."
    "${RUN[@]}" -o "$OUT/warm" >/dev/null 2>&1
else
    echo "warmup skipped (--no-warmup); assuming OS cache already warm"
fi

declare -a WALL APP CPU GCMS
for ((i=1;i<=REPS;i++)); do
    GCLOG="$OUT/gc$i.log"
    TIMEF="$OUT/time$i.txt"
    LOGF="$OUT/run$i.log"
    JAVA_OPTS="-Xlog:gc:file=$GCLOG:uptime,tags ${JAVA_OPTS:-}" \
      /usr/bin/time -f "%e %U %S %M" -o "$TIMEF" \
      "${RUN[@]}" -o "$OUT/r$i" >"$LOGF" 2>&1
    read -r wall user sys maxrss < "$TIMEF"
    cpu=$(awk -v u="$user" -v s="$sys" 'BEGIN{print u+s}')
    # app-internal time (excludes JVM boot)
    app=$(grep -oE "predicting pockets finished in [0-9: hm14] *[0-9.]+ seconds" "$LOGF" | grep -oE "[0-9.]+ seconds" | grep -oE "[0-9.]+" | tail -1)
    [[ -z "$app" ]] && app=$(grep -oE "Finished successfully in.*seconds" "$LOGF" | grep -oE "[0-9.]+ seconds" | grep -oE "[0-9.]+" | tail -1)
    n=$(grep -oE "\([0-9]+/[0-9]+\)" "$LOGF" | grep -oE "/[0-9]+" | tr -d / | sort -n | tail -1)
    gcms=$(awk '/Pause/{for(j=1;j<=NF;j++) if($j ~ /ms$/){g=$j}; sub(/ms/,"",g); s+=g} END{printf "%.0f", s+0}' "$GCLOG" 2>/dev/null)
    gcn=$(grep -c "Pause" "$GCLOG" 2>/dev/null)
    WALL[i]=$wall; APP[i]=$app; CPU[i]=$cpu; GCMS[i]=$gcms; NPROT=$n
    eff=$(awk -v c="$cpu" -v w="$wall" -v t="$THREADS" 'BEGIN{printf "%.2f", (w>0&&t>0)?c/(w*t):0}')
    tput=$(awk -v n="$n" -v a="$app" 'BEGIN{printf "%.1f", (a>0)?n/a:0}')
    printf "  rep %d: wall=%5ss  app=%5ss  proteins=%s  thrput=%6s p/s  cpu=%6ss  eff=%s  gc=%sms/%s\n" \
        "$i" "$wall" "$app" "$n" "$tput" "$cpu" "$eff" "$gcms" "${gcn:-0}"
done

echo ""
echo "== summary (mean +/- sd over $REPS reps; $NPROT proteins) =="
summ() { # name, array values
    local name="$1"; shift
    awk -v name="$name" 'BEGIN{n=0;s=0;ss=0} {for(i=1;i<=NF;i++){n++;s+=$i;ss+=$i*$i}}
      END{m=s/n; sd=(n>1)?sqrt((ss-n*m*m)/(n-1)):0; printf "  %-22s %8.2f  +/- %.2f\n",name,m,sd}' <<<"$*"
}
summ "app time (s)"        "${APP[@]}"
summ "wall time (s)"       "${WALL[@]}"
summ "CPU time (s)"        "${CPU[@]}"
summ "GC pause (ms)"       "${GCMS[@]}"
# throughput + efficiency from means
awk -v reps="$REPS" -v n="$NPROT" -v t="$THREADS" -v app="${APP[*]}" -v wall="${WALL[*]}" -v cpu="${CPU[*]}" '
BEGIN{ na=split(app,A," "); for(i=1;i<=na;i++)sa+=A[i]; ma=sa/na;
       nw=split(wall,W," "); for(i=1;i<=nw;i++)sw+=W[i]; mw=sw/nw;
       nc=split(cpu,C," "); for(i=1;i<=nc;i++)sc+=C[i]; mc=sc/nc;
       printf "  %-22s %8.1f  proteins/s (app-internal)\n","throughput", n/ma;
       printf "  %-22s %8.2f  (1.0 = all %d threads fully busy)\n","cpu efficiency", mc/(mw*t), t; }'

if [[ $JFR -eq 1 ]]; then
    echo ""
    echo "== phase CPU attribution (1 JFR rep, all threads, leaf-most app subsystem) =="
    JFRF="$OUT/p.jfr"; JFRTOOL="${JAVACMD%java}jfr"; [[ -x "$JFRTOOL" ]] || JFRTOOL=jfr
    JAVA_OPTS="-XX:StartFlightRecording=settings=profile,filename=$JFRF,dumponexit=true ${JAVA_OPTS:-}" \
      "${RUN[@]}" -o "$OUT/jfr" >/dev/null 2>&1
    "$JFRTOOL" print --events jdk.ExecutionSample --stack-depth 80 "$JFRF" 2>/dev/null | awk '
      /jdk.ExecutionSample/ { assigned=0; total++ ; next }
      assigned { next }
      {
        if ($0 ~ /cz\.cuni\.cusbg|cz\.siret\.prank\.geom/)      { b["surface (SAS gen)"]++; assigned=1 }
        else if ($0 ~ /cz\.siret\.prank\.fforest/)              { b["forest (RF score)"]++; assigned=1 }
        else if ($0 ~ /cz\.siret\.prank\.features/)             { b["feature extraction"]++; assigned=1 }
        else if ($0 ~ /org\.biojava|org\.rcsb/)                 { b["PDB/CIF parse"]++; assigned=1 }
      }
      END{ tot=0; for(k in b)tot+=b[k];
           for(k in b) printf "  %-22s %6d  %5.1f%% (of app frames)\n", k, b[k], 100*b[k]/tot | "sort -k2 -rn";
           close("sort -k2 -rn");
           printf "  %-22s %6d  (app frames sampled; remaining = JVM/GC/JIT/other)\n","[total attributed]",tot }'
fi
