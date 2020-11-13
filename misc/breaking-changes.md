
This file collects backwards incompatible changes that have a potential to break code thet uses P2Rank.

These incluede:

* changes in the command line interface 
* changes in the input/output format
* changes in default behaviour



#### 2.2

* param `-conservation_dir` (type: `String`) was renamed to `-conservation_dirs` (type: `List<String>`)
* column `probability` was added to `*_predictions.csv` output file
