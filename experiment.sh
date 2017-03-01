#!/bin/bash

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

( cd ../p2rank-results && git-push ) 
