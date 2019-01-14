
# Hidden commands

Apart from hidden commands for training and grid optimization (see `training-tutorial.md`) P2RANK contains some miscellaneous tools. 

## Analyze

### binding residues
List unique ligand binding residue IDs (for relevant ligands) for each protein in the dataset.
~~~
prank analyze binding-residues <dataset.ds>
~~~
Related parameters:
- `-ligand_protein_contact_distance`: cutoff distance between ligand and protein atoms
- params that determine which ligands are relevant:  
  - `-min_ligand_atoms`: smaller ligands are ignored
  - `-ligc_prot_dist`: acceptable distance between ligand center and closest protein atom for relevant ligands
  - `-ignore_het_groups`: codes of ligands that are not considered relevant


### labeled residues
Analyze dataset with defined residue labeling
~~~
prank analyze labeled-residues <dataset.ds>
~~~




## export feature vectors for further analysis

~~~
./prank traineval -t test_data/single.ds -e test_data/single.ds \
    -loop 1 -delete_vectors 0 -sample_negatives_from_decoys 0 \
    -extra_features '(chem.volsite.protrusion.bfactor.xyz)'
~~~


