#!/usr/bin/env bash
#
# A/B/C benchmark for the pocket-grid + pocket-descriptors features.
#
# Runs the SAME workload three times and reports the deltas:
#   A: all OFF                      (-export_pocket_grid 0  -export_pocket_descriptors 0)
#   B: pocket descriptors ON only   (-export_pocket_grid 0  -export_pocket_descriptors 1)
#   C: all ON                       (-export_pocket_grid 1  -export_pocket_descriptors 1)
#
# Deltas isolate the cost of each feature on top of the baseline:
#   B-A: cost of pocket descriptors alone
#   C-B: marginal cost of adding pocket-grid export on top of descriptors
#   C-A: total cost of having both features enabled
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
#                       Use higher N for noisy environments; median is reported.
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
#   -h | --help         show this help and exit.
#
# Examples:
#
#   # Quick smoke during development, 3 reps + warmup, median printed:
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

# --- Discover all registered descriptors so config A exercises everything ---
# Source-tree grep against the descriptor source dirs — when someone registers
# a new descriptor in the registry, this picks it up automatically without a
# script update. Both lists are passed to both configs; config B's
# export-flags are off so the lists are inert (nothing computes), keeping the
# command-line shape symmetric between A and B.
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
PRANK_ARGS+=("-pocket_descriptors" "($POCKET_DESC)"
             "-pocket_grid_point_descriptors" "($GRID_DESC)")

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
 reps:        ${REPS} timed + 1 warmup
 profile:     ${PROFILE:-off}
 git rev:     ${GIT_REV}${GIT_DIRTY}
 p2rank ver:  ${P2RANK_VER}
 java:        ${JAVA_VER}
 host:        ${HOST}
 date (UTC):  ${DATE_ISO}

 pocket desc: (${POCKET_DESC})
 grid desc:   (${GRID_DESC})
============================================================
EOF

# --- Worker: run prank once, return wall-time ms via stdout ---
run_one() {
    local cfg="$1"        # "A" | "B" | "C"
    local grid_flag="$2"  # "1" or "0" for -export_pocket_grid
    local desc_flag="$3"  # "1" or "0" for -export_pocket_descriptors
    local rep="$4"        # rep number (0 = warmup)
    local out_subdir="${OUT_BASE}/${cfg}/rep${rep}"

    local jfr_arg=""
    if [ "$PROFILE" = "jfr" ] && [ "$rep" -gt 0 ]; then
        # JFR only on timed reps; warmup runs uninstrumented.
        local jfr_file="jfr-${cfg}-${TS}-rep${rep}.jfr"
        jfr_arg="-XX:StartFlightRecording=filename=${jfr_file},settings=profile,dumponexit=true"
    fi

    mkdir -p "$(dirname "${out_subdir}.log")"

    local start_ns end_ns
    start_ns=$(date +%s%N)
    JAVA_OPTS="${JAVA_OPTS:-} ${jfr_arg}" ./prank.sh "${PRANK_ARGS[@]}" \
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

# --- Median helper (integer ms) ---
median() {
    # stdin: one number per line; stdout: middle (or avg of two middles for even N)
    sort -n | awk '
        { a[NR] = $1 }
        END {
            if (NR == 0) { print "NaN"; exit }
            mid = int((NR + 1) / 2)
            if (NR % 2 == 1) { print a[mid] }
            else             { print (a[mid] + a[mid+1]) / 2 }
        }
    '
}

# --- A/B/C loop ---
mkdir -p "${OUT_BASE}"
declare -a A_TIMES B_TIMES C_TIMES

run_config() {
    local cfg="$1"
    local grid="$2"
    local desc="$3"
    local label="$4"
    local -n times_arr="$5"

    [ "$QUIET" -eq 0 ] && echo "Config $cfg (${label}):"
    # Warmup (untimed)
    if ! run_one "$cfg" "$grid" "$desc" 0 >/dev/null; then exit 1; fi
    [ "$QUIET" -eq 0 ] && echo "  warmup: done"

    for r in $(seq 1 "$REPS"); do
        local ms
        if ! ms=$(run_one "$cfg" "$grid" "$desc" "$r"); then exit 1; fi
        times_arr+=("$ms")
        [ "$QUIET" -eq 0 ] && printf '  rep %d: %s ms\n' "$r" "$ms"
    done
    echo
}

run_config A 0 0 "all OFF"          A_TIMES
run_config B 0 1 "descriptors only" B_TIMES
run_config C 1 1 "all ON"           C_TIMES

A_MEDIAN=$(printf '%s\n' "${A_TIMES[@]}" | median)
B_MEDIAN=$(printf '%s\n' "${B_TIMES[@]}" | median)
C_MEDIAN=$(printf '%s\n' "${C_TIMES[@]}" | median)

delta_ms() {
    awk -v hi="$1" -v lo="$2" 'BEGIN { printf "%.0f\n", hi - lo }'
}
delta_pct() {
    # percentage relative to the baseline (lo). awk handles lo==0 by printing "inf".
    awk -v hi="$1" -v lo="$2" 'BEGIN { if (lo == 0) { print "inf" } else { printf "%.1f\n", (hi - lo) / lo * 100 } }'
}

BA_MS=$(delta_ms  "$B_MEDIAN" "$A_MEDIAN")
BA_PCT=$(delta_pct "$B_MEDIAN" "$A_MEDIAN")
CB_MS=$(delta_ms  "$C_MEDIAN" "$B_MEDIAN")
CB_PCT=$(delta_pct "$C_MEDIAN" "$B_MEDIAN")
CA_MS=$(delta_ms  "$C_MEDIAN" "$A_MEDIAN")
CA_PCT=$(delta_pct "$C_MEDIAN" "$A_MEDIAN")

# --- Summary ---
cat <<EOF
============================================================
 Summary

 A (all OFF         ):  ${A_TIMES[*]}  ->  median ${A_MEDIAN} ms
 B (descriptors only):  ${B_TIMES[*]}  ->  median ${B_MEDIAN} ms
 C (all ON          ):  ${C_TIMES[*]}  ->  median ${C_MEDIAN} ms

 Descriptors cost (B-A):  ${BA_MS} ms  (${BA_PCT}%)
 Grid export cost (C-B):  ${CB_MS} ms  (${CB_PCT}%)
 Total feature  (C-A):    ${CA_MS} ms  (${CA_PCT}%)
============================================================
EOF

# --- CSV log ---
if [ -n "$CSV_FILE" ]; then
    if [ ! -f "$CSV_FILE" ]; then
        echo "date_utc,git_rev,p2rank_ver,java_major,host,target,mode,threads,reps,a_median_ms,b_median_ms,c_median_ms,ba_delta_ms,ba_delta_pct,cb_delta_ms,cb_delta_pct,ca_delta_ms,ca_delta_pct" \
            > "$CSV_FILE"
    fi
    echo "${DATE_ISO},${GIT_REV}${GIT_DIRTY},${P2RANK_VER},${JAVA_MAJOR},${HOST},${LABEL},${MODE},${THREADS:-default},${REPS},${A_MEDIAN},${B_MEDIAN},${C_MEDIAN},${BA_MS},${BA_PCT},${CB_MS},${CB_PCT},${CA_MS},${CA_PCT}" \
        >> "$CSV_FILE"
    echo "CSV: appended one row to ${CSV_FILE}"
fi
