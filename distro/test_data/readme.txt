
This directory contains example input data.


Dataset File Format
===================

Dataset file specifies a list of files (usually proteins) to be processed by the program.
Multi-column format with declared variable header allows to specify complementary data.

The simplest type of dataset is a list of protein files (see test.ds).

Multi-column datasets need to declare a header (see fpocket-pairs.ds for example of a dataset for evaluation of Fpocket predictions).

~~~
# comments and blank lines are ignored


# dataset parameters (optional)
PARAM.<PARAM_NAME>=<value>
# prediction method must be specified if dataset is used for rescoring (contains "prediction" column)
PARAM.PREDICTION_METHOD=fpocket


# header line defines columns in the dataset (separated by whitespace)
#
# "protein" column is mandatory if program is used for pocket prediction
# "prediction" column is mandatory if program is used for pocket rescoring
# other columns are optional
# "ligand_codes" allows to explicitely specify which ligands should be considered (see test-ligand-codes.ds)
# "conservation" contains link to sequence conservation data
#
# if header is not specified, default header is "HEADER: protein" i.e. dataset contains a list of protein files

HEADER: protein prediction ligand_codes conservation other_column


# column data (separated by whitespace)

<column data>
~~~




