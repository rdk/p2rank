#!/usr/bin/env bash

#
# run experiment ant push results to p2rank-results git repo
#

set -x

git-push() {
    set -e
    git pull
    git add --all
    git commit -m "experiment: $@"
    git push
}

./prank.sh "$@"

if [ $? -eq 0 ]; then
    echo EXPERIMENT WENT OK. Pushing results to git...
    ( cd ../p2rank-results && git-push )
else
    echo FAILED
fi

