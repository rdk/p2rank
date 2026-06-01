#!/usr/bin/env bash
#
# JVM tuning matrix for `prank predict`: benchmarks every combination of
#   CDS  {off, appcds[, aot]}  x  JIT {tiered, c1}  x  GC {g1, parallel, serial}
# across one or more JVMs, then reports the best config per JVM, the effect of each
# axis, and a global ranking. Heap is fixed at 2g (keeps compressed oops + the base
# CDS archive enabled). Predictions are byte-identical across all configs (CDS/JIT/GC
# never change results), so this measures pure wall-time.
#
# Extending with a new JVM is just another argument - pass its SDKMAN name or a path
# to its JAVA_HOME. The script auto-detects per-JVM capabilities (the Java 23+
# --sun-misc-unsafe flag, and the Java 25+ one-step AOT cache) and skips configs a JVM can't run.
#
# Usage:
#   ./misc/test-scripts/predict_bench_matrix.sh [JRE...] [options]
#
# JRE:
#   One or more of:  an SDKMAN candidate name (e.g. 25.0.2-oracle, 26.0.1-oracle)
#                    or a path to a JAVA_HOME dir (e.g. /usr/lib/jvm/java-21-openjdk-amd64)
#   With no JRE args the script benchmarks only the current `java` / $JAVA_HOME.
#
# options:
#   -r, --reps N         timed reps per protein (default 3, plus one untimed warmup)
#       --proteins "..." space-separated protein paths (default: 3-protein size spread)
#       --cds   LIST     comma list from {off,appcds,aot}   (default: off,appcds)
#       --jit   LIST     comma list from {tiered,c1}        (default: tiered,c1)
#       --gc    LIST     comma list from {g1,parallel,serial} (default: g1,parallel,serial)
#       --aot            shorthand for adding 'aot' to --cds (JDK 25+ JVMs only; uses one-step -XX:AOTCacheOutput)
#       --csv FILE       also write the raw rows to FILE (default: a temp file, path printed)
#       --list           list available SDKMAN candidates and exit
#   -h, --help           show this help
#
# Examples:
#   # full default matrix (off/appcds x tiered/c1 x g1/parallel/serial) on 4 JVMs
#   ./misc/test-scripts/predict_bench_matrix.sh 25.0.2-graal 25.0.2-oracle 21.0.10-oracle 17.0.12-oracle
#
#   # add a freshly installed JVM and include the AOT cache where supported
#   ./misc/test-scripts/predict_bench_matrix.sh --aot 26.0.1-oracle 25.0.2-oracle
#
#   # quick scan: just CDS on/off, C1 only, ParallelGC, on the current JVM
#   ./misc/test-scripts/predict_bench_matrix.sh --jit c1 --gc parallel
#
# Reproducibility notes:
#   - Run `./gradlew assemble` first (uses distro/bin/p2rank.jar, not test classes).
#   - Run on an idle machine. Cost = N_jvm x (|cds|*|jit|*|gc|) configs x N_proteins x
#     (reps+1) JVM launches; the default 4-JVM full matrix is ~480 launches (~12-15 min).
#   - Each config gets its OWN archive (built with matching flags) to avoid CDS/AOT
#     mismatch; warmup is excluded from timings.
#

set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1   # repo root
source misc/test-scripts/bench-common.sh

CP="distro/bin/p2rank.jar:distro/bin/lib/*"
REPS=3
CDS_MODES="off,appcds"
JIT_MODES="tiered,c1"
GC_MODES="g1,parallel,serial"
SPECS=()
PROTEINS=(
    distro/test_data/clean/1t7qa.pdb
    distro/test_data/1fbl.pdb
    distro/test_data/1AHP.pdb
)
OUT=$(mktemp -d /tmp/predict_matrix.XXXXXX)
CSV=$(mktemp /tmp/predict_matrix_results.XXXXXX.csv)   # persists after run; survives $OUT cleanup
trap 'rm -rf "$OUT"' EXIT

while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--reps)    REPS="$2"; shift 2 ;;
        --proteins)   read -r -a PROTEINS <<<"$2"; shift 2 ;;
        --cds)        CDS_MODES="$2"; shift 2 ;;
        --jit)        JIT_MODES="$2"; shift 2 ;;
        --gc)         GC_MODES="$2"; shift 2 ;;
        --aot)        case ",$CDS_MODES," in *,aot,*) ;; *) CDS_MODES="$CDS_MODES,aot" ;; esac; shift ;;
        --csv)        CSV="$2"; shift 2 ;;
        --list)       bench_list_candidates; exit 0 ;;
        -h|--help)    bench_usage ;;
        -*)           echo "Unknown option: $1" >&2; bench_usage ;;
        *)            SPECS+=("$1"); shift ;;
    esac
done

