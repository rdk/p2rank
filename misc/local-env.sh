#!/usr/bin/env bash

#
# local environment params user by ./prank.sh in project root dir
#
# copy to project root dir and edit
#

# For training, it id best to use the garbage collector with highest throughput for particular JVM. For Java 8 it is the default one.
# Other options:
# -XX:+UseConcMarkSweepGC
# -XX:+UseG1GC
export JAVA_LOCALENV_PARAMS="-Xmx6G"

export PRANK_LOCALENV_PARAMS="-threads 4"
