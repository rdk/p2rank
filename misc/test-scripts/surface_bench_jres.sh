#!/usr/bin/env bash
#
# Benchmark `prank analyze surface-strategies` across CHOSEN JVMs and threading modes.
#
# Runs the surface-strategy benchmark (all SurfaceStrategy enum values: cdk, faster,
# packed, faster_distinct, packed_distinct, packed_distinct_v2, packed_distinct_v3, float_distinct) on one
# dataset, under every (JVM x threads) combination, and prints a combined summary
# (wall_s, surf_median_ms, avg_points, reduction%, Matoms/s) plus the per-run CSVs.
#
# Uses ./prank.sh (local-env big heap + full JIT), NOT prank_burst: this is a heavy
# compute benchmark that pre-loads all proteins, so it wants the big heap and the
# JVM's real top-tier JIT (C2 on stock HotSpot, Graal on GraalVM, per the chosen JVM).
#
# local-env.sh hardcodes GraalVM-oriented flags (-XX:+EagerJVMCI, -XX:+UseCompactObjectHeaders)
# that are "experimental" on stock HotSpot and would abort startup; the script pre-sets
# JAVA_OPTS=-XX:+UnlockExperimentalVMOptions so they are accepted on any JVM (it does not
# change the compiler: Oracle/stock HotSpot still uses C2, GraalVM still uses Graal).
#
# Usage:
#   ./misc/test-scripts/surface_bench_jres.sh -d DATASET -j "JVM..." [options]
#
# Required:
#   -d, --dataset DS     dataset to benchmark (a .ds name resolved via the config's
#                        dataset_base_dir, or a path to a .ds / structure file)
#   -j, --jvms "..."     space- or comma-separated list of JVMs, each either:
#                          - an SDKMAN candidate name   e.g. 25.0.2-graal 26.0.1-oracle
#                          - a path to a JAVA_HOME dir   e.g. ~/.sdkman/candidates/java/25.0.2-graal
#
# Options:
#   -t, --threads "..."  space/comma list of thread counts (default: "1 16")
#   -T, --tessellation N tessellation level override (default: from --config)
#   -c, --config CFG     p2rank config (default: config/test-default; sets dataset_base_dir,
#                        solvent_radius, tessellation). Pass "" to use built-in defaults.
#   -o, --out DIR        output base dir (default: a fresh /tmp/surface_bench_jres.XXXX)
#       --list           list available SDKMAN candidates and exit
#   -h, --help           show this help
#
# Examples:
#   ./misc/test-scripts/surface_bench_jres.sh -d holo4k.ds -j "25.0.2-graal 26.0.1-oracle"
#   ./misc/test-scripts/surface_bench_jres.sh -d coach420.ds -j "25.0.2-graal 26.ea.13-graal" -t "1 8 16" -T 4
#   ./misc/test-scripts/surface_bench_jres.sh -d fptrain.ds -j "/opt/jdk-26" --out /tmp/sb
#
# Notes:
#   - Run `./gradlew assemble` first (./prank.sh uses distro/bin/p2rank.jar).
#   - Run on an idle machine; the relative ranking across JVMs/threads is the reliable output.
#   - Equality + density are reported per run in each run's surface_strategies.csv / surface_equality.csv.
#

set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1   # repo root
source misc/test-scripts/bench-common.sh

DATASET=""
JVMS_RAW=""
THREADS_RAW="1 16"
TESS=""
CONFIG="config/test-default"
OUT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--dataset)       DATASET="$2"; shift 2 ;;
        -j|--jvms)          JVMS_RAW="$2"; shift 2 ;;
        -t|--threads)       THREADS_RAW="$2"; shift 2 ;;
        -T|--tessellation)  TESS="$2"; shift 2 ;;
        -c|--config)        CONFIG="$2"; shift 2 ;;
        -o|--out)           OUT="$2"; shift 2 ;;
        --list)             bench_list_candidates; exit 0 ;;
        -h|--help)          bench_usage ;;
        -*)                 echo "Unknown option: $1" >&2; bench_usage ;;
        *)                  echo "Unexpected argument: $1" >&2; bench_usage ;;
    esac
done

