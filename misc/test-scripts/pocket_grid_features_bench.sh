#!/usr/bin/env bash
#
# A/M/B/C benchmark for the pocket-grid + pocket-descriptors features.
#
# Runs the SAME workload four times and reports the deltas:
#   A: all OFF                      (no exports, no descriptors compute)
#   M: minimal pocket descriptors   (export_pocket_descriptors 1, pocket_descriptors=(volume), no grid)
#   B: all pocket descriptors       (export_pocket_descriptors 1, full auto-discovered list, no grid)
#   C: all ON                       (B plus -export_pocket_grid 1, full auto-discovered grid list)
#
# Deltas isolate the cost of each layer:
#   M-A: floor cost of having any per-pocket descriptor at all (just the cheapest one)
#   B-M: cost of the other 9 pocket descriptors on top of volume
#   C-B: marginal cost of adding pocket-grid export on top
#   C-A: total cost of having all features enabled
#
# Pair with:
#   - `./prank.sh bench pocket_grid <ds>` for the pure-build single-threaded
#     measurement (no writers, no rescoring).
#   - `pocket_grid_dataset_bench.sh` for a single-config dataset benchmark
#     (just the ON path, useful for one-shot timing).
#
# Usage:
#   ./misc/test-scripts/pocket_grid_features_bench.sh [target] [options]
#
# target:
#   - default:                  distro/test_data/1fbl.pdb  (small, fast)
#   - any *.pdb / *.cif file:   `prank predict -f <file>`
#   - any *.ds dataset name:    `prank predict <name>.ds`
#
# options:
#   --reps N            timed reps per config (default 3, plus one untimed warmup).
#                       Use higher N for noisy environments; mean is reported.
#   --threads N         override -threads (default: prank's own default).
#   --profile [jfr]     enable Java Flight Recorder for all runs; produces
#                       jfr-{A,B,C}-<ts>-rep<N>.jfr files in the cwd.
#                       Open with JDK Mission Control or `jfr print`.
#   --csv [FILE]        append a one-line summary to FILE (default
#                       misc/test-scripts/bench-results.csv). Columns documented
#                       at the top of that file on first write.
#   --quiet             suppress per-rep wall-time prints; only print summary.
#   --keep-output       leave the prediction outputs on disk for inspection.
#                       Default: cleaned after each rep so repeated runs don't
#                       accumulate.
#   --no-warmup         skip the untimed warmup rep before each config. Use for
#                       smoke runs or when measuring cold-start cost. Default
#                       is to warm up so JIT compiles before the first timed rep.
#   -h | --help         show this help and exit.
#
# Examples:
#
#   # Quick smoke during development, 3 reps + warmup, mean printed:
#   ./misc/test-scripts/pocket_grid_features_bench.sh
#
#   # Real dataset, 8 threads, JFR-profiled:
#   ./misc/test-scripts/pocket_grid_features_bench.sh coach420-fpocket \
#       --threads 8 --profile jfr
#
#   # Track across versions / commits:
#   ./misc/test-scripts/pocket_grid_features_bench.sh --csv
#   # Then `cat misc/test-scripts/bench-results.csv` to see the history.

set -u

# --- Defaults ---
DEFAULT_TARGET="distro/test_data/1fbl.pdb"
DEFAULT_CSV="misc/test-scripts/bench-results.csv"
REPS=3
THREADS=""
PROFILE=""
CSV_FILE=""
QUIET=0
KEEP_OUTPUT=0
NO_WARMUP=0

# --- Parse args ---
TARGET=""
while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)
            # Print the leading comment block as help text.
            awk 'NR==1{next} /^[^#]/{exit} {sub(/^# ?/,""); print}' "$0"
            exit 0
            ;;
        --reps)         REPS="$2"; shift 2 ;;
        --threads)      THREADS="$2"; shift 2 ;;
        --profile)      # optional value (jfr is the only one supported today)
                        if [ -n "${2:-}" ] && [ "${2:0:1}" != "-" ]; then
                            PROFILE="$2"; shift 2
                        else
                            PROFILE="jfr"; shift
                        fi ;;
        --csv)          # optional FILE (defaults to misc/test-scripts/bench-results.csv)
                        if [ -n "${2:-}" ] && [ "${2:0:1}" != "-" ]; then
                            CSV_FILE="$2"; shift 2
                        else
                            CSV_FILE="$DEFAULT_CSV"; shift
                        fi ;;
        --quiet)        QUIET=1; shift ;;
        --keep-output)  KEEP_OUTPUT=1; shift ;;
        --no-warmup)    NO_WARMUP=1; shift ;;
        --)             shift; break ;;
        -*) echo "unknown option: $1" >&2; exit 2 ;;
        *)  if [ -z "$TARGET" ]; then TARGET="$1"; shift
            else echo "unexpected positional arg: $1" >&2; exit 2; fi ;;
    esac
