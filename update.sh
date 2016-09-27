#!/bin/bash

set -e  # will terminate script as soon as any command inside it fails

echo
echo GIT:
echo
git pull

echo
echo GRADLE:
echo
gradle clean assemble
