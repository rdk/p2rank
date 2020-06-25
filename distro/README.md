
P2Rank 
======
Ligand-binding site prediction based on machine learning.

<p align="center">
    <img src="http://siret.ms.mff.cuni.cz/krivak/p2rank/figures/points2_small.png" width="600">
</p>

[![version 2.1](https://img.shields.io/badge/version-2.1-green.svg)](/build.gradle)
[![Build Status](https://travis-ci.org/rdk/p2rank.svg?branch=master)](https://travis-ci.org/rdk/p2rank)
[![License: MIT](http://img.shields.io/badge/license-MIT-blue.svg?style=flat)](/LICENSE.txt)

### Description

P2Rank is a stand-alone command line program that predicts ligand-binding pockets from a protein structure. It achieves high prediction success rates without relying on an external software for computation of complex features or on a database of known protein-ligand templates. 

### Requirements

* Java 8 or newer
* PyMOL 1.7 (or newer) for viewing visualizations (optional)

### Setup

P2Rank requires no installation. Binary packages can be downloaded from the project website.

* **Download**: http://siret.ms.mff.cuni.cz/p2rank
* Source code: https://github.com/rdk/p2rank
* Datasets: https://github.com/rdk/p2rank-datasets

### Usage

<pre>
<b>prank</b> predict -f test_data/1fbl.pdb         # predict pockets on a single pdb file 
</pre>  

See more usage examples below...

### Compilation

This project uses [Gradle](https://gradle.org/) build system. Build with `./make.sh` or `./gradlew assemble`.

### Algorithm

P2Rank makes predictions by scoring and clustering points on the protein's solvent accessible surface. Ligandability score of individual points is determined by a machine learning based model trained on the dataset of known protein-ligand complexes. For more details see slides and publications.

Slides introducing original version of the algotithm: http://bit.ly/p2rank_slides 

### Publications

If you use P2Rank, please cite relevant papers:

* [Software article](https://doi.org/10.1186/s13321-018-0285-8) in JChem about P2Rank pocket prediction tool  
 Krivak R, Hoksza D. *P2Rank: machine learning based tool for rapid and accurate prediction of ligand binding sites from protein structure.* Journal of Cheminformatics. 2018 Aug.
* [Web-server article](https://doi.org/10.1093/nar/gkz424) in NAR about the web interface accessible at [prankweb.cz](http://prankweb.cz)  
 Jendele L, Krivak R, Skoda P, Novotny M, Hoksza D. *PrankWeb: a web server for ligand binding site prediction and visualization.* Nucleic Acids Research, Volume 47, Issue W1, 02 July 2019, Pages W345–W349 
* [Conference paper](https://doi.org/10.1007/978-3-319-21233-3_4) inroducing P2Rank prediction algorithm  
 Krivak R, Hoksza D. *P2RANK: Knowledge-Based Ligand Binding Site Prediction Using Aggregated Local Features.* InInternational Conference on Algorithms for Computational Biology 2015 Aug 4 (pp. 41-52). Springer
* [Research article](https://doi.org/10.1186/s13321-015-0059-5) in JChem about PRANK rescoring algorithm  
 Krivak R, Hoksza D. *Improving protein-ligand binding site prediction accuracy by classification of inner pocket points using local features.* Journal of Cheminformatics. 2015 Dec.



Usage Examples
--------------

Following commands can be executed in the installation directory.

### Print help

~~~
prank help
~~~

### Predict ligand binding sites (P2Rank algorithm)

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

   For each file in the dataset P2Rank produces produces several output files:
   * `<pdb_file_name>_predictions.csv`: contains an ordered list of predicted pockets, their scores, coordinates 
   of their centers together with a list of adjacent residues and a list of adjacent protein surface atoms
   * `<pdb_file_name>_residues.csv`: contains list of all residues from the input protein with their scores, 
   mapping to predicted pockets and calibrated probability of being a ligand-binding residue
   * PyMol visualization (`.pml` script with data files) 

   If coordinates of SAS points that belong to predicted pockets are needed, they can be found
   in `visualizations/data/<pdb_file_name>_points.pdb`. There "Residue sequence number" (23-26) of HETATM record 
   corresponds to the rank of corresponding pocket (points with value 0 do not belong to any pocket).

### Configuration

You can override the default params with a custom config file:

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

In addition to predicting new ligand binding sites, 
P2Rank is also able to rescore pockets predicted by other methods 
(Fpocket, ConCavity, SiteHound, MetaPocket2, LISE and DeepSite are supported at the moment).

~~~
prank rescore test_data/fpocket.ds
prank rescore fpocket.ds                 # test_data/ is default 'dataset_base_dir'
prank rescore fpocket.ds -o output_dir   # test_output/ is default 'output_base_dir'
~~~

### Evaluate rescoring model

~~~
prank eval-rescore fpocket.ds
~~~

## Comparison with Fpocket

[Fpocket](http://fpocket.sourceforge.net/) is widely used open source ligand binding site prediction program.
It is fast, easy to use and well documented. As such, it was a great inspiration for this project.
Fpocket is written in C and it is based on a different geometric algorithm.

Some practical differences:

* Fpocket
    - has much smaller memory footprint 
    - runs faster when executed on a single protein
    - produces a high number of less relevant pockets (and since the default scoring function isn't very effective the most relevant pockets often doesn't get to the top)
    - contains MDpocket algorithm for pocket predictions from molecular trajectories 
    - still better documented
* P2Rank 
    - achieves significantly better identification success rates when considering top-ranked pockets
    - produces smaller number of more relevant pockets
    - speed:
        + slower when running on a single protein (due to JVM startup cost)
        + approximately as fast on average running on a big dataset on a single core
        + due to parallel implementation potentially much faster on multi core machines
    - higher memory footprint (~1G but doesn't grow much with more parallel threads)

Both Fpocket and P2Rank have many configurable parameters that influence behaviour of the algorithm and can be tweaked to achieve better results for particular requirements.


## Thanks

This program builds upon software written by other people, either through library dependencies or through code included in it's source tree (where no library builds were available). Notably:
* FastRandomForest by Fran Supek (https://code.google.com/archive/p/fast-random-forest/)
* KDTree by Rednaxela (http://robowiki.net/wiki/User:Rednaxela/kD-Tree)
* BioJava (https://github.com/biojava)
* Chemistry Development Kit (https://github.com/cdk)
* Weka (http://www.cs.waikato.ac.nz/ml/weka/)

## Contributing

We welcome any bug reports, enhancement requests, and other contributions. To submit a bug report or enhancement request, please use the [GitHub issues tracker](https://github.com/rdk/p2rank/issues). For more substantial contributions, please fork this repo, push your changes to your fork, and submit a pull request with a good commit message. 
