#!/usr/bin/env bash
#
# Detailed startup + runtime PHASE BREAKDOWN for a single `prank predict` run on the
# CURRENT JRE (whatever `java` / $JAVA_HOME resolves to).
#
# Decomposes wall time into JVM boot / class-loading / Groovy config compile / model
# load / PDB parse / surface / features / prediction / output, by combining:
#   (1) bare `java -version`        baseline JVM boot
#   (2) -Xlog:startuptime           JVM-internal "Create VM" cost
#   (3) a full per-line timestamped run (perl Time::HiRes) for phase boundaries
#   (4) the phase table derived from (2)+(3)
#   (5) -Xlog:class+load + uptime   class-load census, bucketed by phase window
#   (6) du/find on the model dir
#   (7) the raw milestone timeline as evidence
#
# With --deep it adds an expanded set of probes:
#   (8)  classpath composition (bin/lib/*)
#   (9)  class-load histogram in 0.25s bins (dominant package per bin)
#   (10) first class-load time per major subsystem (Groovy/Log4j2/BioJava/weka/...)
#   (11) model-load split: zstd-decompress wall vs ObjectInputStream deserialize
#   (12) cross-cutting JIT compiler CPU (-XX:+CITime) and GC (-Xlog:gc) over the run
#
# This is a DIAGNOSTIC companion to `predict_bench.sh --phases` (which prints only the
# grep-filtered milestone timeline). It is the reproducible form of the manual analysis
# in documentation/dev/jvm-performance-tuning.md.
#
# IMPORTANT: numbers come from ONE cold run and drift run-to-run with machine load. The
# sub-phase PROPORTIONS are the stable signal, not the absolute seconds. Re-run a few
# times, or average, before drawing fine conclusions.
#
# Usage:
#   ./misc/test-scripts/predict_breakdown.sh [protein] [-l launcher]
#
#   protein    .pdb/.cif path (default: a small protein from distro/test_data)
#   -l CMD     launcher (default: distro/prank; use distro/prank_burst to see CDS/C1 effect)
#   --deep     also run the expanded probes (8)-(12) above (slower: extra JVM runs)
#   -h         show this help
#
# Notes:
#   - Run `./gradlew assemble` first (launchers use distro/bin/p2rank.jar).
#   - Run on an idle machine. A small protein makes the fixed overhead easiest to read.
#   - Linux / GNU coreutils only: uses GNU-specific `stat -c`, `find -printf`,
#     `/usr/bin/time -f`, and `stdbuf`. The BSD/macOS equivalents differ.
#

set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1   # repo root
source misc/test-scripts/bench-common.sh

PROTEIN="${BENCH_DEFAULT_PROTEINS[0]}"
LAUNCHER="distro/prank"
DEEP=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        -l|--launcher) LAUNCHER="$2"; shift 2 ;;
        --deep)        DEEP=1; shift ;;
        -h|--help)     bench_usage ;;
        -*)            echo "Unknown option: $1" >&2; exit 1 ;;
        *)             PROTEIN="$1"; shift ;;
    esac
done

[[ -f "$PROTEIN" ]] || { echo "protein not found: $PROTEIN" >&2; exit 1; }

JAVACMD="$(bench_javacmd)"
OUT=$(mktemp -d /tmp/predict_breakdown.XXXXXX)
trap 'rm -rf "$OUT"' EXIT

echo "JRE:      $("$JAVACMD" -version 2>&1 | head -1)"
echo "Launcher: $LAUNCHER"
echo "Protein:  $PROTEIN ($(( $(stat -c%s "$PROTEIN") / 1024 )) KB)"
echo ""

# warmup: fill OS cache and (for the faster launcher) build the AppCDS archive so the
# measured run reflects steady state, not first-touch disk / archive creation.
$LAUNCHER predict -f "$PROTEIN" -o "$OUT/warm" >/dev/null 2>&1

# ---- (1) bare JVM boot baseline -------------------------------------------------
echo "== (1) bare JVM boot =="
for i in 1 2; do
    t=$( { /usr/bin/time -f "%e" "$JAVACMD" -version ; } 2>&1 | tail -1 )
    echo "   java -version: ${t}s"
done

# ---- (2) JVM-internal Create VM -------------------------------------------------
echo "== (2) JVM Create VM (-Xlog:startuptime) =="
JAVA_OPTS="-Xlog:startuptime" $LAUNCHER predict -f "$PROTEIN" -o "$OUT/su" 2>&1 \
    | grep -iE "Genesis|Initialize module system|Create VM" | sed 's/^/   /'

