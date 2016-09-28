
P2RANK 
======

Ligand-binding site prediction tool based on machine learning.

<p align="center">
    <img src="http://siret.ms.mff.cuni.cz/krivak/p2rank/figures/points2_small.png" width="600">
</p>


### Requirements

* Java 1.8 or newer for execution
* PyMOL 1.7.x for viewing visualizations

### Setup

P2RANK requires no installation. Binary packages can be downloaded from project website.

* Download: http://siret.ms.mff.cuni.cz/p2rank
* Source code: https://github.com/rdk/p2rank
* Datasets: https://github.com/rdk/p2rank-datasets

### Usage

<pre>
<b>prank</b> predict -f test_data/1fbl.pdb         # predict pockets on a single pdb file 
</pre>  

See more usage examples below...

### Compilation

To compile P2RANK you need Gradle (https://gradle.org/). Build with `./make.sh` or `gradle assemble`.


Usage Examples
--------------

Following commands can be executed in the installation directory.

### Print help:

~~~
prank help
~~~

### Ligand binding site prediction (P2RANK algorithm):

~~~
prank predict test.ds                             # run on whole dataset (containing list of pdb files)

prank predict -f test_data/1fbl.pdb               # run on single pdb file
prank predict -f test_data/1fbl.pdb.gz            # run on single gzipped pdb file

prank predict -o output_here      test.ds         # explicitly specify output directory
prank predict -threads 8          test.ds         # specify no. of working threads for parallel processing
prank predict -c predict2.groovy  test.ds         # specify configuration file (predict2.groovy uses 
                                                    different prediction model and combination of parameters)
~~~

### Evaluate model for pocket prediction:

~~~
prank eval-predict test.ds
prank eval-predict -f test_data/1fbl.pdb
~~~

### Prediction output notes:

   For each file in the dataset program produces a CSV file in the output directory named 
   `<pdb_file_name>_predictions.csv`, which contains an ordered list of predicted pockets, their scores, coordinates 
   of their centroids and list of PDBSerials of adjacent amino acids and solvent exposed atoms.

   If coordinates connolly points that belong to individual pockets are needed they can be found
   in `visualizations/data/<pdb_file_name>_points.pdb`. There "Residue sequence number" (23-26) of HETATM record 
   cocrresponds to the rank of corresponding pocket (points with value 0 do not belong to any pocket).

### Rescore pocket detected by other methods (PRANK algorithm):

~~~
prank rescore test_data/fpocket.ds
prank rescore fpocket.ds                 # test_data/ is default 'dataset_base_dir'
prank rescore fpocket.ds -o output_dir   # test_output/ is default 'output_base_dir'
~~~

### Override default params with custom config file:

~~~
prank rescore -c config/example.groovy test_data/fpocket.ds
prank rescore -c example.groovy        fpocket.ds
~~~


### It is also possible to override default params on a command line with their full name:
 (to see complete list of params look into config/default.groovy)

~~~
prank rescore                   -seed 151 -threads 8  test_data/fpocket.ds
prank rescore -c example.groovy -seed 151 -threads 8  test_data/fpocket.ds
~~~

### Evaluate model for pocket rescoring:

~~~
prank eval-rescore                        fpocket-pairs.ds
prank eval-rescore -m model/default.model fpocket-pairs.ds
prank eval-rescore -m default.model       fpocket-pairs.ds
prank eval-rescore -m other.model         fpocket-pairs.ds
prank eval-rescore -m other.model         fpocket-pairs.ds -o output_dir
~~~