# Default to the current JVM if none specified
[[ ${#SPECS[@]} -eq 0 ]] && SPECS=("${JAVA_HOME:-$(dirname "$(dirname "$(command -v java)")")}")

IFS=',' read -r -a CDS_ARR <<<"$CDS_MODES"
IFS=',' read -r -a JIT_ARR <<<"$JIT_MODES"
IFS=',' read -r -a GC_ARR  <<<"$GC_MODES"

echo "jvm,cds,jit,gc,avg_wall_s" > "$CSV"

# base flags common to all configs; $1 = java major version
base_flags() {
    local maj="$1"
    local f="--add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
    f="$f --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"
    f="$f -Xmx2048m -Xlog:cds=off"   # NB: -Xlog:aot=off only valid on JDK 24+ (added in the aot branch)
    [[ "$maj" -ge 23 ]] 2>/dev/null && f="$f --sun-misc-unsafe-memory-access=allow"
    echo "$f"
}

# run one prediction; FLAGS env carries the JVM flags
run1() { "$1" $FLAGS -cp "$CP" cz.siret.prank.program.Main predict -f "$2" -o "$OUT/o" >/dev/null 2>&1; }

# average wall over the protein set for the current FLAGS
time_avg() {
    local J="$1"; local total=0 n=0
    for p in "${PROTEINS[@]}"; do
        [[ -f "$p" ]] || continue
        local t=0
        for ((i=0;i<REPS;i++)); do
            local s e; s=$(date +%s.%N); run1 "$J" "$p"; e=$(date +%s.%N)
            t=$(awk "BEGIN{print $t+$e-$s}")
        done
        total=$(awk "BEGIN{print $total+$t/$REPS}"); n=$((n+1))
    done
    awk "BEGIN{printf \"%.3f\", $total/$n}"
}

for spec in "${SPECS[@]}"; do
    home=$(bench_resolve_home "$spec")
    [[ -z "$home" ]] && { echo "  $spec: UNRESOLVED (skipped)" >&2; continue; }
    J="$home/bin/java"; maj=$(bench_major_of "$home"); lbl=$(bench_label_of "$home")
    BASE=$(base_flags "$maj")
    echo ">> $lbl  ($spec, JDK major $maj)" >&2
    for jit in "${JIT_ARR[@]}"; do
        jitf=""; [[ "$jit" == c1 ]] && jitf="-XX:TieredStopAtLevel=1"
        for gc in "${GC_ARR[@]}"; do
            gcf="-XX:+UseG1GC"; [[ "$gc" == parallel ]] && gcf="-XX:+UseParallelGC"; [[ "$gc" == serial ]] && gcf="-XX:+UseSerialGC"
            for cds in "${CDS_ARR[@]}"; do
                local_arch="$OUT/${spec//\//_}_${jit}_${gc}_${cds}"
                case "$cds" in
                    off)
                        FLAGS="$BASE $jitf $gcf"; run1 "$J" "${PROTEINS[0]}" ;;       # warmup
                    appcds)
                        rm -f "$local_arch.jsa"
                        FLAGS="$BASE $jitf $gcf -XX:ArchiveClassesAtExit=$local_arch.jsa"; run1 "$J" "${PROTEINS[0]}"
                        FLAGS="$BASE $jitf $gcf -XX:SharedArchiveFile=$local_arch.jsa" ;;
                    aot)
                        if [[ "$maj" -lt 25 ]] 2>/dev/null; then echo "   (skip aot: JDK $maj < 25, one-step -XX:AOTCacheOutput needs JDK 25+)" >&2; continue; fi
                        rm -f "$local_arch.aot"
                        FLAGS="$BASE -Xlog:aot=off $jitf $gcf -XX:AOTCacheOutput=$local_arch.aot"; run1 "$J" "${PROTEINS[0]}"
                        FLAGS="$BASE -Xlog:aot=off $jitf $gcf -XX:AOTCache=$local_arch.aot" ;;
                    *) echo "   (unknown cds mode: $cds)" >&2; continue ;;
                esac
                avg=$(time_avg "$J")
                echo "$lbl|$spec,$cds,$jit,$gc,$avg" >> "$CSV"
                printf "   %-8s %-7s %-9s %ss\n" "$cds" "$jit" "$gc" "$avg" >&2
            done
        done
    done
done

# ---------------------------------------------------------------- analysis
D=$(tail -n +2 "$CSV")
[[ -z "$D" ]] && { echo "No results." >&2; exit 1; }

echo ""
echo "================== BEST CONFIG PER JVM =================="
echo "$D" | sort -t, -k5 -n | awk -F, '!seen[$1]++{split($1,a,"|"); printf "  %-26s %-7s %-7s %-9s %ss\n", a[1], $2, $3, $4, $5}'

echo ""
echo "================== AXIS EFFECTS (mean wall) =================="
echo "-- CDS (over all configs):"
echo "$D" | awk -F, '{s[$2]+=$5;n[$2]++} END{for(k in s)printf "   %-8s %.3fs\n",k,s[k]/n[k]}' | sort -k2 -n
echo "-- JIT (per JVM, c1 vs tiered):"
echo "$D" | awk -F, '{split($1,a,"|"); k=a[1]"|"$3; s[k]+=$5;n[k]++} END{for(k in s){split(k,p,"|"); printf "   %-26s %-7s %.3fs\n",p[1],p[2],s[k]/n[k]}}' | sort
echo "-- GC (over all configs):"
echo "$D" | awk -F, '{s[$4]+=$5;n[$4]++} END{for(k in s)printf "   %-9s %.3fs\n",k,s[k]/n[k]}' | sort -k2 -n

echo ""
echo "================== GLOBAL RANKING (top 10) =================="
echo "$D" | sort -t, -k5 -n | head -10 | awk -F, '{split($1,a,"|"); printf "  %-26s %-7s %-7s %-9s %ss\n", a[1], $2, $3, $4, $5}'

echo ""
echo "raw CSV: $CSV"
