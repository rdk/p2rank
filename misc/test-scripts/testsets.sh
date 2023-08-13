#!/usr/bin/env bash

###################################################################################################################

ROUTINE=$1
shift

TIMESTAMP=$(date +'%Y%m%d_%H%M%S')
LOGDIR="local-logs/$TIMESTAMP"
mkdir -pv $LOGDIR

RUN_LOG="$LOGDIR/run.log"
DEBUG_LOG="$LOGDIR/debug.log"
SUMMARY_LOG="$LOGDIR/summary.log"
ERRORS_LOG="$LOGDIR/errors.log"

###################################################################################################################

red=`tput setaf 1`
green=`tput setaf 2`
blue=`tput setaf 3`
cyan=`tput setaf 6`
reset=`tput sgr0`

function format_time {
  local T=$1
  local D=$((T/60/60/24))
  local H=$((T/60/60%24))
  local M=$((T/60%60))
  local S=$((T%60))
  (( $D > 0 )) && printf '%d days ' $D
  (( $H > 0 )) && printf '%d hours ' $H
  (( $M > 0 )) && printf '%d min ' $M
  (( $D > 0 || $H > 0 || $M > 0 ))
  printf '%d s\n' $S
}

# test command and write exit code and running time
test() {
    CMD="$@"

    echo "${reset}testing command [${blue}$CMD${reset}]${reset}"

    start=$(date +%s)

    # run command
    # echo error $CMD >> $APPLOG
    $CMD &>> $RUN_LOG
    EXIT_CODE=$?

    end=$(date +%s)
    runtime=$((end-start))
    ftime=$(format_time runtime)

    if [[ $EXIT_CODE == 0 ]]; then
        echo "${green}[OK]${reset}" "time: $ftime"
    else
        echo "${red}[ERROR]${reset} (exit code: $EXIT_CODE)" "time: $ftime"
    fi
}

title() {
    echo
    echo "${cyan}${@}${reset}"
    echo
}

###################################################################################################################

dummy() {
    title DUMMY TEST COMMANDS

    test echo dummy 1
    test echo dummy 2
    test echo dummy 3
}

quick() {

    title RUNNING QUICK TESTS

    # files relative to distro/test_data/
    # uses default config
    # therefore the results are stored in distro/test_output/

    test ./prank.sh predict -f distro/test_data/1fbl.pdb                          -out_subdir TEST/TESTS
    test ./prank.sh predict -f distro/test_data/1fbl.pdb.gz                       -out_subdir TEST/TESTS
    test ./prank.sh predict -f distro/test_data/1fbl.pdb.zst -c alphafold         -out_subdir TEST/TESTS
    test ./prank.sh predict -f distro/test_data/1fbl.cif     -c alphafold         -out_subdir TEST/TESTS
    test ./prank.sh predict -f distro/test_data/1fbl.cif.gz  -c alphafold         -out_subdir TEST/TESTS
    test ./prank.sh predict -f distro/test_data/1fbl.cif.zst                      -out_subdir TEST/TESTS
    test ./prank.sh predict test.ds                                               -out_subdir TEST/TESTS
    test ./prank.sh eval-predict -f distro/test_data/liganated/1aaxa.pdb          -out_subdir TEST/TESTS
    test ./prank.sh eval-predict test.ds                                          -out_subdir TEST/TESTS

    test ./prank.sh rescore fpocket.ds                                            -out_subdir TEST/TESTS
    test ./prank.sh rescore fpocket3.ds                                           -out_subdir TEST/TESTS
    test ./prank.sh rescore concavity.ds                                          -out_subdir TEST/TESTS
    test ./prank.sh eval-rescore fpocket.ds                                       -out_subdir TEST/TESTS
    test ./prank.sh eval-rescore concavity.ds                                     -out_subdir TEST/TESTS

    test ./prank.sh traineval -t fpocket.ds -e test.ds       -loop 1 -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh crossval  fpocket.ds  -folds 4           -loop 1 -fail_fast 1 -out_subdir TEST/TESTS

    # test grid optimization
    test ./prank.sh ploop -t fpocket.ds -e test.ds -loop 1 -fail_fast 1 -r_generate_plots 0 -feature_filters '((-chem.*),(-chem.*,chem.atoms),(protrusion.*,bfactor.*))' -out_subdir TEST/TESTS
    # test different tessellation
    test ./prank.sh traineval -t fpocket.ds -e test.ds -loop 1 -fail_fast 1 -tessellation 1 -train_tessellation 3 -out_subdir TEST/TESTS
}

