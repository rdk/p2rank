
SprintALL  
SprintT1070
SprintA870 

were calculated on 28.08.2025 using following commands

~~~sh
./prank.sh analyze all-propensities all.ds -out_subdir ANALYZE -l P1 \
    -c config/pept/pept1 \
    -identify_peptides_by_labeling 1 

./prank.sh analyze all-propensities train.ds -out_subdir ANALYZE -l P1 \
    -c config/pept/pept1 \
    -identify_peptides_by_labeling 1 

./prank.sh analyze all-propensities train_A870.ds -out_subdir ANALYZE -l P1 \
    -c config/pept/pept1 \
    -identify_peptides_by_labeling 1 
~~~