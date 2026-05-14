#!/usr/bin/env bash
#
# Drop-in safety benchmark for the -cofactors feature (Issue #79 / R18).
#
# Premise: setting -cofactors to a HETATM that does not exist in any structure of the
# dataset must be a strict no-op. If the byte-level diff between baseline and "with
# never-present cofactor" runs is non-empty, a code path is implicitly different and
# needs investigation before merging.
#
# Usage:  benchmark/cofactors_dropin_safety.sh [dataset.ds]
#
# Default dataset is distro/test_data/concavity.ds (~24 structures - enough for
# byte-equality testing). Pass a larger dataset for stronger signal.

set -e

DATASET="${1:-distro/test_data/concavity.ds}"
OUT="${COFACTORS_BENCH_OUT:-/tmp/cofactor_safety}"

if [[ ! -f "$DATASET" ]]; then
    echo "ERROR: dataset not found: $DATASET"
    exit 1
fi

if [[ ! -x ./distro/prank ]]; then
    echo "ERROR: ./distro/prank not found. Build with: ./gradlew jar copyBinaryToDist copyDependenciesToDist"
    exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"

echo "[1/3] Baseline (no -cofactors) on $DATASET..."
./distro/prank predict "$DATASET" -o "$OUT/baseline" -seed 42 -threads 1 -visualizations 1 >/dev/null

echo "[2/3] With -cofactors ZZZZ (never-present HETATM) on $DATASET..."
./distro/prank predict "$DATASET" -o "$OUT/with_zzzz" -cofactors ZZZZ -seed 42 -threads 1 -visualizations 1 >/dev/null

echo "[3/3] Comparing output files (the actual byte-equality guarantee)..."
# Compare every per-protein output file. Excludes:
#   params.txt    - legitimately captures the -cofactors arg
#   run.log       - timestamps + outdir path differ trivially
#   *.json        - may embed the run config
# Includes all *.csv (predictions, residues, pointscores, sub-pocket, etc.) and *.pml
# (PyMOL scripts - the visualization output that a regression could silently break).
FAIL=0
while IFS= read -r f; do
    rel="${f#./}"
    case "$rel" in
        params.txt|run.log|*.json) continue ;;
    esac
    if [[ ! -f "$OUT/with_zzzz/$rel" ]]; then
        echo "MISSING in with_zzzz: $rel"
        FAIL=1
        continue
    fi
    if ! diff -q "$OUT/baseline/$rel" "$OUT/with_zzzz/$rel" >/dev/null 2>&1; then
        echo "DIFFERS: $rel"
        diff "$OUT/baseline/$rel" "$OUT/with_zzzz/$rel" | head -20
        FAIL=1
    fi
done < <(cd "$OUT/baseline" && find . -type f \( -name "*.csv" -o -name "*.pml" -o -name "*.pdb" -o -name "*.cif" \))

if [[ "$FAIL" -eq 0 ]]; then
    echo "PASS: all per-protein outputs byte-identical between baseline and with-never-present-cofactor runs."
    exit 0
else
    echo "FAIL: at least one output differs. Investigate before merging."
    echo "Output dir: $OUT"
    exit 1
fi