[[ -z "$DATASET" ]]  && { echo "ERROR: -d/--dataset is required." >&2; echo "" >&2; bench_usage; }
[[ -z "$JVMS_RAW" ]] && { echo "ERROR: -j/--jvms is required." >&2; echo "" >&2; bench_list_candidates >&2; exit 1; }
[[ -f distro/bin/p2rank.jar ]] || { echo "ERROR: distro/bin/p2rank.jar missing -- run ./gradlew assemble first." >&2; exit 1; }

# normalize comma -> space, split into arrays
read -r -a JVM_SPECS <<<"${JVMS_RAW//,/ }"
read -r -a THREAD_LIST <<<"${THREADS_RAW//,/ }"

[[ -z "$OUT" ]] && OUT=$(mktemp -d /tmp/surface_bench_jres.XXXXXX)
mkdir -p "$OUT"
SUMMARY="$OUT/summary.csv"
echo "jvm_spec,java_version,threads,tessellation,strategy,wall_s,surf_median_ms,avg_points,sparsify_reduction_pct,Matoms_per_s" > "$SUMMARY"

CONFIG_ARG=(); [[ -n "$CONFIG" ]] && CONFIG_ARG=(-c "$CONFIG")
TESS_ARG=();   [[ -n "$TESS"   ]] && TESS_ARG=(-tessellation "$TESS")
TESS_LABEL="${TESS:-cfg}"

echo "dataset:      $DATASET"
echo "jvms:         ${JVM_SPECS[*]}"
echo "threads:      ${THREAD_LIST[*]}"
echo "tessellation: ${TESS:-(from $CONFIG)}"
echo "config:       ${CONFIG:-(built-in defaults)}"
echo "output:       $OUT"
echo ""

run_one() {  # home, jvm_label, jvm_spec, threads, tag
    local home="$1" label="$2" spec="$3" threads="$4" tag="$5"
    local rundir="$OUT/$tag"
    echo ">> $spec ($label)  threads=$threads  tess=$TESS_LABEL" >&2
    # UnlockExperimentalVMOptions must precede local-env's EagerJVMCI/CompactObjectHeaders;
    # prank.sh appends local-env flags after incoming JAVA_OPTS, so this ordering holds.
    JAVA_HOME="$home" JAVA_OPTS="-XX:+UnlockExperimentalVMOptions" \
        ./prank.sh analyze surface-strategies "$DATASET" \
        "${CONFIG_ARG[@]}" -threads "$threads" "${TESS_ARG[@]}" \
        -cache_datasets 0 -o "$rundir" > "$rundir.log" 2>&1
    local rc=$?
    local csv
    csv=$(find "$rundir" -name surface_strategies.csv 2>/dev/null | head -1)
    if [[ $rc -ne 0 || -z "$csv" ]]; then
        echo "   FAILED (exit $rc) -- see $rundir.log" >&2
        return 1
    fi
    # append rows: strategy[1] wall_s[4] surf_median_ms[8] avg_points[12] reduce[14] Matoms[15]
    awk -F, -v jv="$spec" -v ver="$label" -v th="$threads" -v te="$TESS_LABEL" \
        'NR>1{print jv","ver","th","te","$1","$4","$8","$12","$14","$15}' "$csv" >> "$SUMMARY"
    echo "   ok -> $csv" >&2
}

for spec in "${JVM_SPECS[@]}"; do
    home=$(bench_resolve_home "$spec")
    if [[ -z "$home" ]]; then printf "  %-24s UNRESOLVED (skipped)\n" "$spec" >&2; continue; fi
    label=$(bench_label_of "$home")
    for th in "${THREAD_LIST[@]}"; do
        tag="${spec//\//_}_t${th}_tess${TESS_LABEL}"
        run_one "$home" "$label" "$spec" "$th" "$tag"
    done
done

echo ""
echo "================ SUMMARY (dataset=$DATASET, tess=$TESS_LABEL) ================"
if command -v column >/dev/null 2>&1; then
    column -s, -t "$SUMMARY"
else
    cat "$SUMMARY"
fi
echo ""
echo "summary CSV: $SUMMARY"
echo "per-run dirs + logs under: $OUT"