quick_train() {
    test ./prank.sh traineval -loop 1 -t fpocket.ds -e test.ds       -fail_fast 1 -out_subdir TEST/TESTS
}

basic() {

    title RUNNING BASIC TESTS

    # -fail_fast 0 because of missing ligands
    test ./prank.sh eval-predict chen11.ds                              -c config/train-default                        -out_subdir TEST/TESTS
    test ./prank.sh eval-predict 'joined(mlig).ds'                      -c config/train-default                        -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e chen11-fpocket.ds -c config/train-default  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'joined(mlig).ds' -c config/train-default  -loop 1  -fail_fast 0 -out_subdir TEST/TESTS
    test ./prank.sh crossval chen11-fpocket.ds                          -c config/train-default  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS

    #test ./prank.sh eval-predict mlig-joined.ds   -c config/train-default -visualizations 1 -tessellation 3 -l VISUALIZATIONS_TES3 -c config/train-default -out_subdir TEST/TESTS
    #test ./prank.sh eval-predict mlig-joined.ds   -c config/train-default -visualizations 1  -l VISUALIZATIONS                     -c config/train-default -out_subdir TEST/TESTS
}

# test prediction on all datasets
predict() {

   title PREDICTIONS ON ALL DATASETS

   test ./prank.sh predict joined.ds          -c config/test-default    -out_subdir TEST/PREDICT
   test ./prank.sh predict holo4k.ds          -c config/test-default    -out_subdir TEST/PREDICT
   test ./prank.sh predict coach420.ds        -c config/test-default    -out_subdir TEST/PREDICT
   test ./prank.sh predict ah4h.holoraw.ds    -c config/test-default    -out_subdir TEST/PREDICT

   test ./prank.sh predict chen11.ds          -c config/test-default    -out_subdir TEST/PREDICT
   test ./prank.sh predict fptrain.ds         -c config/test-default    -out_subdir TEST/PREDICT
   test ./prank.sh predict 'joined(mlig).ds'  -c config/test-default    -out_subdir TEST/PREDICT
   test ./prank.sh predict 'holo4k(mlig).ds'  -c config/test-default    -out_subdir TEST/PREDICT

}

# test prediction with flattened forest
predict_flattened() {

   title PREDICTIONS WITH FLATTENED FOREST

   test ./prank.sh predict joined.ds          -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED
   test ./prank.sh predict holo4k.ds          -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED
   test ./prank.sh predict coach420.ds        -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED
   test ./prank.sh predict ah4h.holoraw.ds    -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED
   
   test ./prank.sh predict chen11.ds          -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED
   test ./prank.sh predict fptrain.ds         -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED
   test ./prank.sh predict 'joined(mlig).ds'  -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED
   test ./prank.sh predict 'holo4k(mlig).ds'  -c config/test-default  -rf_flatten 1   -out_subdir TEST/PREDICT_FLATTENED

}

conservation() {

   title PREDICTIONS USING CONSERVATION

   test ./prank.sh predict joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh predict coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/e5i1/scores' -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh predict holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION

   title EVALUATING PREDICTIONS USING CONSERVATION

   test ./prank.sh eval-predict joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh eval-predict coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/e5i1/scores' -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh eval-predict holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION

   title TRAIN/EVAL USING CONSERVATION

   test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/e5i1/scores' -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION

}



# evaluate default model/settings on main datasets
eval_predict() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test ./prank.sh eval-predict joined.ds       -c config/test-default    -out_subdir TEST/EVAL
    test ./prank.sh eval-predict coach420.ds     -c config/test-default    -out_subdir TEST/EVAL
    test ./prank.sh eval-predict holo4k.ds       -c config/test-default    -out_subdir TEST/EVAL
    # test ./prank.sh predict ah4h.holoraw.ds      -c config/test-default    -out_subdir TEST/PREDICT
    
}


eval_predict_flattened() {

    title EVALUATING PREDICTIONS WITH FLATTENED FOREST

    test ./prank.sh eval-predict joined.ds       -c config/test-default  -rf_flatten 1  -out_subdir TEST/EVAL_FLATTENED
    test ./prank.sh eval-predict coach420.ds     -c config/test-default  -rf_flatten 1  -out_subdir TEST/EVAL_FLATTENED
    test ./prank.sh eval-predict holo4k.ds       -c config/test-default  -rf_flatten 1  -out_subdir TEST/EVAL_FLATTENED

}




