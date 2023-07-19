#!/usr/bin/env bash

#
# creates binary distribution package for release
#

set -e

./make-clean.sh

VERSION=`./gradlew properties -q | grep "version:" | awk '{print $2}'`
DIRNAME="p2rank_$VERSION"

mkdir -p build

cp -rafv distro build/${DIRNAME}
(
    cd build
    GZIP_OPT=-9 tar cvzf ${DIRNAME}.tar.gz ${DIRNAME}
    rm -rf ${DIRNAME}
)

echo
echo "DISTRO BUILD: build/${DIRNAME}.tar.gz"
echo DONE