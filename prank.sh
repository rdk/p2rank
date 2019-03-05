#!/bin/bash

#
# script for running development builds in-place from this directory
#
# copy misc/local-env.sh to this directory
#

. local-env.sh

export GROOVY_TURN_OFF_JAVA_WARNINGS=true

echo JAVA_LOCALENV_PARAMS = $JAVA_LOCALENV_PARAMS
echo PRANK_LOCALENV_PARAMS = $PRANK_LOCALENV_PARAMS

export JAVA_OPTS="$JAVA_OPTS -XX:+CMSClassUnloadingEnabled "
export JAVA_OPTS="$JAVA_OPTS $JAVA_LOCALENV_PARAMS"
#–XX:+UseG1GC
#-XX:+UseConcMarkSweepGC

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

PARAMS="$PRANK_LOCALENV_PARAMS"



CMD="$JAVACMD $JAVA_OPTS -cp ${CLASSPATH} cz.siret.prank.program.Main ${PARAMS} $@"
echo "+" $CMD
"$JAVACMD" $JAVA_OPTS -cp "${CLASSPATH}" cz.siret.prank.program.Main ${PARAMS} "$@"


