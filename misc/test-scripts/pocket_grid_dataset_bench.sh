#!/usr/bin/env bash
#
# End-to-end benchmark of the pocket-grid + descriptors output pipeline on a
# rescore-style dataset.
#
# Uses `prank rescore` with `-bench_skip_rescoring 1` so the ML rescorer is a
# no-op; what's measured is the path users actually care about: load protein +
# load pockets + build grid + compute descriptors + write outputs.
#
# Pair this with `./prank.sh bench pocket_grid <ds>` for the single-threaded
# pure-build measurement (no writers).
#
# Usage:
#   ./misc/test-scripts/pocket_grid_dataset_bench.sh [dataset] [extra prank args...]
#
# Examples:
#   ./misc/test-scripts/pocket_grid_dataset_bench.sh                        # coach420-fpocket, default threads
#   ./misc/test-scripts/pocket_grid_dataset_bench.sh holo4k-fpocket
#   ./misc/test-scripts/pocket_grid_dataset_bench.sh coach420-fpocket -threads 8
#   ./misc/test-scripts/pocket_grid_dataset_bench.sh coach420-fpocket -pocket_grid_format parquet

set -u

DATASET="${1:-coach420-fpocket}"
shift || true
EXTRA_ARGS=("$@")

# Strip trailing .ds if the user supplied it; prank.sh accepts either form.
DATASET="${DATASET%.ds}"

OUT_SUBDIR="TEST/POCKET_GRID_BENCH/${DATASET}"

echo "============================================================"
echo " pocket-grid dataset bench"
echo "   dataset:       ${DATASET}.ds"
echo "   out_subdir:    ${OUT_SUBDIR}"
echo "   extra args:    ${EXTRA_ARGS[*]:-(none)}"
echo "   skip rescore:  1 (ML pass-through)"
echo "   grid:          1"
echo "   descriptors:   1"
echo "   visualizations: 0"
echo "============================================================"

time ./prank.sh rescore "${DATASET}.ds" \
    -c config/test-default \
    -bench_skip_rescoring 1 \
    -export_pocket_grid 1 \
    -export_pocket_descriptors 1 \
    -visualizations 0 \
    -out_subdir "${OUT_SUBDIR}" \
    "${EXTRA_ARGS[@]}"
