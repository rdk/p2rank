
# Hidden commands

Apart from hidden commands for training and grid optimization (see `training-tutorial.md`) P2Rank contains some miscellaneous tools. 

## Analyze

### binding residues
List unique ligand binding residue IDs (for relevant ligands) for each protein in the dataset.
~~~
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
~~~
./prank.sh analyze labeled-residues <dataset.ds>
~~~


## Export feature vectors for further analysis

~~~
./prank.sh traineval -t test_data/basic.ds -e test_data/basic.ds \
    -loop 1 -delete_vectors 0 -sample_negatives_from_decoys 0 \
    -features '(chem.volsite.protrusion.bfactor.xyz)'
~~~


## Export chains to FASTA
                           
`fasta-raw` exports residue codes as P2Rank sees them.
`fasta-mask` will transform any possible non-letter code (such as `_` or `?`) to `X`.

~~~
# run in P2Rank root directory (distro in repo)

./prank analyze fasta-raw test_data/basic.ds         # dataset
./prank analyze fasta-raw -f test_data/2W83.pdb      # single file
./prank analyze fasta-raw test_data/basic.ds 

./prank analyze fasta-masked test_data/basic.ds      # dataset
./prank analyze fasta-masked -f test_data/2W83.pdb   # single file
./prank analyze fasta-masked test_data/basic.ds -o out_dir  # specify output directory
~~~

## Print
            

### Print a list of features

Print a list of features employed by particyulat config.

~~~
./prank print features                          # for default config
./prank print features -c other_config.groovy   # for custom config
~~~

### Print model info

Print information of trained model.

~~~
./prank print model-info                     # for default model
./prank print model-info -m model2.model     # for custom model
~~~