eval_predict_uop() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test ./prank.sh eval-predict joined.ds       -c config/test-default  -use_only_positive_score 1  -out_subdir TEST/EVAL_UOP
    test ./prank.sh eval-predict holo4k.ds       -c config/test-default  -use_only_positive_score 1  -out_subdir TEST/EVAL_UOP
    test ./prank.sh eval-predict coach420.ds     -c config/test-default  -use_only_positive_score 1  -out_subdir TEST/EVAL_UOP
    # test ./prank.sh predict ah4h.holoraw.ds      -c config/test-default    -out_subdir TEST/PREDICT

}
eval_predict_flattened_uop() {

    title EVALUATING PREDICTIONS WITH FLATTENED FOREST

    test ./prank.sh eval-predict joined.ds       -c config/test-default  -rf_flatten 1 -use_only_positive_score 1 -out_subdir TEST/EVAL_FLATTENED_UOP
    test ./prank.sh eval-predict holo4k.ds       -c config/test-default  -rf_flatten 1 -use_only_positive_score 1 -out_subdir TEST/EVAL_FLATTENED_UOP
    test ./prank.sh eval-predict coach420.ds     -c config/test-default  -rf_flatten 1 -use_only_positive_score 1 -out_subdir TEST/EVAL_FLATTENED_UOP

}





eval_predict_alphafold() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test ./prank.sh eval-predict joined.ds       -c config/test-alphafold    -out_subdir TEST/EVAL_ALPHAFOLD
    test ./prank.sh eval-predict holo4k.ds       -c config/test-alphafold    -out_subdir TEST/EVAL_ALPHAFOLD
    test ./prank.sh eval-predict coach420.ds     -c config/test-alphafold    -out_subdir TEST/EVAL_ALPHAFOLD

}

# evaluate default model/settings on main datasets
eval_predict_rest() {

    title EVALUATING PREDICTIONS ON OTHER DATASETS

    # train=test for the reference
    test ./prank.sh eval-predict chen11.ds           -c config/test-default   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict fptrain.ds          -c config/test-default   -out_subdir TEST/EVAL

    # -fail_fast 0 because of missing ligands
    test ./prank.sh eval-predict 'joined(mlig).ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test ./prank.sh eval-predict 'coach420(mlig).ds' -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test ./prank.sh eval-predict 'holo4k(mlig).ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL



    #test ./prank.sh eval-predict mlig-moad-nr.ds -c config/test-default -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
    #test ./prank.sh eval-predict moad-nr.ds      -c config/test-default -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
}

eval_rescore() {

    title EVALUATING RESCORING ON ALL DATASETS

    test ./prank.sh eval-rescore joined-fpocket.ds           -c config/test-default  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore coach420-fpocket.ds         -c config/test-default  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore holo4k-fpocket.ds           -c config/test-default  -out_subdir TEST/EVAL
                                                             
    test ./prank.sh eval-rescore chen11-fpocket.ds           -c config/test-default  -out_subdir TEST/EVAL

    # -fail_fast 0 because of missing ligands
    test ./prank.sh eval-rescore 'joined(mlig)-fpocket.ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore 'coach420(mlig)-fpocket.ds' -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore 'holo4k(mlig)-fpocket.ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
}

# train and evaluate new model/settings on main datasets
eval_train() {

    title TRAIN/EVAL ON MAIN DATASETS

    test ./prank.sh crossval chen11-fpocket.ds                     -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds    -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e coach420.ds  -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds    -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN


}

eval_train_rest() {

    title TRAIN/EVAL ON OTHER DATASETS

    # train=test for the reference
    test ./prank.sh traineval -t chen11-fpocket.ds -e chen11-fpocket.ds   -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e fptrain.ds          -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'joined(mlig).ds'   -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'coach420(mlig).ds' -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'holo4k(mlig).ds'   -c config/train-default  -loop 1 -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
}

eval_ploop() {

    title GRID OPTIMIZATION

    test ./prank.sh ploop -t chen11-fpocket.ds -e coach420.ds -c config/train-default -loop 1 -fail_fast 1 -r_generate_plots 0 -rf_trees '(20,40,100)' -rf_features '(6,0)' -out_subdir TEST/PLOOP
    test ./prank.sh ploop -t chen11-fpocket.ds -e speed5.ds   -c config/train-default -loop 1 -fail_fast 1 -r_generate_plots 0 -rf_trees '[10:30:10]' -feature_filters '((-chem.*),(-chem.*,chem.atoms),(protrusion.*,bfactor.*))' -out_subdir TEST/PLOOP

    # test ability to separate normal param features (type: list) and iterative feature_filters (type: list)
    # test feature_filters
    test ./prank.sh ploop -t chen11-fpocket.ds -e speed5.ds   -c config/train-default -loop 1 -fail_fast 1 -r_generate_plots 0 -features '(volsite,bfactor)' -feature_filters '((-volsite.*),(volsite.*,-volsite.vsCation),(volsite.*,bfactor.*))' -out_subdir TEST/PLOOP
}

