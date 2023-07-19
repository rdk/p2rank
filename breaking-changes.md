
## Breaking changes

### Introduction

This file collects backwards incompatible changes that have potential to break code that uses P2Rank.

These include:

* changes in the command line interface 
* changes in the input/output format
* changes in default behaviour

All changes of that type should be rare and should be all listed here.

## List of changes



### 2.4.1

###### Prediction

* Scripts that execute P2Rank (shell script `distro/prank` and `distro/prank.bat`) no longer redirect log (***stderr*** stream) to the file `distro/log/prank.log`. 
  Instead, they write ***stderr*** to the console. This was done to avoid P2Rank writing to the installation directory by default, which may be forbidden on some systems.
  See issue #59.

###### Training new models

* Type of parameter `-ignore_het_groups` changed from `Set<String>` to `List<String>`
     


### 2.4

###### Prediction

none

###### Training new models

* Removed deprecated parameters `-conservation_origin` and `-load_conservation_paths` 

### 2.3

###### Prediction

none

###### Training new models

* parameter `-extra_features` was renamed to `-features` 
* command line format of parameters values with type `List<String>` and `List<List<String>>` has changed
    * now only comas `,` are delimeters and inner parentheses are respected 
    * before `.` was used as an alternative delimeter and delimeter for inner lists, now it is part of element value
    * Examples: 
        * `'(a.b.c)'` was interpreted as list of 3 elements, now it defines list of 1 element: `a.b.c`
        * list of lists value `'((a.b.c),(d.e))'` should be changed to `'((a,b,c),(d,e))'`
* Changes in `csv_file_feature`
    * renamed to `csv`
    * introduced parameter `-feat_csv_columns` (type: `List<String>`). 
        Names of enabled value columns from csv files must be listed here. 
        Columns not listed are ignored.
        * Example: if you were working with one directory of csv files with one value column named `pdbekb_conservation`, 
        you must now run the program with `-feat_csv_columns '(pdbekb_conservation)'` 
    * introduced parameter `-feat_csv_ignore_missing` (type: `boolean`, default: `false`). If true, then feature ignores:
        * missing csv files for proteins
        * missing value columns
        * missing rows for atoms and residues
      
    

### 2.2

* parameter `-conservation_dir` (type: `String`) was renamed to `-conservation_dirs` (type: `List<String>`)
* column `probability` was added to `*_predictions.csv` output file
