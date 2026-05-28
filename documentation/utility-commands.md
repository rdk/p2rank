
# Utility commands

P2Rank ships a number of utility subcommands beyond the main `predict` /
`rescore` / `eval-predict` workflow. They are grouped under three top-level
verbs: `analyze`, `transform`, and `print`. None of these are hidden from
`--help`; they are simply secondary to the prediction workflow.

Within each section below, user-facing diagnostics come first and
developer-only utilities are clearly marked at the end.

For training, cross-validation, and grid optimization commands (`traineval`,
`ploop`, etc.), see [training-tutorial.md](training-tutorial.md).


## Analyze

`analyze` subcommands inspect proteins, residues, ligands, conservation,
cofactors, and dataset statistics. They never modify input structures.

### Residue-level inspection

#### residues

List all residues with secondary structure and binding information for each
protein in a dataset. Output: `<outdir>/<protein>_residues.csv` per protein,
with columns `chain_name, seq_num, ins_code, key, chain_mmcif_id, atoms,
sec_struct_type, is_binding`.

~~~sh
./prank.sh analyze residues <dataset.ds>
~~~


#### binding-residues

List residues that bind relevant ligands.
Residue key format: `<chain_author_id>_<seq_number><ins_code>`.
Output: `<protein>_binding-residues.txt` per protein (sorted, deduplicated
residue keys, one per line) plus a stdout summary.

~~~sh
./prank.sh analyze binding-residues <dataset.ds>
~~~

Related parameters:
- `-ligand_protein_contact_distance`: cutoff distance between ligand and protein atoms
- params that determine which ligands are relevant:
  - `-min_ligand_atoms`: smaller ligands are ignored
  - `-ligc_prot_dist`: acceptable distance between ligand center and closest protein atom
  - `-ignore_het_groups`: codes of ligands that are not considered relevant


#### labeled-residues

Analyze a dataset with an explicitly specified residue labeling.

~~~sh
./prank.sh analyze labeled-residues <dataset.ds>
~~~


### Sequence export

`fasta-raw` exports residue codes as P2Rank sees them.
`fasta-masked` transforms any non-letter code (such as `_` or `?`) to `X`.
Most analyze commands also accept `-o <out_dir>` to override the output directory.

~~~sh
./prank.sh analyze fasta-raw test_data/basic.ds         # dataset
./prank.sh analyze fasta-raw -f test_data/2W83.pdb      # single file

./prank.sh analyze fasta-masked test_data/basic.ds      # dataset
./prank.sh analyze fasta-masked -f test_data/2W83.pdb   # single file
~~~


### Cofactors and conservation diagnostics

#### cofactors