analyze() {

    title PRINT/ANALYZE COMMANDS

    test ./prank.sh print features     -c config/train-default  -out_subdir TEST/ANALYZE
    test ./prank.sh print model-info   -c config/train-default  -out_subdir TEST/ANALYZE

    test ./prank.sh analyze fasta-masked -f distro/test_data/liganated/1aaxa.pdb -c config/train-default  -out_subdir TEST/ANALYZE

    test ./prank.sh analyze fasta-masked chen11.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked joined.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked coach420.ds            -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked holo4k.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

    test ./prank.sh analyze binding-residues      joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains                joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains-residues       joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-propensities       joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-surf-seq-duplets   joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-surf-seq-triplets  joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-raw             joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

}

transform() {

  title TRANSFORM COMMANDS

  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains A                                                  # output: <out_dir>/2W83_A.cif
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.pdb     -chains A                                                  # output: <out_dir>/2W83_A.pdb
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A,B                                                # output: <out_dir>/2W83_A,B.cif.gz
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A,B  -out_file distro/test_output/2W83_A,B.cif.gz  # output: distro/test_output/2W83_A,B.cif.gz
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains keep                                               # output: <out_dir>/2W83.cif
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains keep -out_format pdb.gz                            # output: <out_dir>/2W83.pdb.gz
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains all                                                # output: <out_dir>/2W83_all.cif
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains A    -out_format keep                              # output: <out_dir>/2W83_A.cif
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A    -out_format pdb.gz                            # output: <out_dir>/2W83_A.pdb.gz
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/2W83.pdb.gz  -chains A,B  -out_format cif                               # output: <out_dir>/2W83_A,B.cif

  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains A
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.pdb     -chains A
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A,B
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A,B  -out_file distro/test_output/1fbl_A,B.cif.gz
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains keep
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains keep -out_format pdb.gz
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains all
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains A    -out_format keep
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A    -out_format pdb.gz
  test ./prank.sh transform reduce-to-chains  -f distro/test_data/1fbl.pdb.gz  -chains A,B  -out_format cif
  
}

classifiers() {

    title TRAIN/EVAL USING DIFFERENT CLASSIFIERS

    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds  -c config/train-default -classifier RandomForest     -label RF   -loop 1 -cache_datasets 0   -out_subdir TEST/CLASSIFIERS
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds  -c config/train-default -classifier FastRandomForest -label FRF  -loop 1 -cache_datasets 0   -out_subdir TEST/CLASSIFIERS
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds  -c config/train-default -classifier FasterForest     -label FF   -loop 1 -cache_datasets 0   -out_subdir TEST/CLASSIFIERS
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds  -c config/train-default -classifier FasterForest2    -label FF2  -loop 1 -cache_datasets 0   -out_subdir TEST/CLASSIFIERS

}

feature_importances() {

    title CALCULATING FEATURE IMPORTANCES

    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default  -feature_importances 1 -classifier RandomForest     -label RF  -loop 1 -cache_datasets 0  -out_subdir TEST/IMPORTANCES
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default  -feature_importances 1 -classifier FastRandomForest -label FRF -loop 1 -cache_datasets 0  -out_subdir TEST/IMPORTANCES
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default  -feature_importances 1 -classifier FasterForest     -label FF  -loop 1 -cache_datasets 0  -out_subdir TEST/IMPORTANCES
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default  -feature_importances 1 -classifier FasterForest2    -label FF2 -loop 1 -cache_datasets 0  -out_subdir TEST/IMPORTANCES
}



###################################################################################################################


quick_train_new() {
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'joined(mlig).ds'   -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'holo4k(mlig).ds'   -c config/new -loop 1   -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds           -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds           -c config/new -loop 1   -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh crossval chen11-fpocket.ds                            -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
}


# evaluate particular config on main datasets
# usage: ./testsets.sh traineval_config <config> <label>
traineval_config() {
    CONFIG=$1
    LABEL=$2
    test ./prank.sh traineval -t chen11.ds -e 'joined(mlig).ds' -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e 'holo4k(mlig).ds' -c ${CONFIG} -loop 3   -cache_datasets 0 -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e joined.ds         -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e holo4k.ds         -c ${CONFIG} -loop 3   -cache_datasets 0 -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh crossval chen11.ds                          -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
}

