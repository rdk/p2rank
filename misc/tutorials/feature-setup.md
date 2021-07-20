
# Feature setup documentation

This file describes feature vector configuration and provides introduction to adding new features.
Useful only for training and evaluating new models.

P2Rank version: 2.4-dev.1

## Introduction

P2Rank is based on predicting scores of SAS points that are described by feature vectors.
A feature vector is basically an array of real numbers (`double[]`) with a header (i.e. each element has a unique name).

P2Rank comes with a set of implemented feature calculators.
Each calculator has a name and calculates an array of a certain length (e.g. for `volsite` n=5, `bfactor` n=1).

We will use the term *feature* for feature calculator (e.g. `chem`) and *sub-feature* for an individual element - single scalar number (e.g. `chem.atoms`).


## Feature configuration

Composition of feature vector is influenced by parameters:
              
* `-features` 
    * lists enabled feature calculators    
    * deafult: `(chem,volsite,protrusion,bfactor)` 
    * `atom_table` and `residue_table`features are implicitly enabled by default
* `-atom_table_features` and `-residue_table_features` 
    * determine which columns from atom type and residue type tables are enabled   
* `-feature_filters`
    * see "Filtering features" section below

#### Configuration syntax

Note that the syntax for list-of-strings parameter value is different on the command line and in a `*.groovy` config file:
* command line: `-features '(chem,volsite,protrusion,bfactor)'`
* config file: `features = ['chem','volsite','protrusion','bfactor']`

#### Check enabled features

To check which features are enabled for a particular configuration run `print features` command:
```bash
./prank print features
```

<details>
  <summary>Example: Default feature setup:  (click to expand)</summary>
  
  ```bash
$ ./prank print features
----------------------------------------------------------------------------------------------
 P2Rank 2.3-dev.1
----------------------------------------------------------------------------------------------

Effectively enabled features:

chem
volsite
protrusion
bfactor
atom_table

Effective feature vector header (i.e. enabled sub-features):

 0: chem.hydrophobic
 1: chem.hydrophilic
 2: chem.hydrophatyIndex
 3: chem.aliphatic
 4: chem.aromatic
 5: chem.sulfur
 6: chem.hydroxyl
 7: chem.basic
 8: chem.acidic
 9: chem.amide
10: chem.posCharge
11: chem.negCharge
12: chem.hBondDonor
13: chem.hBondAcceptor
14: chem.hBondDonorAcceptor
15: chem.polar
16: chem.ionizable
17: chem.atoms
18: chem.atomDensity
19: chem.atomC
20: chem.atomO
21: chem.atomN
22: chem.hDonorAtoms
23: chem.hAcceptorAtoms
24: volsite.vsAromatic
25: volsite.vsCation
26: volsite.vsAnion
27: volsite.vsHydrophobic
28: volsite.vsAcceptor
29: volsite.vsDonor
30: protrusion.protrusion
31: bfactor.bfactor
32: atom_table.apRawValids
33: atom_table.apRawInvalids
34: atom_table.atomicHydrophobicity

----------------------------------------------------------------------------------------------
 finished successfully in 0 hours 0 minutes 1.044 seconds
----------------------------------------------------------------------------------------------
  ```
</details>


## Adding new features

If you want to add new features that are not implemented in P2Rank you have 3 options:
* Implement a new feature calculator in Java or Groovy
    * this is not too difficult and has an advantage that the feature will be calculated automatically for new datasets
    * For introduction see [new feature tutorial](new-feature-evaluation-tutorial.md) 
* Provide custom atom type and residue type tables for `atom_table` and `residue_table` features
    * allow defining values for residue types and atom types
        * residue types are: (ALA,ARG,ASN,...)
        * atom types are: (ALA.C,ALA.CA,ALA.CB,...)
    * useful only if the values are the same for all proteins in the dataset (for example: hydrophobicity index of amino acids).
    * see example tables: `aa-propensities.csv` and `atomic-properties.csv`
    * NOTE: providing custom tables is not implemented yet (planned for 2.3-dev.2)
* Use `csv` feature
    * allows defining values for every protein residue and/or every protein atom (for each protein separately) via external csv files
    * disadvantage: csv files must be manually calculated for each dataset  
    * Configuration:
        * looks for csv files named `{peorein_file_name}.csv` in directories defined in `-feat_csv_directories` parameter
        * enabled value columns from csv files must be declared in `-feat_csv_columns`
        * `-feat_csv_ignore_missing` allows ignoring missing csv files, columns and rows
    * _TODO_: add more detailed documentation for csv feature

## Filtering features

You can selectively enable/disable certain features and sub-features with `-feature_filters` parameter.
Filters are applied only to the features that are first enabled by `-features` parameter.
If the value of `-feature_filters` is empty, all sub-features are used (i.e. no filtering is applied).

Examples of individual filters:

 * `*` - include all
 * `chem.*` - include all with prefix "chem."
 * `-chem.*` - exclude all with prefix "chem."
 * `chem.hydrophobicity` - include particular sub-feature
 * `-chem.hydrophobicity` - exclude particular sub-feature

Filters are applied sequentially.

If the first filter starts with `-`, everything is implicitly enabled. Otherwise, everything is implicitly disabled.
For example:
* `-feature_filters '(-chem.atoms)'` - include everything except `chem.atoms`
* `-feature_filters '(chem.atoms)'` - include only `chem.atoms`


Further examples:

* `-feature_filters '()'` - include all
* `-feature_filters '(*)'` - include all
* `-feature_filters '(*,-chem.*)'` - include all except those with prefix "chem."
* `-feature_filters '(-chem.*)'` - include all except those with prefix "chem."
* `-feature_filters '(-chem.*,chem.hydrophobicity)'` - include all except those with prefix "chem.", but include "chem.hydrophobicity"
* `-feature_filters '(chem.hydrophobicity)'` - include only "chem.hydrophobicity"
* `-feature_filters '(chem.*,-chem.hydrophobicity,-chem.atoms)` - include only those with prefix "chem.", except "chem.hydrophobicity" and "chem.atoms"


<details>
  <summary>Example: `-feature_filters '(chem.atoms,volsite.*,bfactor.*)'`:  (click to expand)</summary>
  
  ```bash
$ ./prank print features -features '(chem,volsite,bfactor)' -feature_filters '(chem.atoms,volsite.*,bfactor.*)'
----------------------------------------------------------------------------------------------
 P2Rank 2.3-dev.1
----------------------------------------------------------------------------------------------

Effectively enabled features (after filtering):

chem
volsite
bfactor

Effective feature vector header (i.e. enabled sub-features):

 0: chem.atoms
 1: volsite.vsAromatic
 2: volsite.vsCation
 3: volsite.vsAnion
 4: volsite.vsHydrophobic
 5: volsite.vsAcceptor
 6: volsite.vsDonor
 7: bfactor.bfactor

----------------------------------------------------------------------------------------------
 finished successfully in 0 hours 0 minutes 1.043 seconds
----------------------------------------------------------------------------------------------
  ```
</details>

#### Filtering and grid optimization

You can use `-feature_filters` param in combination with grid optimization (`ploop` command).
For datails see [hyperparameter optimization tutorial](hyperparameter-optimization-tutorial.md).

Example:
```
./prank ploop -t train.ds -e eval.ds -loop 10 -feature_filters '((-chem.*),(-chem.atoms,-chem.polar),(protrusion.*,bfactor.*))'
```            
This command will run train-eval experiments for 3 different feature setups by applying a different list of feature filters. 
For each feature setup, it will run 10 train-eval cycles (using different random seed) and calculate average results. 
