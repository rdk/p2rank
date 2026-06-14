#!/usr/bin/env bash
#
# End-to-end benchmark + profiler for `prank predict`, using the CURRENT JRE
# (whatever `java` / $JAVA_HOME resolves to).
#
# Measures wall time per protein (one JVM per run, as a user invokes it) and the
# app-internal "Finished successfully in N seconds" time (excludes JVM startup), so
# the fixed startup/model-load overhead is visible separately from the actual work.
#
# Modes (combine freely):
#   default      per-protein timing table + average, for one launcher
#   --compare    run BOTH distro/prank and distro/prank_burst and show the speedup
#   --phases     print the startup phase breakdown (JVM -> config -> model -> work)
#                with real per-line wall-clock timestamps, for one small protein
#   --breakdown  DETAILED phase breakdown: a per-phase table (secs + %) plus a class-load
#                census and JVM-startup probes, for one protein. Delegates to
#                predict_breakdown.sh; deeper than --phases.
#   --profile    JFR CPU profile: run the protein set repeated in ONE JVM and print
#                the top hot methods (needs the `jfr` tool from the JDK)
#
# Usage:
#   ./misc/test-scripts/predict_bench.sh [proteins...] [options]
#
# proteins:
#   Zero or more .pdb/.cif paths. Default: a 5-protein size spread from
#   distro/test_data (1t7qa, 1fbl, 1a26A, 2W83, 1AHP).
#
# options:
#   -r, --reps N        timed reps per protein (default 4, plus one untimed warmup)
#   -l, --launcher CMD  launcher to benchmark (default: distro/prank)
#       --compare       benchmark distro/prank vs distro/prank_burst
#       --phases        startup phase breakdown only
#       --breakdown     detailed per-phase table + class-load census only
#       --deep          with --breakdown: also run the expanded probes (histogram,
#                       subsystem first-load, zstd/deserialize split, JIT/GC)
#       --profile       JFR hot-method profile only
#       --profile-reps N  copies of the protein set in the JFR dataset (default 6)
#   -h, --help          show this help
#
# Examples:
#   ./misc/test-scripts/predict_bench.sh                       # default 5-protein table
#   ./misc/test-scripts/predict_bench.sh --compare             # stock vs faster
#   ./misc/test-scripts/predict_bench.sh distro/test_data/1fbl.pdb -r 8
#   ./misc/test-scripts/predict_bench.sh --phases
#   ./misc/test-scripts/predict_bench.sh --profile
#
# Notes:
#   - Run `./gradlew assemble` first: the distro launchers use distro/bin/p2rank.jar,
#     not freshly compiled test classes.
#   - Results are sensitive to machine load. Run on an idle box; warmup is excluded.
#

set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1   # repo root
source misc/test-scripts/bench-common.sh

# Defaults
REPS=4
LAUNCHER="distro/prank"
COMPARE=0
PHASES=0
BREAKDOWN=0
BREAKDOWN_DEEP=0
PROFILE=0
PROFILE_REPS=6
PROTEINS=()
OUT=$(mktemp -d /tmp/predict_bench.XXXXXX)
trap 'rm -rf "$OUT"' EXIT

usage() { bench_usage; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--reps)        REPS="$2"; shift 2 ;;
        -l|--launcher)    LAUNCHER="$2"; shift 2 ;;
        --compare)        COMPARE=1; shift ;;
        --phases)         PHASES=1; shift ;;
        --breakdown)      BREAKDOWN=1; shift ;;
        --deep)           BREAKDOWN_DEEP=1; shift ;;
        --profile)        PROFILE=1; shift ;;
        --profile-reps)   PROFILE_REPS="$2"; shift 2 ;;
        -h|--help)        usage ;;
        -*)               echo "Unknown option: $1" >&2; usage ;;
        *)                PROTEINS+=("$1"); shift ;;
    esac
done

