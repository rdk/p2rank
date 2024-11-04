#!/usr/bin/env bash

set -e

git add --all

./commit.sh "$1"

git push --all
git push --tags
