#!/usr/bin/env bash
#
# Quickly evaluate a list of new feature calculators by adding them to the
# default feature set individually AND all together, via a single `prank
# ploop` invocation. P2Rank runs train + eval cycles for each grid cell and
# reports comparable metrics (point_AUPRC, DCA_4_0, ...).
#
# This is the recipe from documentation/new-feature-evaluation-tutorial.md
# and documentation/hyperparameter-optimization-tutorial.md, specialised
# for "compare N candidate features against the baseline" via -extra_features.
#
# -extra_features is added on TOP of the default -features list (the
# baseline `(chem,volsite,protrusion,bfactor)`), so the grid cell `()`
# evaluates the baseline alone and `(featX)` evaluates baseline + featX.
#
# Usage:
#   ./misc/test-scripts/eval_new_features.sh feat1 [feat2 ... featN]
#
# Examples:
#   # Compare one new feature against baseline:
#   ./misc/test-scripts/eval_new_features.sh partial_charge
#
#   # Compare two features individually + together against baseline:
#   ./misc/test-scripts/eval_new_features.sh partial_charge electrostatics
#
# Environment overrides:
#   TRAIN_DS    training dataset (default: chen11-fpocket.ds)
#   EVAL_DS     eval dataset (default: joined.ds)
#   LOOP        random-seed iterations averaged per grid cell (default: 3)
#               Increase to 10+ for publication-grade comparisons; the
#               tutorial uses 10. Lower values run faster but increase
#               variance between cells.
#   CONFIG      groovy config file (default: config/train-default)
#   OUT_SUBDIR  prank output sub-directory (default: EVAL_NEW_FEATURES)
#   LABEL       run label (default: NEW_FEATS_<timestamp>)
#   EXTRA       any extra prank.sh args, appended verbatim (e.g.
#               EXTRA='-rf_trees 50 -rf_depth 8' for a smaller faster forest)

set -eu

TRAIN_DS="${TRAIN_DS:-chen11-fpocket.ds}"
EVAL_DS="${EVAL_DS:-joined.ds}"
LOOP="${LOOP:-3}"
CONFIG="${CONFIG:-config/train-default}"
OUT_SUBDIR="${OUT_SUBDIR:-EVAL_NEW_FEATURES}"
LABEL="${LABEL:-NEW_FEATS_$(date +%Y%m%d_%H%M%S)}"
EXTRA="${EXTRA:-}"

if [ $# -lt 1 ]; then
    awk 'NR==1{next} /^[^#]/{exit} {sub(/^# ?/,""); print}' "$0"
    exit 2
fi

FEATURES=("$@")

# Build the list-of-lists value for -extra_features. Per ploop's "list of lists"
# grammar (-features '((a,b),(a,b,c))' etc., documented in
# hyperparameter-optimization-tutorial.md): each inner parenthesised list is one
# grid cell. Outer parens wrap them all.
#
# Cells we want:
#   ()                              -- baseline: just the default features
#   (feat1) ... (featN)             -- baseline + each candidate individually
#   (feat1,feat2,...,featN)         -- baseline + all candidates together (only if N >= 2)
GRID="()"
for f in "${FEATURES[@]}"; do
    GRID+=",(${f})"
done
if [ ${#FEATURES[@]} -ge 2 ]; then
    ALL_CSV=$(IFS=,; echo "${FEATURES[*]}")
    GRID+=",(${ALL_CSV})"
fi
GRID="(${GRID})"

cat <<EOF
============================================================
 eval_new_features

 candidates: ${FEATURES[*]}
 train:      ${TRAIN_DS}
 eval:       ${EVAL_DS}
 config:     ${CONFIG}
 loop:       ${LOOP} (random-seed iterations per grid cell)
 grid:       -extra_features '${GRID}'
 out:        test_output/${OUT_SUBDIR}/${LABEL}
============================================================
EOF

# -sample_negatives_from_decoys 1 is the standard choice when training on
# chen11-fpocket.ds — keeps the positive/negative SAS-point distribution
# consistent across runs (recommended in new-feature-evaluation-tutorial.md).
set -x
exec ./prank.sh ploop \
    -c "${CONFIG}" \
    -t "${TRAIN_DS}" \
    -e "${EVAL_DS}" \
    -loop "${LOOP}" \
    -sample_negatives_from_decoys 1 \
    -out_subdir "${OUT_SUBDIR}" \
    -label "${LABEL}" \
    -extra_features "${GRID}" \
    ${EXTRA}
