
# Feature setup documentation

This file describes feature vector configuration and provides introduction to adding new features.
Useful only for training and evaluating new models.

## Introduction

P2Rank is based on predicting scores of SAS points that are described by feature vectors.
A feature vector is basically an array of real numbers (`double[]`) with a header (i.e. each element has a unique name).

P2Rank comes with a set of implemented feature calculators.
Each calculator has a name and calculates an array of a certain length (e.g. for `volsite` n=6, `bfactor` n=1).

We will use the term *feature* for feature calculator (e.g. `chem`) and *sub-feature* for an individual element - single scalar number (e.g. `chem.atoms`).


## Feature configuration

Composition of feature vector is influenced by parameters:
              
* `-features` 
    * list of enabled feature calculators    
    * default: `(chem,volsite,protrusion,bfactor)` 
    * `atom_table` is implicitly enabled when `-atom_table_features` is non-empty (true by default); `residue_table` is implicitly enabled when `-residue_table_features` is non-empty (empty by default, so not active out of the box)
* `-extra_features`
    * list of feature calculators appended **on top of** `-features`
    * default: empty
    * the effective feature set is `features ∪ extra_features`
    * useful when you want to compare a candidate against the default baseline without restating the baseline list every time; see also the
      `((),(my_new),(my_new,my_other))` ploop pattern in [hyperparameter-optimization-tutorial.md](hyperparameter-optimization-tutorial.md)
* `-atom_table_features` and `-residue_table_features` 
    * determine which columns from atom type and residue type tables are enabled   
* `-feature_filters`
    * see "Filtering features" section below

### Configuration syntax

> [!NOTE]
> The syntax for list-of-strings parameter value is different on the command line and in a `*.groovy` config file:
> * command line: `-features '(chem,volsite,protrusion,bfactor)'`
> * config file: `features = ['chem','volsite','protrusion','bfactor']` (Groovy syntax)

### Check enabled features

To check which features are enabled for a particular configuration run `print features` command:
```bash
./prank print features
```

<details>
  <summary>Example: Default feature setup:  (click to expand)</summary>
  
