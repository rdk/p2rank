#!/usr/bin/env bash
#
# Evaluate the feature calculators added in the electrostatics suite by
# invoking eval_new_features.sh with this session's new feature names.
#
# Three new feature calculators were registered in this commit series:
#
#   partial_charge      — atom-level AMBER ff14SB partial charge
#                         (auto-projected to SAS via P2Rank's default
#                         closest-atom projection at feature-vector time)
#
#   partial_charge_sas  — explicit SAS-level projection via
#                         AtomicToSasFeatWrapper (wrapper-naming convention:
#                         "<atom_name>_sas"). Functionally equivalent to
#                         enabling partial_charge alone — included here for
#                         completeness; pick one or the other in practice.
#
#   electrostatics      — SAS-level Coulomb sum over partial charges
#                         (3 columns: potential, abs_potential,
#                         field_magnitude). Distinct signal from
#                         partial_charge — captures the surrounding-atom
#                         electrostatic environment at each SAS point.
#
# This wrapper runs the comparison with partial_charge + electrostatics:
# baseline (no extras), each individually, and both together. partial_charge_sas
# is excluded by default to avoid redundancy with partial_charge; enable it
# via FEATURES env var if you want the SAS-wrapper variant in the grid.
#
# Usage:
#   ./misc/test-scripts/eval_session_features.sh
#   FEATURES="partial_charge_sas electrostatics" ./misc/test-scripts/eval_session_features.sh
#   LOOP=10 ./misc/test-scripts/eval_session_features.sh

set -eu

cd "$(dirname "$0")/../.."

FEATURES_DEFAULT="partial_charge electrostatics"
FEATURES="${FEATURES:-${FEATURES_DEFAULT}}"

# shellcheck disable=SC2086  # intentional word-splitting
exec ./misc/test-scripts/eval_new_features.sh ${FEATURES}
