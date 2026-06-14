#!/usr/bin/env bash
#
# Compare `prank predict` performance across CHOSEN JREs.
#
# Runs the same launcher + protein set under each JRE (via JAVA_HOME), reports a
# wall-time table relative to the fastest, and verifies that predictions are
# identical across all JREs (a JRE that changes results is a correctness bug).
#
# AppCDS archives are JVM-specific, so when benchmarking distro/prank_burst the
# archive (distro/bin/p2rank-appcds.jsa) is removed and rebuilt for each JRE; the
# original is restored on exit.
#
# Usage:
#   ./misc/test-scripts/predict_bench_jres.sh [JRE...] [options]
#
# JRE:
#   One or more of:
#     - an SDKMAN candidate name      e.g. 25.0.2-oracle   21.0.10-oracle
#     - a path to a JAVA_HOME dir      e.g. /usr/lib/jvm/java-21-openjdk-amd64
#   With no JRE args the script lists available SDKMAN candidates and exits.
#
# options:
#   -r, --reps N        timed reps per protein (default 4, plus one untimed warmup)
#   -l, --launcher CMD  launcher to benchmark (default: distro/prank_burst)
#       --proteins "a b c"   space-separated protein paths (default: 5-protein spread)
#       --list          list available SDKMAN candidates and exit
#   -h, --help          show this help
#
# Examples:
#   ./misc/test-scripts/predict_bench_jres.sh 25.0.2-graal 25.0.2-oracle 21.0.10-oracle
#   ./misc/test-scripts/predict_bench_jres.sh 25.0.2-oracle /usr/lib/jvm/java-21-openjdk-amd64
#   ./misc/test-scripts/predict_bench_jres.sh --launcher distro/prank 25.0.2-oracle 25-graal
#   ./misc/test-scripts/predict_bench_jres.sh --list
#
# Notes:
#   - Run `./gradlew assemble` first (launchers use distro/bin/p2rank.jar).
#   - Run on an idle machine; warmup is excluded but absolute numbers still drift
#     with load. The relative ranking is the reliable output.
#

set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1   # repo root
source misc/test-scripts/bench-common.sh

REPS=4
LAUNCHER="distro/prank_burst"
SPECS=()
PROTEINS=("${BENCH_DEFAULT_PROTEINS[@]}")
ARCHIVE="distro/bin/p2rank-appcds.jsa"
OUT=$(mktemp -d /tmp/predict_bench_jres.XXXXXX)

while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--reps)      REPS="$2"; shift 2 ;;
        -l|--launcher)  LAUNCHER="$2"; shift 2 ;;
        --proteins)     read -r -a PROTEINS <<<"$2"; shift 2 ;;
        --list)         bench_list_candidates; exit 0 ;;
        -h|--help)      bench_usage ;;
        -*)             echo "Unknown option: $1" >&2; bench_usage ;;
        *)              SPECS+=("$1"); shift ;;
    esac
done

