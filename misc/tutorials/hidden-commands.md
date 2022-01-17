
# Hidden commands

Apart from hidden commands for training and grid optimization (see `training-tutorial.md`) P2Rank contains some miscellaneous tools. 



## Analyze

### residues

List all residues with some details:
* secondary structure
* binding information

See also "binding residues".

~~~sh
./prank.sh analyze residues <dataset.ds>
~~~


### binding residues

List residues binding relevant ligands.
            
Residue key format: `<chain_author_id>_<seq_number><ins_code>`

~~~sh
./prank.sh analyze binding-residues <dataset.ds>
~~~

Related parameters:
- `-ligand_protein_contact_distance`: cutoff distance between ligand and protein atoms
- params that determine which ligands are relevant:  
  - `-min_ligand_atoms`: smaller ligands are ignored
  - `-ligc_prot_dist`: acceptable distance between ligand center and closest protein atom for relevant ligands
  - `-ignore_het_groups`: codes of ligands that are not considered relevant


### labeled residues

Analyze a dataset with an explicitly specified residue labeling.

~~~sh
./prank.sh analyze labeled-residues <dataset.ds>
~~~


## Export feature vectors for further analysis

`-delete_vectors 0`           
`xyz` is a dummy feature that stores 3D coordinates of a given SAS point.   

~~~sh
./prank.sh traineval -t test_data/basic.ds -e test_data/basic.ds \
    -loop 1 -delete_vectors 0 -sample_negatives_from_decoys 0 \
    -features '(chem,volsite,protrusion,bfactor,xyz)'
~~~


## Export chains to FASTA
                           
`fasta-raw` exports residue codes as P2Rank sees them.
`fasta-mask` will transform any possible non-letter code (such as `_` or `?`) to `X`.

~~~sh
# run in P2Rank root directory (distro in repo)

./prank analyze fasta-raw test_data/basic.ds         # dataset
./prank analyze fasta-raw -f test_data/2W83.pdb      # single file
./prank analyze fasta-raw test_data/basic.ds 

./prank analyze fasta-masked test_data/basic.ds      # dataset
./prank analyze fasta-masked -f test_data/2W83.pdb   # single file
./prank analyze fasta-masked test_data/basic.ds -o out_dir  # specify output directory
~~~
   

## Reduce structure to chains

~~~sh
./prank.sh analyze reduce-to-chains -f <structure_file> -chains <chain_names> -out_format <format_file_extension> -out_file <file_name>
~~~
* `-f <>` required, structure fie in one of the formats `pdb|pdb.gz|cif|cif.gz`
* `-chains` required, coma separated list of chain names, wildcards: `keep`, `all`
  * in the case of mmcif files, values refer to old PDB chain names (author id), not mmcif ids
  * `keep` keeps the structure as is, just saves with required format (may not work perfectly due to biojava), useful for debugging
  * `*` is not the same as keeping structure as is, but runs the reduction procedure with all the chains, useful for debugging
* `-out_format` optional, default value is `keep` -- use the same format as the input 
  * possible values: `keep|pdb|pdb.gz|cif|cif.gz`
* `-out_file` optional, output structure file name, path relative to the shell working directory
  * if specified, redced strucdure is saved under secified name and no other output is produced
  * if not specified, default name is generated (see examples) and file is saved in the output directory specified with parameters `-o`, `-output_base_dir`, `-out_subdir`
     
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

## Print
            

### Print a list of features

Check which features are enabled for a particular configuration.

~~~sh
./prank print features                          # for default config
./prank print features -c other_config.groovy   # for custom config
~~~

### Print model info

Print information about trained model (`*.model` file).

~~~
./prank print model-info                     # for default model
./prank print model-info -m model2.model     # for custom model
~~~