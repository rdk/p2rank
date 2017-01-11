
# P2RANK model traning and optimization turorial




## Parameters

P2RANK uses global static parameters object. In code it can be accessed with `Params.getInst()` or through `Parametrized` trait. For full list of parameters see `Params.groovy`.

For execution, params can be set in 2 ways:
1. on the command line `-<param_name> <value>`
2. in config groovy file with `-c <config.file>` (see working.groovy for example... `prank -c working.groovy`). 

Parameter application priority (last wins):
1. default values in `Params.groovy`
2. defaults in `config/default.groovy`
3. (optionally) defaults in `config/default-rescore.groovy` only if you run `prank rescore ...`
4. `-c <config.file>`
5. command line

## Training

`prank `

Example commands:




## Grid optimization

