done
TARGET="${TARGET:-$DEFAULT_TARGET}"

# --- Resolve target mode (file vs dataset) ---
# Extension wins over file-existence: a `.ds` path is a dataset whether or not
# the file exists at the current cwd (prank resolves dataset names against its
# own dataset directories too). Structure files (.pdb/.cif/.bcif) go through
# `-f`. Anything else is treated as a dataset name and let prank's resolver
# sort it out.
case "$TARGET" in
    *.pdb|*.pdb.gz|*.cif|*.cif.gz|*.bcif|*.bcif.gz|*.cif.zst)
        MODE="file"
        PRANK_ARGS=("predict" "-f" "$TARGET")
        LABEL="$(basename "$TARGET")"
        ;;
    *)
        MODE="dataset"
        DATASET="${TARGET%.ds}"
        PRANK_ARGS=("predict" "${DATASET}.ds")
        LABEL="$(basename "$DATASET")"
        ;;
esac

[ -n "$THREADS" ] && PRANK_ARGS+=("-threads" "$THREADS")

# Pin grid spacing for the bench so runs are comparable across versions and
# more sensitive to grid-export cost than the production default (1.2 Å).
# 1.0 Å is a finer grid → more grid points → ~1.7× the per-protein work in
# the grid path, which makes optimizations / regressions easier to read.
PRANK_ARGS+=("-pocket_grid_spacing" "1.0")

# --- Discover all registered descriptors so configs B and C exercise everything ---
# Source-tree grep against the descriptor source dirs — when someone registers
# a new descriptor in the registry, this picks it up automatically without a
# script update.
discover_names() {
    local dir="$1"
    grep -hE 'name\(\) \{ return "' "$dir"/*.java 2>/dev/null \
        | sed -E 's/.*return "([^"]+)".*/\1/' \
        | sort -u | paste -sd, -
}
POCKET_DESC=$(discover_names \
    src/main/groovy/cz/siret/prank/program/routines/predict/output/descriptors)
GRID_DESC=$(discover_names \
    src/main/groovy/cz/siret/prank/program/routines/predict/output/grid/descriptors)
if [ -z "$POCKET_DESC" ] || [ -z "$GRID_DESC" ]; then
    echo "Could not discover descriptors — are you running from the repo root?" >&2
    exit 1
fi
# Minimal mode M uses only the volume descriptor — cheapest existing pocket
# descriptor, exercises the export pipeline at floor cost.
MIN_POCKET_DESC="volume"

# --- Captured environment ---
GIT_REV="$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
GIT_DIRTY="$(git diff --quiet HEAD 2>/dev/null || echo '+dirty')"
DATE_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
HOST="$(hostname)"
JAVA_VER="$(java -version 2>&1 | head -1 | sed 's/^[^"]*"\([^"]*\)".*/\1/')"
JAVA_MAJOR="$(echo "$JAVA_VER" | awk -F. '{print $1}')"
P2RANK_VER="$(grep -E "^version\s*=" build.gradle | head -1 | sed "s/.*'\\(.*\\)'.*/\\1/")"
TS="$(date +%Y%m%d-%H%M%S)"

# --- Output dirs ---
OUT_BASE="test_output/POCKET_GRID_FEATURES_BENCH/${TS}"

# --- Banner ---
cat <<EOF
============================================================
 pocket-grid features A/B bench

 target:      ${LABEL}   (${MODE})
 threads:     ${THREADS:-(prank default)}
 grid spacing: 1.0 Å (bench-pinned for cross-version comparability; prank default is 1.2)
 reps:        ${REPS} timed$( [ "$NO_WARMUP" -eq 1 ] && echo ", no warmup" || echo ", + 1 warmup" )
 profile:     ${PROFILE:-off}
 git rev:     ${GIT_REV}${GIT_DIRTY}
 p2rank ver:  ${P2RANK_VER}
 java:        ${JAVA_VER}
 host:        ${HOST}
 date (UTC):  ${DATE_ISO}

 pocket desc (full set used in B and C): (${POCKET_DESC})
 pocket desc (minimal — used in M):      (${MIN_POCKET_DESC})
 grid desc (used in C):                  (${GRID_DESC})
