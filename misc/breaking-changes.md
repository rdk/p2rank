
This file collects backwards incompatible changes that have a potential to break code that uses P2Rank.

These include:

* changes in the command line interface 
* changes in the input/output format
* changes in default behaviour



#### 2.2

* param `-conservation_dir` (type: `String`) was renamed to `-conservation_dirs` (type: `List<String>`)
* column `probability` was added to `*_predictions.csv` output file
