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
