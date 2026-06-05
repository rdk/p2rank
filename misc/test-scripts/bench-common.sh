#!/usr/bin/env bash
#
# Shared helpers for the predict_bench*.sh benchmark scripts.
# Source this AFTER cd-ing to the repo root:  source misc/test-scripts/bench-common.sh
#

SDK_DIR="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java"

# Default protein set: a size spread from distro/test_data (small -> large).
BENCH_DEFAULT_PROTEINS=(
    distro/test_data/clean/1t7qa.pdb
    distro/test_data/1fbl.pdb
    distro/test_data/clean/1a26A.pdb
    distro/test_data/2W83.pdb
    distro/test_data/1AHP.pdb
)

# Print the calling script's header comment block (line 2 up to the first blank) as help.
# Uses $0 so it reflects the entry script, not this sourced file.
bench_usage() { sed -n '2,/^$/p' "$0" | sed 's/^# \?//; s/^#$//'; exit 0; }

# List installed SDKMAN java candidates.
bench_list_candidates() {
    echo "Available SDKMAN JREs ($SDK_DIR):"
    if [[ -d "$SDK_DIR" ]]; then
        ls -1 "$SDK_DIR" 2>/dev/null | grep -v '^current$' | sed 's/^/  /'
    else
        echo "  (none)"
    fi
}

# Resolve a spec (SDKMAN candidate name or a JAVA_HOME path) to a JAVA_HOME.
# Echoes the resolved path, or empty string if it cannot be resolved.
bench_resolve_home() {
    local spec="$1"
    [[ -x "$spec/bin/java" ]] && { echo "$spec"; return; }
    [[ -x "$SDK_DIR/$spec/bin/java" ]] && { echo "$SDK_DIR/$spec"; return; }
    echo ""
}

# Major version (e.g. 21) and short label (e.g. java-21.0.10) for a JAVA_HOME.
bench_major_of() { "$1/bin/java" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'; }
bench_label_of() { "$1/bin/java" -version 2>&1 | head -1 | sed -E 's/ version "/-/; s/".*//'; }

# The `java` command honoring $JAVA_HOME (so launchers and probes use the same JRE).
bench_javacmd() { echo "${JAVA_HOME:+$JAVA_HOME/bin/}java"; }

# Pipe filter: prepend a cumulative wall-clock timestamp (seconds since the first
# line) to each stdin line. $1 is an optional perl printf format taking the time
# float then the line (default "%7.3f  %s"); pass a $'...'-quoted format if you need
# a literal tab/newline, since the variable form is not escape-interpolated.
bench_ts_prefix() {
    BENCH_TS_FMT="${1:-%7.3f  %s}" perl -MTime::HiRes=time -ne 'BEGIN{$s=time} printf $ENV{BENCH_TS_FMT}, time-$s, $_'
}