```bash
$ ./prank print features
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


## Feature catalog

The table below lists feature calculators that ship with P2Rank. Use the names with
`-features` or `-extra_features`. For the full effective sub-feature list of any
configuration, run `./prank print features -c <config>`. For the authoritative
list (including training-internal variants), see
`src/main/groovy/cz/siret/prank/features/api/FeatureRegistry.groovy`.

| Name | Description |
|---|---|
| `chem` | Per-atom chemical descriptors (hydrophobicity, charge, donor/acceptor, ...) |
| `volsite` | VolSite pharmacophore indicators (aromatic, cation, anion, hydrophobic, acceptor, donor) |
| `protrusion` | Local surface protrusion |
| `bfactor` | Per-atom B-factor (meaningful only for experimental structures) |
| `atom_table` | Atom-type table lookup (columns selected by `-atom_table_features`) |
| `residue_table` | Residue-type table lookup (columns selected by `-residue_table_features`) |
| `conservation` | Sequence conservation score (requires `-conservation_dirs` or HMM provider). Several variants exist, see [Conservation features](#conservation-features) below. |
| `asa` | Accessible surface area |
| `aa-propensity`, `atomtype-propensity`, `duplets`, `triplets` | AA / atom-type / sequence-duplet / triplet binding propensities |
| `ss`, `sss` (+ `ss_cloud`, `sss_cloud`, `sss_motif`) | Secondary structure (full + simplified, cloud and motif variants) |
| `contactres` | Contact-residue features |
| `electrostatics` | AMBER ff14SB Coulomb potential evaluated at four distance scales |
| `energy-*` (+ `energy-cloud*-ch3`), `energy2-*` / `e2s-*`, `e3-*` | Probe-energy features at several force-field parameterisations and aggregation scales |
| `anm_sensor`, `anm_effectiveness`, `anm_msf`, `cg_betweenness`, `cg_closeness`, `cg_degree` | Physics: ANM modes and contact-graph centrality measures |
| `csv` | Per-protein values supplied through external CSV files (see "Adding new features" below) |
| `xyz` | Dummy feature exposing the 3D coordinates of each SAS point (useful for `-export_points`) |


## Conservation features

P2Rank ships several conservation feature calculators. They all start from the same per-residue
conservation scores and differ only in how those scores are mapped onto SAS points. All require
conservation data (`-conservation_dirs` or an HMM provider, see [conservation.md](conservation.md)).

| Name | Notes |
|---|---|
| `conservation`, `conservationcloud`, `conservationcloudscaled` | Original triplet. |
| `conserv_sas`, `conserv_atomic`, `conserv_cloud` (+ `conserv_cloud2`) | Cleaner re-implementation, recommended. |
| `z-conserv_sas`, `z-conserv_atomic`, `z-conserv_cloud` (+ `z-conserv_cloud2`) | Z-score normalized variants. |

> [!WARNING]
> The bare name `conserv` (and `z-conserv`) is **not** a usable feature: it is the internal name
> of the residue-level calculator, exposed only through the `_sas` / `_atomic` wrappers. Passing
> `-features '(conserv)'` fails with `Feature implementation not found: conserv`. Use
> `conserv_sas`, `conserv_atomic`, or `conserv_cloud` instead.

> [!TIP]
> If you are unsure which to use, prefer the `conserv_*` triplet and tune `-conserv_cloud_radius`.
> The variants differ mainly in SAS-point mapping; in practice the difference between them is
> usually small.


## Adding new features

If you want to add new features that are not implemented in P2Rank you have 3 options:
* Implement a new feature calculator in Java or Groovy
    * this is not too difficult and has an advantage that the feature will be calculated automatically for new datasets
    * For introduction see [new feature tutorial](new-feature-evaluation-tutorial.md)
    * Atom features are auto-projected to SAS points by the model. If you ALSO want
      to expose an explicit SAS-projected variant (e.g. for `-export_points` output
      or for selecting it by a different name on the command line), additionally
      register `AtomicToSasFeatWrapper(new MyFeature())`; it registers under the
      name `my_feature_sas` (the wrapped feature's name with a `_sas` suffix). 
* Provide custom atom type and residue type tables for `atom_table` and `residue_table` features
    * allow defining values for residue types and atom types
        * residue types are: (ALA,ARG,ASN,...)
        * atom types are: (ALA.C,ALA.CA,ALA.CB,...)
    * useful only if the values are the same for all proteins in the dataset (for example: hydrophobicity index of amino acids).
    * see example tables: `aa-propensities.csv` and `atomic-properties.csv`
    * **Important:** custom tables must be placed under `src/main/resources/tables/` and require a rebuild;
      there is no CLI/config knob to point at an external path. For per-protein values, use the `csv` feature instead.
* Use `csv` feature
    * allows defining values for every protein residue and/or every protein atom (for each protein separately) via external csv files
    * disadvantage: csv files must be manually calculated for each dataset  
    * Configuration:
        * looks for csv files named `{protein_file_name}.csv` in directories defined in `-feat_csv_directories` parameter
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
 * `chem.hydrophobic` - include particular sub-feature
 * `-chem.hydrophobic` - exclude particular sub-feature

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
* `-feature_filters '(-chem.*,chem.hydrophobic)'` - include all except those with prefix "chem.", but include "chem.hydrophobic"
* `-feature_filters '(chem.hydrophobic)'` - include only "chem.hydrophobic"
* `-feature_filters '(chem.*,-chem.hydrophobic,-chem.atoms)'` - include only those with prefix "chem.", except "chem.hydrophobic" and "chem.atoms"


<details>
  <summary>Example: `-feature_filters '(chem.atoms,volsite.*,bfactor.*)'`:  (click to expand)</summary>
  
  ```bash
$ ./prank print features -features '(chem,volsite,bfactor)' -feature_filters '(chem.atoms,volsite.*,bfactor.*)'
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

### Filtering and grid optimization

You can use `-feature_filters` param in combination with grid optimization (`ploop` command).
For details see [hyperparameter optimization tutorial](hyperparameter-optimization-tutorial.md).

Example:
```bash
./prank ploop -t train.ds -e eval.ds -loop 10 -feature_filters '((-chem.*),(-chem.atoms,-chem.polar),(protrusion.*,bfactor.*))'
```            
This command will run train-eval experiments for 3 different feature setups by applying a different list of feature filters. 
For each feature setup, it will run 10 train-eval cycles (using different random seed) and calculate average results. 


## Exporting feature vectors

Use the `traineval` command with `-delete_vectors 0` to dump the computed
feature vectors of every SAS point alongside its label. The `xyz` dummy
feature stores the 3D coordinates of each point, which is useful for
downstream geometric analysis.

~~~bash
./prank.sh traineval -t test_data/basic.ds -e test_data/basic.ds \
    -loop 1 -delete_vectors 0 -sample_negatives_from_decoys 0 \
    -features '(chem,volsite,protrusion,bfactor,xyz)'
~~~
