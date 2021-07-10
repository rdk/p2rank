## More prediction examples

### predict using the model trained with conservation
~~~
./prank.sh eval-predict ../p2rank-datasets/coach420.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'coach420/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh       
./prank.sh eval-predict ../p2rank-datasets/holo4k.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'holo4k/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh     
./prank.sh eval-predict ../p2rank-datasets/joined.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'joined/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh     
./prank.sh eval-predict ../p2rank-datasets/fptrain.ds -l conserv -out_subdir CONS \
    -c distro/config/conservation \
    -conservation_dir 'fptrain/conservation/e5i1/scores' \
    -fail_fast 1 \
    -visualizations 0 | ./logc.sh      
    
# same but with default model   
 
./prank.sh eval-predict ../p2rank-datasets/coach420.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0       
./prank.sh eval-predict ../p2rank-datasets/holo4k.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0   
./prank.sh eval-predict ../p2rank-datasets/joined.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0   
./prank.sh eval-predict ../p2rank-datasets/fptrain.ds -l default -out_subdir CONS \
    -fail_fast 1 \
    -visualizations 0    

~~~
