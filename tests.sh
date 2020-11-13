#!/usr/bin/env bash

#
# run test sets
#
# Examples:
#
# ./tests.sh quick   # fast set of basic tests
# ./tests.sh all     # comprehensive set of tests that include training an evaluation on real datasets
#                    # datasets from https://github.com/rdk/p2rank-datasets have to be downloaded first
#

misc/test-scripts/testsets.sh $@