# ---- (3) full per-line timestamped run (captured for boundary extraction) --------
TS="$OUT/timeline.txt"
stdbuf -oL -eL $LAUNCHER predict -f "$PROTEIN" -o "$OUT/tl" 2>&1 \
    | bench_ts_prefix $'%8.3f\t%s' > "$TS"

# first wall timestamp whose line matches an (extended) regex; empty if no match
tsof() { awk -v re="$1" '$0 ~ re {print $1; exit}' "$TS"; }

T_banner=$(tsof "P2Rank ")
T_config=$(tsof "loading default config")
T_dsann=$(tsof "predicting pockets for proteins")
T_model=$(tsof "Loading model from directory")
T_feat=$(tsof "effectively enabled features")
T_loadp=$(tsof "loading protein")
T_struct=$(tsof "structure atoms")
T_sas=$(tsof "SAS points:")
T_pred=$(tsof "PREDICTING POCKETS")
T_pock=$(tsof "pocket 1 -")
T_pfin=$(tsof "predicting pockets finished")
T_fin=$(tsof "Finished successfully")

# ---- (4) phase breakdown table --------------------------------------------------
echo "== (4) PHASE BREAKDOWN (approx, one cold run) =="
awk -v banner="$T_banner" -v config="$T_config" -v ds="$T_dsann" -v model="$T_model" \
    -v feat="$T_feat" -v loadp="$T_loadp" -v struct="$T_struct" -v sas="$T_sas" \
    -v pred="$T_pred" -v pock="$T_pock" -v pfin="$T_pfin" -v fin="$T_fin" '
function row(name, a, b,   d) {
    if (a == "" || b == "") return
    d = b - a
    printf "   %-36s %8.3f s  %6.1f%%\n", name, d, (total > 0 ? d / total * 100 : 0)
}
BEGIN {
    total = fin + 0
    printf "   %-36s %10s  %7s\n", "phase", "secs", "share"
    row("A. startup (JVM + classload + Groovy)", 0,     config)
    row("   A. of which: -> banner",            0,     banner)
    row("B. config parse (Groovy compile)",     config, ds)
    row("C. model load (zstd + deserialize)",   model,  feat)
    row("D1. PDB parse (BioJava)",              loadp,  struct)
    row("D2. surface (SAS points)",            struct, sas)
    row("D3. feature extraction",              sas,    pred)
    row("E. prediction (cluster + RF score)",  pred,   pock)
    row("F. output + visualization",           pock,   pfin)
    printf "   %-36s %8.3f s  %6.1f%%\n", "TOTAL", total, 100.0
}'

# ---- (5) class-load census ------------------------------------------------------
echo "== (5) class-load census (bucketed by phase window) =="
CL="$OUT/classload.txt"
JAVA_OPTS="-Xlog:class+load=info:file=$CL:uptime,tags" \
    $LAUNCHER predict -f "$PROTEIN" -o "$OUT/cl" >/dev/null 2>&1
echo "   total classes loaded: $(wc -l < "$CL")"
awk -v config="$T_config" -v ds="$T_dsann" -v feat="$T_feat" -F'[][]' '
    { u = $2 + 0
      if      (u < config) b = "A. startup"
      else if (u < ds)     b = "B. config parse"
      else if (u < feat)   b = "C. model load"
      else                 b = "D-F. work"
      c[b]++ }
    END { for (k in c) printf "   %-18s %5d\n", k, c[k] }' "$CL" | sort
echo "   -- top packages --"
grep -oE 'class,load\] [a-zA-Z0-9_.$]+' "$CL" | sed 's/class,load\] //' \
    | awk -F. '{ if ($1=="org"||$1=="com"||$1=="cz") print $1"."$2; else print $1 }' \
    | sort | uniq -c | sort -rn | head -12 | sed 's/^/   /'

# ---- (6) model facts ------------------------------------------------------------
echo "== (6) model directory =="
du -sh distro/models/default 2>/dev/null | sed 's/^/   /'
find distro/models/default -type f -printf '   %10s  %p\n' 2>/dev/null

# ---- (7) raw milestone timeline (evidence) --------------------------------------
echo "== (7) milestone timeline (cumulative wall seconds) =="
grep -iE "P2Rank |default config|predicting pockets for|Loading model|enabled features|processing dataset|loading protein|loading file|structure atoms|ignoring ligands|SAS points|exposed protein|PREDICTING POCKETS|pocket 1 -|predicting pockets finished|results saved|Finished successfully" "$TS" \
    | sed 's/^/   /'