============================================================
EOF

# --- Worker: run prank once, return wall-time ms via stdout ---
run_one() {
    local cfg="$1"             # "A" | "M" | "B" | "C"
    local grid_flag="$2"       # "1" or "0" for -export_pocket_grid
    local desc_flag="$3"       # "1" or "0" for -export_pocket_descriptors
    local pocket_desc_list="$4"  # csv of pocket descriptor names (no parens), or empty to skip
    local grid_desc_list="$5"    # csv of grid descriptor names (no parens), or empty to skip
    local rep="$6"             # rep number (0 = warmup)
    local out_subdir="${OUT_BASE}/${cfg}/rep${rep}"

    local jfr_arg=""
    if [ "$PROFILE" = "jfr" ] && [ "$rep" -gt 0 ]; then
        # JFR only on timed reps; warmup runs uninstrumented.
        local jfr_file="jfr-${cfg}-${TS}-rep${rep}.jfr"
        jfr_arg="-XX:StartFlightRecording=filename=${jfr_file},settings=profile,dumponexit=true"
    fi

    mkdir -p "$(dirname "${out_subdir}.log")"

    # Build per-mode extra args. Lists are only passed when non-empty so each
    # mode's command line shows exactly what was selected.
    local extra_args=()
    [ -n "$pocket_desc_list" ] && extra_args+=("-pocket_descriptors" "(${pocket_desc_list})")
    [ -n "$grid_desc_list" ]   && extra_args+=("-pocket_grid_point_descriptors" "(${grid_desc_list})")

    local start_ns end_ns
    start_ns=$(date +%s%N)
    JAVA_OPTS="${JAVA_OPTS:-} ${jfr_arg}" ./prank.sh "${PRANK_ARGS[@]}" \
        "${extra_args[@]}" \
        -export_pocket_grid "${grid_flag}" \
        -export_pocket_descriptors "${desc_flag}" \
        -visualizations 0 \
        -out_subdir "${out_subdir}" \
        > "${out_subdir}.log" 2>&1
    local rc=$?
    end_ns=$(date +%s%N)

    if [ $rc -ne 0 ]; then
        echo "  FAILED (rc=$rc); see ${out_subdir}.log" >&2
        return 1
    fi
    local elapsed_ms=$(( (end_ns - start_ns) / 1000000 ))

    if [ "$KEEP_OUTPUT" -ne 1 ]; then
        rm -rf "${out_subdir}" "${out_subdir}.log"
    fi
    echo "$elapsed_ms"
}

# --- Mean helper (rounded ms) ---
mean() {
    # stdin: one number per line; stdout: arithmetic mean rounded to integer ms.
    awk '
        { sum += $1; n++ }
        END {
            if (n == 0) { print "NaN"; exit }
            printf "%.0f\n", sum / n
        }
    '
}

# --- A/M/B/C loop ---
mkdir -p "${OUT_BASE}"
declare -a A_TIMES M_TIMES B_TIMES C_TIMES

run_config() {
    local cfg="$1"
    local grid="$2"
    local desc="$3"
    local pocket_list="$4"   # csv of pocket descriptor names, empty to skip the flag
    local grid_list="$5"     # csv of grid descriptor names, empty to skip the flag
    local label="$6"
    local -n times_arr="$7"

    [ "$QUIET" -eq 0 ] && echo "Config $cfg (${label}):"
    if [ "$NO_WARMUP" -eq 0 ]; then
        if ! run_one "$cfg" "$grid" "$desc" "$pocket_list" "$grid_list" 0 >/dev/null; then exit 1; fi
        [ "$QUIET" -eq 0 ] && echo "  warmup: done"
    fi

    for r in $(seq 1 "$REPS"); do
        local ms
        if ! ms=$(run_one "$cfg" "$grid" "$desc" "$pocket_list" "$grid_list" "$r"); then exit 1; fi
        times_arr+=("$ms")
        [ "$QUIET" -eq 0 ] && printf '  rep %d: %s ms\n' "$r" "$ms"
    done
    echo
}

