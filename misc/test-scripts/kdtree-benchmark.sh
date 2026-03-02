#!/usr/bin/env bash

#
# Runs comparative correctness and performance benchmark for v1 vs v2 KdTree.
# Generates random points, builds both trees, verifies identical query results,
# and measures relative performance. First iteration is warmup (excluded from averages).
#
# Usage: ./misc/test-scripts/kdtree-benchmark.sh [OPTIONS]
#
# Options:
#   -p, --points N       Number of random data points (default: 5000)
#   -q, --queries N      Number of random query points (default: 500)
#   -i, --iterations N   Number of iterations with different seeds (default: 5)
#   -s, --seed N         Base random seed (default: 42)
#   -r, --radii LIST     Comma-separated radii to test (default: "2.0,6.0,10.0")
#   -h, --help           Show this help
#
# Examples:
#
#   # Quick smoke test — small dataset, few iterations, fast feedback
#   ./misc/test-scripts/kdtree-benchmark.sh -p 1000 -q 100 -i 3
#
#   # Default run — typical protein size (5K atoms), good balance of speed and accuracy
#   ./misc/test-scripts/kdtree-benchmark.sh
#
#   # Large stress test — 15K points with many queries, more iterations for stable timings
#   ./misc/test-scripts/kdtree-benchmark.sh -p 15000 -q 10000 -i 20
#
#   # Reproducibility check — use a specific seed to reproduce exact results
#   ./misc/test-scripts/kdtree-benchmark.sh -s 12345
#
#   # Test with tight radii typical for SAS point consolidation (~1.5 A)
#   ./misc/test-scripts/kdtree-benchmark.sh -r "1.0,1.5,3.0"
#

set -euo pipefail

# Defaults
POINTS=5000
QUERIES=500
ITERATIONS=5
SEED=42
RADII="2.0,6.0,10.0"

usage() {
    sed -n '3,13p' "$0" | sed 's/^# \?//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--points)     POINTS="$2";     shift 2 ;;
        -q|--queries)    QUERIES="$2";    shift 2 ;;
        -i|--iterations) ITERATIONS="$2"; shift 2 ;;
        -s|--seed)       SEED="$2";       shift 2 ;;
        -r|--radii)      RADII="$2";      shift 2 ;;
        -h|--help)       usage ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
done

echo "KdTree Benchmark: points=$POINTS queries=$QUERIES iterations=$ITERATIONS seed=$SEED radii=$RADII"
echo ""

RESULT_XML="build/test-results/test/TEST-cz.siret.prank.geom.kdtree.v2.KdTreeBenchmarkTest.xml"

./gradlew cleanTest test \
    --tests 'cz.siret.prank.geom.kdtree.v2.KdTreeBenchmarkTest' \
    -Dkdtree.benchmark=true \
    -Dkdtree.points="$POINTS" \
    -Dkdtree.queries="$QUERIES" \
    -Dkdtree.iterations="$ITERATIONS" \
    -Dkdtree.seed="$SEED" \
    -Dkdtree.radii="$RADII" \
    --quiet

# Extract and display report from test output (gradle hides stdout by default)
if [[ -f "$RESULT_XML" ]]; then
    # Extract content between CDATA tags in system-out
    python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('$RESULT_XML')
root = tree.getroot()
for tc in root.findall('testcase'):
    if tc.get('name') == 'compareV1vsV2()':
        errors = tc.findall('failure') + tc.findall('error')
        if errors:
            print('FAILED:', errors[0].text[:500] if errors[0].text else 'unknown error')
so = root.find('system-out')
if so is not None and so.text:
    print(so.text)
" 2>/dev/null || {
    # Fallback: show report file
    if [[ -f local/kdtree-benchmark-report.txt ]]; then
        cat local/kdtree-benchmark-report.txt
    else
        echo "Test completed. See build/reports/tests/test/index.html for details."
    fi
}
else
    echo "ERROR: Test result not found. Check build/reports/tests/test/index.html"
    exit 1
fi
