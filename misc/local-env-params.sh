#!/usr/bin/env bash

#
# local environment params user by ./prank.sh in project root dir
#
# copy to project root dir and edit
#

# -XX:+UseConcMarkSweepGC
# -XX:+UseG1GC
export JAVA_LOCALENV_PARAMS="-Xmx6G -XX:+UseConcMarkSweepGC"

export PRANK_LOCALENV_PARAMS="-threads 4"