run_config A 0 0 ""                 ""             "all OFF"               A_TIMES
run_config M 0 1 "${MIN_POCKET_DESC}" ""            "minimal (volume only)" M_TIMES
run_config B 0 1 "${POCKET_DESC}"   ""             "all pocket descriptors" B_TIMES
run_config C 1 1 "${POCKET_DESC}"   "${GRID_DESC}" "all ON (+ grid)"        C_TIMES

A_MEAN=$(printf '%s\n' "${A_TIMES[@]}" | mean)
M_MEAN=$(printf '%s\n' "${M_TIMES[@]}" | mean)
B_MEAN=$(printf '%s\n' "${B_TIMES[@]}" | mean)
C_MEAN=$(printf '%s\n' "${C_TIMES[@]}" | mean)

delta_ms() {
    awk -v hi="$1" -v lo="$2" 'BEGIN { printf "%.0f\n", hi - lo }'
}
delta_pct() {
    # percentage relative to the baseline (lo). awk handles lo==0 by printing "inf".
    awk -v hi="$1" -v lo="$2" 'BEGIN { if (lo == 0) { print "inf" } else { printf "%.1f\n", (hi - lo) / lo * 100 } }'
}

MA_MS=$(delta_ms  "$M_MEAN" "$A_MEAN")
MA_PCT=$(delta_pct "$M_MEAN" "$A_MEAN")
BM_MS=$(delta_ms  "$B_MEAN" "$M_MEAN")
BM_PCT=$(delta_pct "$B_MEAN" "$M_MEAN")
BA_MS=$(delta_ms  "$B_MEAN" "$A_MEAN")
BA_PCT=$(delta_pct "$B_MEAN" "$A_MEAN")
CB_MS=$(delta_ms  "$C_MEAN" "$B_MEAN")
CB_PCT=$(delta_pct "$C_MEAN" "$B_MEAN")
CA_MS=$(delta_ms  "$C_MEAN" "$A_MEAN")
CA_PCT=$(delta_pct "$C_MEAN" "$A_MEAN")

# --- Summary ---
cat <<EOF
============================================================
 Summary

 A (all OFF              ):  ${A_TIMES[*]}  ->  mean ${A_MEAN} ms
 M (volume only, no grid ):  ${M_TIMES[*]}  ->  mean ${M_MEAN} ms
 B (all pocket descriptors):  ${B_TIMES[*]}  ->  mean ${B_MEAN} ms
 C (all ON + grid         ):  ${C_TIMES[*]}  ->  mean ${C_MEAN} ms

 Min-descriptor floor (M-A):  ${MA_MS} ms  (${MA_PCT}%)
 Rest of descriptors  (B-M):  ${BM_MS} ms  (${BM_PCT}%)
 All pocket descriptors (B-A): ${BA_MS} ms  (${BA_PCT}%)
 Grid export cost     (C-B):  ${CB_MS} ms  (${CB_PCT}%)
 Total feature cost   (C-A):  ${CA_MS} ms  (${CA_PCT}%)
============================================================
EOF

# --- CSV log ---
if [ -n "$CSV_FILE" ]; then
    if [ ! -f "$CSV_FILE" ]; then
        echo "date_utc,git_rev,p2rank_ver,java_major,host,target,mode,threads,reps,a_mean_ms,m_mean_ms,b_mean_ms,c_mean_ms,ma_delta_ms,ma_delta_pct,bm_delta_ms,bm_delta_pct,ba_delta_ms,ba_delta_pct,cb_delta_ms,cb_delta_pct,ca_delta_ms,ca_delta_pct" \
            > "$CSV_FILE"
    fi
    echo "${DATE_ISO},${GIT_REV}${GIT_DIRTY},${P2RANK_VER},${JAVA_MAJOR},${HOST},${LABEL},${MODE},${THREADS:-default},${REPS},${A_MEAN},${M_MEAN},${B_MEAN},${C_MEAN},${MA_MS},${MA_PCT},${BM_MS},${BM_PCT},${BA_MS},${BA_PCT},${CB_MS},${CB_PCT},${CA_MS},${CA_PCT}" \
        >> "$CSV_FILE"
    echo "CSV: appended one row to ${CSV_FILE}"
fi