# evaluate particular config on all datasets
# usage: ./testsets.sh traineval_config_all <config> <label>
traineval_config_all() {
    traineval_config $@

    CONFIG=$1
    LABEL=$2
    test ./prank.sh traineval -t chen11.ds -e fptrain.ds  -c ${CONFIG} -loop 10      -out_subdir EVAL/CONFIG_${LABEL}

}


###################################################################################################################


speed() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "FPTRAIN" "1 2 4 8 16"   "./prank.sh predict fptrain.ds -c config/test-default -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE"   "1"            "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/test-default -out_subdir TEST/SPEED"
}

speed_basic() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "PREDICT"   "1 8"        "./prank.sh predict fptrain.ds -c config/test-default -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default -loop 1 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default -loop 3 -out_subdir TEST/SPEED"
}

speed_ff() {

    title SPEED TESTS - FASTER FOREST

    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default -classifier FasterForest -loop 1 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default -classifier FasterForest -loop 3 -out_subdir TEST/SPEED"
}

speed_ff_quick() {

    title SPEED TESTS - FASTER FOREST

    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/train-default -classifier FasterForest -loop 1 -out_subdir TEST/SPEED"
}

speed_quick() {

    title "SPEED TESTS (QUICK)"

    misc/test-scripts/benchmark.sh 3  "FPTRAIN"   "1 2 4 5 6 8 9 12 13 24"  "./prank.sh predict fptrain.ds -c config/test-default -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                           "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/test-default -out_subdir TEST/SPEED"
}

speed_quick_tes3() {

    title "SPEED TESTS (QUICK)"

    misc/test-scripts/benchmark.sh 3  "FPTRAIN"   "1 2 4 5 6 8 9 12 13 24"  "./prank.sh predict fptrain.ds -c config/test-default -tessellation 3 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                           "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/test-default -tessellation 3 -out_subdir TEST/SPEED"
}

speed_joined_flattening() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 1  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 0  -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 1  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 1  -out_subdir TEST/SPEED"
}

speed_joined_flattening3() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 0  -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 1  -out_subdir TEST/SPEED"
}

speed_joined16() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 3 4 5 6 7 8 9 10 12 15 16 17"  "./prank.sh predict joined.ds -c config/test-default -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                                    "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/test-default -out_subdir TEST/SPEED"
}


dummy_speed() {
    misc/test-scripts/benchmark.sh 10 "JOINED"   "1 2 4 8 12 16 20 24"  "echo dummy"
    misc/test-scripts/benchmark.sh 50 "1FILE" "1"                       "echo dummy"
}

###################################################################################################################

eval_predict_all() {
    eval_predict
    eval_predict_alphafold
    eval_predict_rest
}

eval_train_all() {
    eval_train
    eval_train_rest
}

tests() {
    quick
    basic
}

all() {
    tests
    predict
    conservation
    eval_predict_all
    eval_rescore
    eval_train_all
    classifiers
    feature_importances
    eval_ploop
    analyze
    transform
    speed
}

###################################################################################################################

printcmd() {
    echo
    echo "[$@]"
    echo
    $@ 2>&1 | sed 's/^/    /'
}

print_env() {
    echo
    echo
    echo ENVIRONMENT: ============================================================================================
    printcmd ./distro/prank -version
    printcmd date
    printcmd hostname
    printcmd uname -a
    printcmd java -version
    printcmd lscpu
    printcmd WMIC CPU Get /Format:List
}

###################################################################################################################

run() {
    $ROUTINE $@
}

rm -f $RUN_LOG
rm -f $DEBUG_LOG
rm -f $SUMMARY_LOG
rm -f $ERRORS_LOG

xstart=`date +%s`

print_env >> $SUMMARY_LOG

# colors are stripped from stream that goes to the file
run $@ > >( tee >( sed 's/\x1B\[[0-9;]*[A-Za-z]//g' | sed 's/[\x01-\x1F\x7F]//g' | sed 's/(B//g' >> $SUMMARY_LOG ) )


xend=`date +%s`
runtime=$((xend-xstart))
ftime=`format_time runtime`

echo
echo "ERRORS (from stdout -> see $ERRORS_LOG):"
echo
cat $RUN_LOG | grep --color=always ERROR | tee $ERRORS_LOG | wc -l
echo
echo "ERRORS (from summary):"
echo
cat $SUMMARY_LOG | grep --color=always ERROR | wc -l
echo
cat $SUMMARY_LOG | grep --color=always ERROR

echo "summary saved to [$SUMMARY_LOG]"
echo "stdout went to   [$RUN_LOG]"
echo "stderr went to   [$DEBUG_LOG]"

printf "\nDONE in $ftime\n" | tee -a $SUMMARY_LOG
