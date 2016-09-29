
P2RANK 
======
Ligand-binding site prediction tool based on machine learning.


### Requirements

* Java 1.8 or newer for execution
* PyMOL 1.7.x for viewing visualizations

### Setup

P2RANK requires no installation. Binary packages can be downloaded from the project website.

* **Download**: http://siret.ms.mff.cuni.cz/p2rank
* Source code: https://github.com/rdk/p2rank
* Datasets: https://github.com/rdk/p2rank-datasets

### Usage

<pre>
<b>prank</b> predict -f test_data/1fbl.pdb         # predict pockets on a single pdb file 
</pre>  

See more usage examples below...

### Compilation

To compile P2RANK you need Gradle (https://gradle.org/). Build with `./make.sh` or `gradle assemble`.

### Algorithm

P2RANK makes predictions by scoring and clustering points on the protein's Connolly surface. Ligandability score of individual points is determined by a machine learning based model trained on the dataset of known protein-ligand complexes.

Slides: http://bit.ly/p2rank_slides (somewhat dated overview)


Usage Examples
--------------

Following commands can be executed in the installation directory.

### Print help

~~~
prank help
~~~

### Predict ligand binding sites (P2RANK algorithm)

~~~
prank predict test.ds                             # run on whole dataset (containing list of pdb files)

prank predict -f test_data/1fbl.pdb               # run on single pdb file
prank predict -f test_data/1fbl.pdb.gz            # run on single gzipped pdb file

prank predict -threads 8          test.ds         # specify no. of working threads for parallel processing
prank predict -o output_here      test.ds         # explicitly specify output directory
prank predict -c predict2.groovy  test.ds         # specify configuration file (predict2.groovy uses 
                                                    different prediction model and combination of parameters)
~~~

### Evaluate prediction model
...on a file or a dataset with known ligands.

~~~
prank eval-predict -f test_data/1fbl.pdb
prank eval-predict test.ds
~~~

### Prediction output 

   For each file in the dataset program produces a CSV file in the output directory named 
   `<pdb_file_name>_predictions.csv`, which contains an ordered list of predicted pockets, their scores, coordinates 
   of their centroids and list of PDBSerials of adjacent amino acids and solvent exposed atoms.

   If coordinates of Connolly points that belong to predicted pockets are needed they can be found
   in `visualizations/data/<pdb_file_name>_points.pdb`. There "Residue sequence number" (23-26) of HETATM record 
   cocrresponds to the rank of corresponding pocket (points with value 0 do not belong to any pocket).

### Configuration

You can override default params with custom config file:

~~~
prank predict -c config/example.groovy  test.ds
prank predict -c example.groovy         test.ds
~~~


It is also possible to override the default params on the command line using their full name. To see complete list of params look into `config/default.groovy`.

~~~
prank predict                   -seed 151 -threads 8  test.ds
prank predict -c example.groovy -seed 151 -threads 8  test.ds
~~~

### Rescoring (PRANK algorithm)

In addition to predicting new ligand binding sites, P2RANK is also able to rescore pockets predicted by other methods (Fpocket and ConCavity are supported at the moment).

~~~
prank rescore test_data/fpocket.ds
prank rescore fpocket.ds                 # test_data/ is default 'dataset_base_dir'
prank rescore fpocket.ds -o output_dir   # test_output/ is default 'output_base_dir'
~~~

### Evaluate rescoring model

~~~
prank eval-rescore fpocket-pairs.ds
~~~


## Thanks

This program builds upon software written by other people, either through library dependencies or through code included in it's source tree (where no library builds were available). Notably:
* FastRandomForest by Fran Supek (https://code.google.com/archive/p/fast-random-forest/)
* KDTree by Rednaxela (http://robowiki.net/wiki/User:Rednaxela/kD-Tree)
* BioJava (https://github.com/biojava)
* Chemistry Development Kit (https://github.com/cdk)
* Weka (http://www.cs.waikato.ac.nz/ml/weka/)

## Contributing

We welcome any bug reports, enhancement requests, and other contributions. To submit a bug report or enhancement request, please use the [GitHub issues tracker](https://github.com/rdk/p2rank/issues). For more substantial contributions, please fork this repo, push your changes to your fork, and submit a pull request with a good commit message. 