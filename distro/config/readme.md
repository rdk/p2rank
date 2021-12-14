Config Directory
================

This directory contains P2Rank config files.

Initially, P2Rank loads configuration from `default.groovy` (and from `default_rescore.groovy` in case you run `prank rescore ...`).

Parameters can be then overridden in a custom config file (`-c <config.file>`) or directly on the command line.

## Details

Parameters can be set in 2 ways:
1. on the command line `-<param_name> <value>`
2. in config groovy file specified with `-c <config.file>` (see working.groovy for an example... `prank -c example.groovy`). 

Parameters on the command line override those in the config file, which override defaults.

Parameter application priority (last wins):
1. default values in the source code (`Params.groovy`)
2. defaults in `default.groovy`
3. (optionally) defaults in `default_rescore.groovy` only if you run `prank rescore ...`
4. parameters in custom config file `-c <config.file>`
5. parameters on the command line

To see a comprehensive list of all possible params see `Params.groovy` in the source code:
https://github.com/rdk/p2rank/blob/master/src/main/groovy/cz/siret/prank/program/params/Params.groovy
