
# Hidden commands

Apart from hidden commands for training and grid optimization (see `training-tutorial.md`) P2RANK contains some miscellaneous tools. 

## Analyze

### binding residues
List binding residue IDs (for relevant ligands) for each protein in the dataset.
~~~
prank analyze binding-residues <dataset.ds>
~~~
Related parameters:
* `-ligand_protein_contact_distance`: cutoff distance between ligand and protein atoms
* `-ignore_het_groups`: codes of ligands that are not considered relevant
* `-min_ligand_atoms`: smaller ligands are ignored