[[ $DEEP -eq 1 ]] || exit 0

# ================================ DEEP probes ====================================
# Extra detail behind --deep. Reuses the class-load capture from section (5) for the
# histogram and subsystem first-load; runs extra JVMs for the zstd split and JIT/GC.

# ---- (8) classpath composition --------------------------------------------------
echo "== (8) classpath composition (bin/lib/*) =="
echo "   jars: $(ls distro/bin/lib/*.jar 2>/dev/null | wc -l)   total: $(du -sh distro/bin/lib 2>/dev/null | cut -f1)"
ls -S distro/bin/lib/*.jar 2>/dev/null | head -6 | while read -r j; do
    printf "   %6sK  %s\n" "$(( $(stat -c%s "$j") / 1024 ))" "$(basename "$j")"
done

# ---- (9) class-load histogram (0.25s bins, dominant package) --------------------
echo "== (9) class-load histogram (0.25s bins; dominant package per bin) =="
awk -F'[][]' '{ u=$2+0; c=$0; sub(/.*class,load\] /,"",c); sub(/ .*/,"",c)
    split(c,a,"."); pk=(a[1]=="org"||a[1]=="com"||a[1]=="cz") ? a[1]"."a[2] : a[1]
    b=int(u/0.25)*0.25; n[b]++; key=b SUBSEP pk; pc[key]++ }
  END { for (bb in n) { best=""; bc=0
          for (k in pc) { split(k,kk,SUBSEP); if (kk[1]==bb && pc[k]>bc) { bc=pc[k]; best=kk[2] } }
          printf "   %6.2f-%4.2fs  %5d classes   (top: %s %d)\n", bb, bb+0.25, n[bb], best, bc } }' \
    "$CL" | sort -n

# ---- (10) first class-load per subsystem ----------------------------------------
echo "== (10) first class-load per subsystem =="
for pat in "org.apache.logging:log4j2" "org.codehaus.groovy:groovy-compiler" \
           "groovy.:groovy-runtime" "org.biojava:biojava" "org.rcsb:rcsb-cif" \
           "com.fasterxml:jackson" "com.google:guava" "weka.:weka" \
           "cz.cuni:surface" "cz.siret:p2rank"; do
    pfx="${pat%%:*}"; lbl="${pat##*:}"
    t=$(awk -F'[][]' -v p="$pfx" '{c=$0; sub(/.*class,load\] /,"",c); if (index(c,p)==1) {print $2; exit}}' "$CL")
    printf "   %-16s %s\n" "$lbl" "${t:-(never)}"
done

# ---- (11) model-load split: zstd-decompress vs deserialize ----------------------
echo "== (11) model-load split: zstd-decompress vs deserialize =="
M=distro/models/default/model.zst
if [[ -f "$M" ]] && command -v zstd >/dev/null 2>&1; then
    zt=$( { /usr/bin/time -f "%e" sh -c "zstd -dc '$M' > /dev/null" ; } 2>&1 | tail -1 )
    echo "   zstd -dc wall: ${zt}s  (remainder of phase C is ObjectInputStream deserialize)"
else
    echo "   (zstd CLI not installed or model.zst missing; skipping)"
fi

# ---- (12) cross-cutting: JIT compiler CPU + GC ----------------------------------
echo "== (12) cross-cutting JIT + GC (overlap all phases, background threads) =="
GCL="$OUT/gc.log"
citout=$(JAVA_OPTS="-XX:+CITime -Xlog:gc:file=$GCL" \
    $LAUNCHER predict -f "$PROTEIN" -o "$OUT/cit" 2>&1)
# stock prints per-tier C1/C2 blocks; C1-only prints a single "Total compilation time"
echo "$citout" | grep -oE 'C[12] \{[^}]*standard:[^;]*' | sed 's/^/   /'
echo "$citout" | grep -iE 'Total compilation time' | sed 's/^[[:space:]]*/   /'
gcsum=$(awk -F'[() ]+' '/Pause/ { for (i=1;i<=NF;i++) if ($i ~ /ms$/) g=$i; sub(/ms/,"",g); s+=g; n++ }
    END { printf "%.0f ms across %d GCs", s, n }' "$GCL" 2>/dev/null)
echo "   GC: ${gcsum:-n/a}"
