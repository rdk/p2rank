#!/usr/bin/env bash

#
# run unit tests in parallel (fork-level: test classes spread across N test JVMs)
#
# usage: ./unit-tests-parallel.sh [N]
#   N = number of parallel forks (default: min(6, cpus/2))
#
# NOTE: each fork is a separate JVM (maxHeapSize=2g each), so total RAM ~= N*2g.
# Wall time is floored by the slowest single test class, so >6 forks rarely helps.
#

forks="${1:-}"
if [ -z "$forks" ]; then
    cpus=$(nproc 2>/dev/null || echo 2)
    forks=$(( cpus / 2 ))
    [ "$forks" -lt 1 ] && forks=1
    [ "$forks" -gt 6 ] && forks=6
fi

echo "running unit tests with maxParallelForks=$forks"
./gradlew test -PmaxParallelForks="$forks"