Survey HETATM groups in a structure or dataset, and dry-run a `-cofactors`
configuration. Writes `het_groups.csv`, `het_groups_summary.txt`, and
`cofactor_matches.csv` to the output directory.
See [cofactors.md](cofactors.md#discovery--diagnostics) for full documentation.

~~~sh
./prank.sh analyze cofactors -f protein.pdb                              # survey all HETATM groups
./prank.sh analyze cofactors -f protein.pdb -cofactors FAD,PLP           # dry-run: which specifiers match?
./prank.sh analyze cofactors dataset.ds     -cofactors FAD,PLP           # dry-run on a dataset
~~~


#### conservation

Load and print per-residue conservation scores for each chain. Writes a
`conservation.csv` and `conservation_summary.txt`, plus per-protein PyMOL
visualizations when `-visualizations true` is set. `-threads 1` is recommended
so that log output is readable; conservation IO is the bottleneck rather than CPU.
See [conservation.md](conservation.md#debugging-sequence-and-conservation-mapping)
for full documentation.

~~~sh
./prank.sh analyze conservation dataset.ds -threads 1 -visualizations true
~~~


### Dataset and structure surveys (power user)

These subcommands are mostly useful when curating or validating datasets.

| Subcommand | Description |
|---|---|
| `proteins` | Per-structure dataset stats (chains, residues, atoms, ligands, peptides). |
| `parse-proteins` | Parse every dataset item and report load errors. |
| `chains` | Per-chain stats (id, mmcif id, length, residue string). |
| `chains-residues` | `chains` plus a residue-detail CSV per chain. |
| `peptides` | List peptide chains per protein. |
| `binding-sites` | Per-site stats (atoms/residues/radius/center) for ligand or explicit-site datasets. |


### Propensity statistics (power user)

| Subcommand | Description |
|---|---|
| `aa-propensities` | Per-AA propensity of being labeled as binding (over exposed residues). |
| `atomtype-propensities` | Per-atom-type propensity of contacting a relevant ligand (restricted to exposed atoms). |
| `aa-surf-seq-duplets` | Ordered sequence-duplet propensities starting from exposed residues. |
| `aa-surf-seq-triplets` | Sequence-triplet propensities for exposed residues. |
| `all-propensities` | Run all four propensity analyses above in one pass. |


### Developer-only analyze utilities

| Subcommand | Description |
|---|---|
| `binding-site-centers` | Compute each `SiteCenterMethod` for every site and report distances between methods, to SAS, and to protein. |
| `convert-dataset-to-atomid` | Rewrite a dataset using `[atom_id:N]` ligand specifiers. |
| `print-volsite-table` | Dump the VolSite atom-property table used by `volsite` features. |


## Transform

`transform` subcommands rewrite or convert structures, datasets, and models.

### reduce-to-chains

Reduce a structure file to a subset of chains.

~~~sh
./prank.sh transform reduce-to-chains -f <structure_file> [-chains <chain_names>] [-out_format <ext>] [-out_file <name>]
~~~

* `-f <>` required, structure file in one of the formats `pdb|pdb.gz|cif|cif.gz`
* `-chains` optional (default `keep`), comma-separated list of chain names
  * in mmCIF files, values refer to old PDB chain names (author id), not mmCIF ids
  * `keep` keeps the structure as is, just re-saves with the requested format (useful for format conversion)
  * `all` runs the reduction procedure with all the chains (useful for debugging)
* `-out_format` optional, default `keep` (same format as the input)
  * possible values: `keep|pdb|pdb.gz|cif|cif.gz`
* `-out_file` optional, output structure file name, path relative to the shell working directory
  * if specified, the reduced structure is saved under the given name and no other output is produced
  * if not specified, a default name is generated (see examples) and the file is saved in the output directory specified with `-o`, `-output_base_dir`, or `-out_subdir`

Examples:

~~~sh
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif    -chains A                                                 # output: <out_dir>/2W83_A.cif
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.pdb    -chains A                                                 # output: <out_dir>/2W83_A.pdb
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif.gz -chains A,B                                               # output: <out_dir>/2W83_A,B.cif.gz
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif.gz -chains A,B  -out_file distro/test_output/2W83_A,B.cif.gz # output: distro/test_output/2W83_A,B.cif.gz
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif    -chains keep                                              # output: <out_dir>/2W83.cif
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif    -chains keep -out_format pdb.gz                           # output: <out_dir>/2W83.pdb.gz
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif    -chains all                                               # output: <out_dir>/2W83_all.cif
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif    -chains A    -out_format keep                             # output: <out_dir>/2W83_A.cif
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.cif.gz -chains A    -out_format pdb.gz                           # output: <out_dir>/2W83_A.pdb.gz
./prank.sh transform reduce-to-chains -f distro/test_data/2W83.pdb.gz -chains A,B  -out_format cif                              # output: <out_dir>/2W83_A,B.cif
~~~


### aaindex1-to-csv

Convert an AAIndex1 file to a CSV table indexed by amino acid.

~~~sh
./prank.sh transform aaindex1-to-csv -f <aaindex1-file>
~~~


### Model maintenance

| Subcommand | Description |
|---|---|
| `flatten-rf-model` | Re-save a Random Forest model with `rf_flatten=true` for faster inference. |
| `model-to-v3-format` | Convert a legacy model to the v3 directory layout (`model.zst`). |


### Developer-only transform utilities

| Subcommand | Description |
|---|---|
| `loop-flatten-rf-model` | Repeatedly flatten a model in an infinite loop (developer micro-benchmark). |
| `bench-flatten-optimizers` | Not implemented (TODO stub). |


## Print

`print` subcommands dump internal state to stdout for inspection.

### features

Show effectively enabled features and the full sub-feature vector header for a
particular configuration.

~~~sh
./prank.sh print features                          # for default config
./prank.sh print features -c other_config.groovy   # for custom config
~~~


### feature-sets

Print just the list of effectively enabled features (no sub-feature header).

~~~sh
./prank.sh print feature-sets -c <config>
~~~


### model-info

Print information about a trained model (`*.model` file).

~~~sh
./prank.sh print model-info                     # for default model
./prank.sh print model-info -m model2.model     # for custom model
~~~


### params

Dump the effective parameters (command-line args plus resolved defaults).

~~~sh
./prank.sh print params -c <config>
~~~


### Developer-only print utilities

| Subcommand | Description |
|---|---|
| `transform-model` | Re-serialize a model with the current `Futils` Zstd writer. |
