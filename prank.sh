#!/bin/bash

#
# script for running development builds in-place from this directory
#
# copy misc/local-env-params.sh to this directory
#

. local-env-params.sh


echo JAVA_LOCALENV_PARAMS = $JAVA_LOCALENV_PARAMS
echo PRANK_LOCALENV_PARAMS = $PRANK_LOCALENV_PARAMS

export JAVA_OPTS="$JAVA_OPTS -XX:+CMSClassUnloadingEnabled "
export JAVA_OPTS="$JAVA_OPTS $JAVA_LOCALENV_PARAMS"
#–XX:+UseG1GC
#-XX:+UseConcMarkSweepGC

THIS_SCRIPT_DIR_REL_PATH="$( dirname "${BASH_SOURCE[0]}" )"
export POCKET_RANK_BASE_DIR="$THIS_SCRIPT_DIR_REL_PATH/distro"

UNAME=`uname -a`
if [[ "$UNAME" = MINGW* ]] ; then
    PATH_SEPARATOR=';' # for win
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
"$JAVACMD" $JAVA_OPTS -cp "${CLASSPATH}" cz.siret.prank.program.Main ${PARAMS} "$@" 2>>local-debug.log


