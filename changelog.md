
# Changelog

This files track major changes in development versions of P2RANK since version 2.0-dev.6.


## 2.0-dev.7

- "chem" and "volsite" feature sets 
    - original features were moved to those two feature sets and can be turned off through `-estra_features` param
- FasterForest 
    -  streamlined implementation of FastRandomForest (~ 0.75x time, 0.5x memory, same algorithm)
- AUC (area under ROC curve) and AUPRC (area under Precision-Recall curve) metrics
    - can be turned on with `-stats_collect_predictions 1` param    
- improved logging
    - possible to log to a file inside the output directory 
    - related params: `-log_to_file 1`, `-zip_log_file 1`, `-log_to_console 1`, `-log_level WARN`
     
    
 ## 2.0-dev.6   
    
    


