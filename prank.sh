#!/usr/bin/env bash

#
# script for running development builds in-place from this directory
#
# copy misc/local-env.sh to this directory
#

. local-env.sh

# echo JAVA_LOCALENV_PARAMS = $JAVA_LOCALENV_PARAMS
# echo PRANK_LOCALENV_PARAMS = $PRANK_LOCALENV_PARAMS

#-XX:+UseG1GC
export JAVA_OPTS="$JAVA_OPTS"
export JAVA_OPTS="$JAVA_OPTS $JAVA_LOCALENV_PARAMS"

THIS_SCRIPT_DIR_REL_PATH="$( dirname "${BASH_SOURCE[0]}" )"
export POCKET_RANK_BASE_DIR="$THIS_SCRIPT_DIR_REL_PATH/distro"

if [[ "$OSTYPE" = msys* ]] ; then
    PATH_SEPARATOR=';' # for win ... we want this script to run on Windows with MSYS / Git Bash
else
    PATH_SEPARATOR=':' # for unix
fi

CLASSPATH="${POCKET_RANK_BASE_DIR}/bin/p2rank.jar${PATH_SEPARATOR}${POCKET_RANK_BASE_DIR}/bin/lib/*"


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# JVM flags for compatibility with Java 17+
# --add-opens: required by Apache Arrow and Netty for direct memory access via sun.misc.Unsafe
# --enable-native-access: required by zstd-jni for native library loading
export JAVA_OPTS="$JAVA_OPTS --add-opens=java.base/java.nio=ALL-UNNAMED"
export JAVA_OPTS="$JAVA_OPTS --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
export JAVA_OPTS="$JAVA_OPTS --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
export JAVA_OPTS="$JAVA_OPTS --enable-native-access=ALL-UNNAMED"

# --sun-misc-unsafe-memory-access: suppresses warnings from parquet-hadoop CleanUtil
# which uses sun.misc.Unsafe (upstream unfixed). Only available in Java 23+.
# Anchor the parse on `version "` (unique in the line) and take the first number inside
# the quotes. A plain `.*"\([0-9]*\)` is greedy and matches through the CLOSING quote of
# `"21.0.10"`, yielding an empty string on modern LTS lines like:
#   java version "21.0.10" 2026-01-20 LTS
JAVA_MAJOR_VERSION=$("$JAVACMD" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
if [ "$JAVA_MAJOR_VERSION" -ge 23 ] 2>/dev/null; then
    export JAVA_OPTS="$JAVA_OPTS --sun-misc-unsafe-memory-access=allow"
fi

# --add-modules jdk.incubator.vector: enables the SIMD (256-bit Vector API) occlusion scan in the
# faster-molecular-surface library (surface_strategy=packed_distinct_v2 and the default packed_distinct_v4). Output is bit-identical to
# the scalar fallback -- this only speeds up surface generation. Added only when the running JVM
# actually ships the incubator module, so a stripped or future runtime without it still starts cleanly.
if "$JAVACMD" --list-modules 2>/dev/null | grep -q '^jdk.incubator.vector'; then
    export JAVA_OPTS="$JAVA_OPTS --add-modules jdk.incubator.vector"
fi

PARAMS="$PRANK_LOCALENV_PARAMS"


"$JAVACMD" $JAVA_OPTS -cp "${CLASSPATH}" cz.siret.prank.program.Main ${PARAMS} "$@"


