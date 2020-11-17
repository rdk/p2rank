Dataset File Format
===================

Dataset file specifies a list of files (typically proteins) to be processed by the program.

##### Example 1: Basic dataset
~~~
2W83.pdb
1fbl.pdb
~~~
A basic single-column dataset that specifies a list of proteins. 


## Multi-column dataset format

Optionally, dataset files can have a multi-column format that allows specifying complementary data. This is relevant only if you are interested in training and evaluating new models. 

Multi-column datasets need to declare a header (see `test_data/fpocket-pairs.ds` for example of a dataset for evaluation of Fpocket predictions).

**Valid column names**:
* `"protein"` column is mandatory if the program is used for pocket prediction
* `"prediction"` column is mandatory if the program is used for pocket rescoring
* `"chains"` allows to explicitly specify which protein chains from the structure should be considered. Structures will be reduced to specified chains when loaded. Value `*` means all chains.
* `"ligands"` allows to explicitly specify which ligands should be considered (see test-ligand-codes.ds)    
* `"ligand_codes"` same as "ligands" (for backward compatibility)
* `"conservation"` contains link to sequence conservation data

If the header is not specified, the default implicit header is `HEADER: protein` i.e. dataset contains just a list of protein files.

Additionally, it is possible to specify global dataset parameters.

**Generic dataset format**:
~~~sh
# comments (everything after character #) and blank lines are ignored

# dataset parameters (optional)
PARAM.<param_name>=<param_value>
      
# Header line defines columns in the dataset (separated by whitespace)
HEADER: <column_names>

# column data (separated by whitespace)
<column_data>
~~~                

### Examples

Following examples are valid multi-column dataset files. See other examples in `test_data` folder.
Ligands can be specified by a group name (e.g. `PHI`) in which case all ligands with this name will be considered relevant. 
To specify particular molecules you can optionally use `atom_id` and `group_id` specifiers.
No whitespace in the column value is allowed.

##### Example 2: Dataset with explicitly specified relevant ligands 
~~~sh
HEADER:  protein  ligands

liganated/1a82a.pdb   DNN,ATP
liganated/1aaxa.pdb   BPM[atom_id:22344],BOG
liganated/1nlua.pdb   PHI[atom_id:1234]
liganated/1t7qa.pdb   COA[group_id:C_234A]
liganated/2ck3b.pdb   ANP
~~~
A dataset with explicitly specified ligands. Useful only for training and evaluation datasets. 

##### Example 3: Dataset of protein/prediction pairs
~~~sh
PARAM.PREDICTION_METHOD=fpocket     # specifies the method that was used to create predictions 
PARAM.LIGANDS_SEPARATED_BY_TER=true # specifies that ligands are separated by TER record (relevant only for legacy CHEN11 dataset)

HEADER: protein prediction

liganated/1a82a.pdb   predictions/fpocket/1a82a_out/1a82a_out.pdb  
liganated/1aaxa.pdb   predictions/fpocket/1aaxa_out/1aaxa_out.pdb  
~~~  
A dataset that defines pairs of liganated protein and binding site pedictions for this protein made by some prediction method, in this case, Fpocket. 
It is used for rescoring and evaluating predictions of other methods (using `prank rescore <dataset-whih-pairs.ds>`). 