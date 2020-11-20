#!/usr/bin/env bash

set -e  # fail fast

HEAD1=`git log -n 1 | head -n 1`

echo
echo GIT:
echo
git pull

HEAD2=`git log -n 1 | head -n 1`

	
if [ "$HEAD1" != "$HEAD2" ]; then 

	echo
	echo GRADLE:
	echo
	./gradlew clean assemble

fi

