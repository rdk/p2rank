
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
Analyze dataset with explicitly specified residue labeling.
~~~
./prank.sh analyze labeled-residues <dataset.ds>
~~~




## Exporting feature vectors for further analysis

~~~
./prank.sh traineval -t test_data/basic.ds -e test_data/basic.ds \
    -loop 1 -delete_vectors 0 -sample_negatives_from_decoys 0 \
    -extra_features '(chem.volsite.protrusion.bfactor.xyz)'
~~~



## More prediction examples


~~~
# predict using model trained with conservation
   
./prank.sh eval-predict ../p2rank-datasets/coach420.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'coach420/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh       
./prank.sh eval-predict ../p2rank-datasets/holo4k.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'holo4k/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh     
./prank.sh eval-predict ../p2rank-datasets/joined.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'joined/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh     
./prank.sh eval-predict ../p2rank-datasets/fptrain.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'fptrain/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh      
    
# same but with default model   
 
./prank.sh eval-predict ../p2rank-datasets/coach420.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0       
./prank.sh eval-predict ../p2rank-datasets/holo4k.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0   
./prank.sh eval-predict ../p2rank-datasets/joined.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0   
./prank.sh eval-predict ../p2rank-datasets/fptrain.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0    

~~~

