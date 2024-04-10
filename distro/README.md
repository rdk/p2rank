
<p align="left">
    <img src="https://github.com/rdk/p2rank/blob/develop/misc/img/p2rank_logo.svg?raw=true" width="280" alt="P2Rank" title="logo">
</p>
Ligand-binding site prediction based on machine learning.

<p align="center">
    <img src="https://github.com/rdk/p2rank/blob/develop/misc/img/p2rank_sas_points.png?raw=true" width="600" alt="P2Rank illustration">
</p>

[![version 2.4.1](https://img.shields.io/badge/version-2.4.1-green.svg)](/build.gradle)
[![Build Status](https://github.com/rdk/p2rank/actions/workflows/develop.yml/badge.svg)](https://github.com/rdk/p2rank/actions/workflows/develop.yml)
[![License: MIT](http://img.shields.io/badge/license-MIT-blue.svg?style=flat)](/LICENSE.txt)

### Description

P2Rank is a stand-alone command line program that predicts ligand-binding pockets from a protein structure. It achieves high prediction success rates without relying on an external software for computation of complex features or on a database of known protein-ligand templates. 
                        
Version 2.4 adds support for `.cif` input and contains a special profile for predictions on AlphaFold models and NMR/cryo-EM structures.  

### Requirements

* Java 11 to 21
* PyMOL 1.7 (or newer) for viewing visualizations (optional)

P2Rank is tested on Linux, macOS, and Windows. 
On Windows, it is recommended to use the `bash` console to execute the program instead of `cmd` or `PowerShell`.

### Setup

P2Rank requires no installation. Binary packages are available as GitHub Releases.

* **Download**: https://github.com/rdk/p2rank/releases
* Source code: https://github.com/rdk/p2rank
* Datasets: https://github.com/rdk/p2rank-datasets

### Usage

<pre>
<b>prank</b> predict -f test_data/1fbl.pdb         # predict pockets on a single pdb file 
</pre>  

See more usage examples below...

### Algorithm

P2Rank makes predictions by scoring and clustering points on the protein's solvent accessible surface. 
Ligandability score of individual points is determined by a machine learning based model trained on the dataset of known protein-ligand complexes. 
For more details see the slides and publications.

Presentation slides introducing the original version of the algorithm: [Slides (pdf)](http://bit.ly/p2rank_slides)  

### Publications

If you use P2Rank, please cite relevant papers:

* [Software article](https://doi.org/10.1186/s13321-018-0285-8) about P2Rank pocket prediction tool  
 Krivak R, Hoksza D. ***P2Rank: machine learning based tool for rapid and accurate prediction of ligand binding sites from protein structure.*** Journal of Cheminformatics. 2018 Aug.
* [A new web-server article](https://doi.org/10.1093/nar/gkac389) about updates in the web interface [prankweb.cz](https://prankweb.cz)  
 Jakubec D, Skoda P, Krivak R, Novotny M, Hoksza D ***PrankWeb 3: accelerated ligand-binding site predictions for experimental and modelled protein structures.*** Nucleic Acids Research, Volume 50, Issue W1, 5 July 2022, Pages W593–W597
* [Web-server article](https://doi.org/10.1093/nar/gkz424) introducing the web interface at [prankweb.cz](https://prankweb.cz)  
 Jendele L, Krivak R, Skoda P, Novotny M, Hoksza D. ***PrankWeb: a web server for ligand binding site prediction and visualization.*** Nucleic Acids Research, Volume 47, Issue W1, 02 July 2019, Pages W345-W349 
* [Conference paper](https://doi.org/10.1007/978-3-319-21233-3_4) introducing P2Rank prediction algorithm  
 Krivak R, Hoksza D. ***P2RANK: Knowledge-Based Ligand Binding Site Prediction Using Aggregated Local Features.*** International Conference on Algorithms for Computational Biology 2015 Aug 4 (pp. 41-52). Springer
* [Research article](https://doi.org/10.1186/s13321-015-0059-5) about PRANK rescoring algorithm  
 Krivak R, Hoksza D. ***Improving protein-ligand binding site prediction accuracy by classification of inner pocket points using local features.*** Journal of Cheminformatics. 2015 Dec.


Usage Examples
--------------

Following commands can be executed in the installation directory.

### Print help

~~~bash
prank help
~~~

### Predict ligand binding sites (P2Rank algorithm)

~~~bash
prank predict test.ds                    # run on dataset containing a list of pdb/cif files

prank predict -f test_data/1fbl.pdb      # run on a single pdb file
prank predict -f test_data/1fbl.cif      # run on a single cif file
prank predict -f test_data/1fbl.pdb.gz   # run on a single gzipped pdb file

prank predict -threads 8     test.ds     # specify num. of working threads for parallel dataset processing
prank predict -o output_here test.ds     # explicitly specify output directory

prank predict -c alphafold   test.ds     # use alphafold config and model (config/alphafold.groovy)  
                                         # this profile is recommended for AlphaFold models, NMR and cryo-EM 
                                         # structures since it doesn't depend on b-factor as a feature         
~~~

### Prediction output 

   For each structure file `<struct_file>` in the dataset P2Rank produces several output files:
   * `<struct_file>_predictions.csv`: contains an ordered list of predicted pockets, their scores, coordinates 
   of their centers together with a list of adjacent residues, list of adjacent protein surface atoms, and a calibrated probability of being a ligand-binding site
   * `<struct_file>_residues.csv`: contains list of all residues from the input protein with their scores, 
   mapping to predicted pockets, and a calibrated probability of being a ligand-binding residue
   * `visualizations/<struct_file>.pml`: PyMol visualization (`.pml` script with data files in `data/`) 
     * generating visualizations can be turned off by `-visualizations 0` parameter
     * coordinates of the SAS points can be found in `visualizations/data/<struct_file>_points.pdb.gz`. There the "Residue sequence number" (23-26) of HETATM record
       corresponds to the rank of the corresponding pocket (points with value 0 do not belong to any pocket).


### Configuration

You can override the default params with a custom config file:

~~~bash
prank predict -c config/example.groovy  test.ds
prank predict -c example                test.ds # same effect, config/ is default location and .groovy implicit extension
~~~


It is also possible to override the default params on the command line using their full name.

~~~bash
prank predict                   -visualizations 0 -threads 8  test.ds   #  turn off visualizations and set the number of threads
prank predict -c example.groovy -visualizations 0 -threads 8  test.ds   #  overrides defaults as well as values from example.groovy
~~~     

P2Rank has numerous configurable parameters. 
To see the list of standard params look into `config/default.groovy` and other example config files in this directory.
To see the complete commented list of all (including undocumented) 
params see [Params.groovy](https://github.com/rdk/p2rank/blob/develop/src/main/groovy/cz/siret/prank/program/params/Params.groovy) in the source code.


### Evaluate prediction model
...on a file or a dataset with known ligands.

~~~bash
prank eval-predict -f test_data/1fbl.pdb
prank eval-predict test.ds
~~~

### Rescoring (PRANK algorithm)

In addition to predicting new ligand binding sites, 
P2Rank is also able to rescore pockets predicted by other methods 
(Fpocket, ConCavity, SiteHound, MetaPocket2, LISE and DeepSite are supported at the moment).

~~~bash
prank rescore test_data/fpocket.ds
prank rescore fpocket.ds                 # test_data/ is default 'dataset_base_dir'
prank rescore fpocket.ds -o output_dir   # test_output/ is default 'output_base_dir'       
prank eval-rescore fpocket.ds            # evaluate rescoring model
~~~

## Build from sources

This project uses [Gradle](https://gradle.org/) build system via included Gradle wrapper.
On Windows use `bash` to execute build commands (`bash` is installed as a part of [Git for Windows](https://git-scm.com/download/win)).

```bash
git clone https://github.com/rdk/p2rank.git && cd p2rank
./make.sh       

./unit-tests.sh    # optionally you can run tests to check everything works fine on your machine        
./tests.sh quick   # runs further tests
```    
Now you can run the program via:
```bash
distro/prank       # standard mode that is run in production
./prank.sh         # development/training mode 
``` 
To use `./prank.sh` (development/training mode) first you need to copy and edit `misc/locval-env.sh` into repo root directory (see https://github.com/rdk/p2rank/blob/develop/misc/tutorials/training-tutorial.md#preparing-the-environment).

## Comparison with Fpocket

[Fpocket](https://github.com/Discngine/fpocket) is a widely used open source ligand binding site prediction program.
It is fast, easy to use and well documented. As such, it was a great inspiration for this project.
Fpocket is written in C, and it is based on a different geometric algorithm.

Some practical differences:

* **Fpocket**
    - has a much smaller memory footprint 
    - runs faster when executed on a single protein
    - produces a high number of less relevant pockets (and since the default scoring function isn't very effective the most relevant pockets often don't get to the top)
    - contains MDpocket algorithm for pocket predictions from molecular trajectories 
    - still better documented
* **P2Rank** 
    - achieves significantly higher identification success rates when considering top-ranked pockets
    - produces a smaller number of more relevant pockets
    - speed:
        + slower when running on a single protein (due to JVM startup cost)
        + approximately as fast on average running on a big dataset on a single core
        + due to parallel implementation potentially much faster on multi-core machines
    - higher memory footprint (~1G but doesn't grow much with more parallel threads)

Both Fpocket and P2Rank have many configurable parameters that influence behaviour of the algorithm and can be tweaked to achieve better results for particular requirements.


## Thanks

This program builds upon software written by other people, either through library dependencies or through code included in its source tree (where no library builds were available). Notably:
* FastRandomForest by Fran Supek (https://code.google.com/archive/p/fast-random-forest/)
* FastRandomForest 2.0 (https://github.com/GenomeDataScience/FastRandomForest)
* KDTree by Rednaxela (http://robowiki.net/wiki/User:Rednaxela/kD-Tree)
* BioJava (https://github.com/biojava)
* Chemistry Development Kit (https://github.com/cdk)
* Weka (http://www.cs.waikato.ac.nz/ml/weka/)

## Contributing

We welcome any bug reports, enhancement requests, and other contributions. To submit a bug report or enhancement request, please use the [GitHub issues tracker](https://github.com/rdk/p2rank/issues). For more substantial contributions, please fork this repo, push your changes to your fork, and submit a pull request with a good commit message. 
