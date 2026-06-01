#!/usr/bin/env bash

#
# local environment params user by ./prank.sh in project root dir
#
# copy to project root dir and edit
#

# -Xmx31G                       max heap 31G (not 32G, to keep compressed oops enabled)
# -XX:+UseParallelGC            throughput-oriented GC (better than G1 for batch workloads)
# -XX:+UseCompactObjectHeaders  64-bit object headers instead of 128-bit (JEP 450, JDK 24+)
#                               note: incompatible with -Xmx31G (compressed oops limit shifts to ~29G)
# -XX:+AlwaysPreTouch           pre-fault heap pages at startup to avoid page faults later
# -XX:+EagerJVMCI               initialize Graal JIT at startup for faster warmup (only with GraalVM)
export JAVA_LOCALENV_PARAMS="-Xmx31G -XX:+UseParallelGC"

export PRANK_LOCALENV_PARAMS="-threads 8"

#
# Tuning note for LONG TRAINING runs (traineval / crossval / ploop):
# Do NOT bake the following into this file, because it is sourced by EVERY ./prank.sh
# call (including short predict runs, where a large pre-touched heap adds multi-second
# startup and can OOM). Pass them per training run instead, e.g.:
#
#   JAVA_OPTS="-Xms<N>g -Xmx<N>g -XX:+AlwaysPreTouch" ./prank.sh traineval -t ... -e ...
#
#   - -Xms = -Xmx + AlwaysPreTouch: fixed, pre-faulted heap -> no first-touch page-fault
#     stalls and no resize churn -> steadier throughput over a long run.
#   - size <N> to the dataset; stay <= 31g when it fits, to keep compressed oops
#     (compressed oops + compact object headers is denser than a > 32g heap).
#   - keep full C2 (do NOT add -XX:TieredStopAtLevel=1; that is only for short predict),
#     ParallelGC, and threads = physical cores; do not raise crossval_threads while
#     threads already saturates the cores (it would oversubscribe).
#   - if -Xlog:gc shows long full-GC pauses on a large heap, trial G1 or Generational ZGC.
#