# Default protein set: size spread from distro/test_data
if [[ ${#PROTEINS[@]} -eq 0 ]]; then
    PROTEINS=("${BENCH_DEFAULT_PROTEINS[@]}")
fi

# Detailed per-phase breakdown: delegate to the dedicated script (single source of
# truth). Single-protein by nature, so it uses the first protein in the set.
# --deep forwards the expanded probe set.
if [[ $BREAKDOWN -eq 1 ]]; then
    bd_args=("${PROTEINS[0]}" -l "$LAUNCHER")
    [[ $BREAKDOWN_DEEP -eq 1 ]] && bd_args+=(--deep)
    exec misc/test-scripts/predict_breakdown.sh "${bd_args[@]}"
fi

# Report the active JRE
JAVACMD="$(bench_javacmd)"
echo "JRE: $("$JAVACMD" -version 2>&1 | head -1) ${JAVA_HOME:+($JAVA_HOME)}"
echo "Launcher(s): $([[ $COMPARE -eq 1 ]] && echo 'distro/prank vs distro/prank_burst' || echo "$LAUNCHER")"
echo ""

# --- helper: average wall time of one launcher over the protein set --------------
# usage: bench_set <launcher>   ; prints per-protein lines, echoes overall avg as last word
bench_set() {
    local cmd="$1"; local total=0; local count=0
    # warmup (build AppCDS archive if faster launcher, fill OS cache)
    $cmd predict -f "${PROTEINS[0]}" -o "$OUT/pbo" >/dev/null 2>&1
    for f in "${PROTEINS[@]}"; do
        [[ -f "$f" ]] || { printf "  %-22s MISSING\n" "$(basename "$f")" >&2; continue; }
        local szkb=$(( $(stat -c%s "$f") / 1024 ))
        local wt=0; local it=0; local n="$REPS"
        for ((i=0;i<n;i++)); do
            local s e out
            s=$(date +%s.%N)
            out=$($cmd predict -f "$f" -o "$OUT/pbo" 2>&1)
            e=$(date +%s.%N)
            wt=$(echo "$wt + $e - $s" | bc)
            local intern=$(grep -oE "Finished successfully in.*seconds" <<<"$out" | grep -oE "[0-9.]+ seconds" | grep -oE "[0-9.]+" | tail -1)
            it=$(echo "$it + ${intern:-0}" | bc)
        done
        local wavg=$(echo "scale=3; $wt/$n" | bc)
        local iavg=$(echo "scale=3; $it/$n" | bc)
        printf "  %-22s %5dKB  wall %7.3fs  internal %7.3fs\n" "$(basename "$f")" "$szkb" "$wavg" "$iavg" >&2
        total=$(echo "$total + $wavg" | bc); count=$((count+1))
    done
    echo "scale=3; $total/$count" | bc
}

# --- phase breakdown -------------------------------------------------------------
if [[ $PHASES -eq 1 ]]; then
    echo "=== Startup phase breakdown (per-line wall-clock, ${PROTEINS[0]##*/}) ==="
    stdbuf -oL -eL $LAUNCHER predict -f "${PROTEINS[0]}" -o "$OUT/pbo" 2>&1 \
      | bench_ts_prefix \
      | grep -iE "default config|loading model|enabled features|processing dataset|loading protein|predicting pockets|finished|results saved" \
      | head -20
    exit 0
fi

# --- JFR profile -----------------------------------------------------------------
if [[ $PROFILE -eq 1 ]]; then
    command -v "${JAVACMD%java}jfr" >/dev/null 2>&1 || JFRTOOL=jfr
    JFRTOOL="${JAVACMD%java}jfr"; [[ -x "$JFRTOOL" ]] || JFRTOOL=jfr
    DS="$OUT/profile.ds"; : > "$DS"
    for ((r=0;r<PROFILE_REPS;r++)); do for f in "${PROTEINS[@]}"; do readlink -f "$f"; done; done >> "$DS"
    local_total=$(( ${#PROTEINS[@]} * PROFILE_REPS ))
    echo "=== JFR profile: $local_total protein-runs in one JVM ($LAUNCHER) ==="
    JFR="$OUT/prank.jfr"
    JAVA_OPTS="-XX:StartFlightRecording=settings=profile,filename=$JFR,dumponexit=true ${JAVA_OPTS:-}" \
        $LAUNCHER predict "$DS" -o "$OUT/pbo" 2>&1 | grep -iE "predicting pockets finished|finished" | tail -2
    echo ""
    echo "--- Top leaf methods (CPU execution samples) ---"
    "$JFRTOOL" print --events jdk.ExecutionSample --stack-depth 1 "$JFR" 2>/dev/null \
      | grep -oE '^\s+(cz|java|jdk|groovy|org|sun)\.[a-zA-Z0-9_.$]+\.[a-zA-Z0-9_<>$]+\(' \
      | sed 's/^[[:space:]]*//; s/($//' | sort | uniq -c | sort -rn | head -20
    echo ""
    echo "--- Top p2rank/surface frames (anywhere in stack) ---"
    "$JFRTOOL" print --events jdk.ExecutionSample --stack-depth 40 "$JFR" 2>/dev/null \
      | grep -oE '(cz\.siret|cz\.cuni)\.[a-zA-Z0-9_.$]+\.[a-zA-Z0-9_<>$]+' \
      | sort | uniq -c | sort -rn | head -20
    exit 0
fi

# --- timing table ----------------------------------------------------------------
if [[ $COMPARE -eq 1 ]]; then
    echo "=== distro/prank (stock) ==="
    stock=$(bench_set "distro/prank")
    echo "=== distro/prank_burst ==="
    faster=$(bench_set "distro/prank_burst")
    sp=$(awk "BEGIN{printf \"%.1f\", (1 - $faster/$stock)*100}")
    echo ""
    printf "AVERAGE  stock %.3fs   faster %.3fs   speedup %s%%\n" "$stock" "$faster" "$sp"
else
    echo "=== $LAUNCHER ==="
    avg=$(bench_set "$LAUNCHER")
    echo ""
    printf "AVERAGE wall: %.3fs  (reps=%d)\n" "$avg" "$REPS"
fi
