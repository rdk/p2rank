Dataset File Format
===================

Dataset file specifies a list of files (usually proteins) to be processed by the program.
Multi-column format with declared variable header allows to specify complementary data.

The simplest type of dataset is a list of protein files (see `test_data/test.ds`).

Multi-column datasets need to declare a header (see `test_data/fpocket-pairs.ds` for example of a dataset for evaluation of Fpocket predictions).

~~~
# comments (lines trat start with #) and blank lines are ignored


# dataset parameters (optional)
PARAM.<PARAM_NAME>=<value>
# prediction method must be specified if dataset is used for rescoring (contains "prediction" column)
PARAM.PREDICTION_METHOD=fpocket


# header line defines columns in the dataset (separated by whitespace)
#
# "protein" column is mandatory if program is used for pocket prediction
# "prediction" column is mandatory if program is used for pocket rescoring
# other columns are optional
# "ligands" allows to explicitely specify which ligands should be considered (see test-ligand-codes.ds)    
# "ligand_codes" same as "ligands" (for backward compatibility)
# "conservation" contains link to sequence conservation data
#
# if header is not specified, default header is "HEADER: protein" i.e. dataset contains a list of protein files

HEADER: protein prediction ligand_codes conservation other_column


# column data (separated by whitespace)

<column data>
~~~                

### Examples

Folllowing examples are valid dataset files.

##### Example 1: Basic dataset
~~~
2W83.pdb
1fbl.pdb
~~~
Basic single-culumn dataset that specifies list of proteins. It is not necessary to specify header or any datset parameters, so the sections are ommited.


##### Example 2: Dataset with explicitely specified relevant ligands 
~~~
HEADER:  protein  ligands

liganated/1a82a.pdb   DNN,ATP
liganated/1aaxa.pdb   BPM[atom_id:22344],BOG
liganated/1nlua.pdb   PHI[atom_id:1234]
liganated/1t7qa.pdb   COA[group_id:C_234A]
liganated/2ck3b.pdb   ANP
~~~
Dataset with explicitely specified ligands. Useful only for training and evaluation datasets. 

##### Example 3: Dataset of protein/prediction pairs
~~~
PARAM.PREDICTION_METHOD=fpocket
PARAM.LIGANDS_SEPARATED_BY_TER=true
# specifies that ligands are separated by TER record

HEADER: protein prediction

liganated/1a82a.pdb   predictions/fpocket/1a82a_out/1a82a_out.pdb  
liganated/1aaxa.pdb   predictions/fpocket/1aaxa_out/1aaxa_out.pdb  
~~~  
Dataset that allows to define pairs of liganated protein and binding site pedictions for this protein made by some prediction method, in this case Fpocket. 
It is used for rescoring predictions of other methods (using `prank rescore <dataset-whih-pairs.ds>`) or for evaluating predictions of other method. 