if [[ ${#SPECS[@]} -eq 0 ]]; then
    echo "No JREs specified." >&2; echo "" >&2
    bench_list_candidates >&2
    echo "" >&2; echo "Pass one or more, e.g.:  $0 25.0.2-oracle 21.0.10-oracle" >&2
    exit 1
fi

# Preserve any existing AppCDS archive, restore on exit
SAVED_ARCHIVE=""
if [[ -f "$ARCHIVE" ]]; then SAVED_ARCHIVE="$OUT/saved.jsa"; cp "$ARCHIVE" "$SAVED_ARCHIVE"; fi
cleanup() {
    rm -f "$ARCHIVE"
    [[ -n "$SAVED_ARCHIVE" ]] && cp "$SAVED_ARCHIVE" "$ARCHIVE"
    rm -rf "$OUT"
}
trap cleanup EXIT

echo "Launcher: $LAUNCHER    reps: $REPS    proteins: ${#PROTEINS[@]}"
echo ""

# Benchmark one JRE; echoes average wall seconds (or FAIL)
bench_jre() {
    local home="$1"; local tag="$2"
    export JAVA_HOME="$home"
    # faster launcher: each JRE needs its own AppCDS archive
    [[ "$LAUNCHER" == *prank_burst ]] && rm -f "$ARCHIVE"
    # warmup (also builds the archive)
    if ! $LAUNCHER predict -f "${PROTEINS[0]}" -o "$OUT/$tag" >/dev/null 2>&1; then echo FAIL; return; fi
    local total=0; local count=0
    for f in "${PROTEINS[@]}"; do
        [[ -f "$f" ]] || continue
        local t=0
        for ((i=0;i<REPS;i++)); do
            local s e; s=$(date +%s.%N)
            $LAUNCHER predict -f "$f" -o "$OUT/$tag" >/dev/null 2>&1
            e=$(date +%s.%N); t=$(echo "$t + $e - $s" | bc)
        done
        total=$(echo "$total + $t/$REPS" | bc -l); count=$((count+1))
    done
    echo "scale=3; $total/$count" | bc
}

declare -a LABELS HOMES AVGS
i=0
for spec in "${SPECS[@]}"; do
    home=$(bench_resolve_home "$spec")
    if [[ -z "$home" ]]; then printf "  %-20s UNRESOLVED (skipped)\n" "$spec" >&2; continue; fi
    label=$(bench_label_of "$home")
    printf ">> %-22s %s\n" "$spec" "$("$home/bin/java" -version 2>&1 | head -1)" >&2
    avg=$(bench_jre "$home" "jre$i")
    LABELS[i]="$label ($spec)"; HOMES[i]="$home"; AVGS[i]="$avg"
    printf "   avg wall: %s\n" "$avg" >&2
    i=$((i+1))
done

n=$i
[[ $n -eq 0 ]] && { echo "No JREs ran." >&2; exit 1; }

# Find fastest for relative column
fastest=""
for ((j=0;j<n;j++)); do
    a="${AVGS[j]}"; [[ "$a" == FAIL ]] && continue
    if [[ -z "$fastest" ]] || (( $(echo "$a < $fastest" | bc -l) )); then fastest="$a"; fi
done

echo ""
echo "================ RESULTS (avg wall, ${REPS} reps over ${#PROTEINS[@]} proteins) ================"
printf "%-40s %10s %10s\n" "JRE" "avg(s)" "vs best"
for ((j=0;j<n;j++)); do
    a="${AVGS[j]}"
    if [[ "$a" == FAIL ]]; then printf "%-40s %10s\n" "${LABELS[j]}" "FAIL"; continue; fi
    rel=$(awk "BEGIN{printf \"%.1f\", ($a/$fastest - 1)*100}")
    printf "%-40s %10s %9s%%\n" "${LABELS[j]}" "$a" "+$rel"
done

# --- correctness: predictions identical across JREs ------------------------------
echo ""
echo "---- prediction consistency (vs first JRE) ----"
ref="$OUT/jre0/$(basename "${PROTEINS[0]}")_predictions.csv"
allok=1
for ((j=0;j<n;j++)); do
    cur="$OUT/jre$j/$(basename "${PROTEINS[0]}")_predictions.csv"
    if [[ ! -f "$cur" ]]; then printf "  %-40s NO OUTPUT\n" "${LABELS[j]}"; allok=0; continue; fi
    if diff -q <(grep -v '^#' "$ref" 2>/dev/null) <(grep -v '^#' "$cur" 2>/dev/null) >/dev/null; then
        printf "  %-40s identical\n" "${LABELS[j]}"
    else
        printf "  %-40s DIFFERS\n" "${LABELS[j]}"; allok=0
    fi
done
[[ $allok -eq 1 ]] && echo "All JREs produced identical predictions." || echo "WARNING: prediction mismatch across JREs."
