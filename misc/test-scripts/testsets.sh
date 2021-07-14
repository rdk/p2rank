#!/usr/bin/env bash

###################################################################################################################

ROUTINE=$1
shift
RUN_LOG=local-run.log
DEBUG_LOG=local-debug.log
SUMMARY_LOG=local-summary.log

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

    test ./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb               -out_subdir TEST/TESTS
    test ./prank.sh predict test.ds                                               -out_subdir TEST/TESTS
    test ./prank.sh eval-predict -f distro/test_data/liganated/1aaxa.pdb          -out_subdir TEST/TESTS
    test ./prank.sh eval-predict test.ds                                          -out_subdir TEST/TESTS

    test ./prank.sh rescore fpocket.ds                                            -out_subdir TEST/TESTS
    test ./prank.sh rescore fpocket3.ds                                           -out_subdir TEST/TESTS
    test ./prank.sh rescore concavity.ds                                          -out_subdir TEST/TESTS
    test ./prank.sh eval-rescore fpocket.ds                                       -out_subdir TEST/TESTS
    test ./prank.sh eval-rescore concavity.ds                                     -out_subdir TEST/TESTS

    test ./prank.sh traineval -t fpocket.ds -e test.ds       -loop 1 -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh crossval  fpocket.ds                     -loop 1 -fail_fast 1 -out_subdir TEST/TESTS

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

    test ./prank.sh eval-predict chen11.ds                              -c config/working                        -out_subdir TEST/TESTS
    test ./prank.sh eval-predict 'joined(mlig).ds'                      -c config/working                        -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e chen11-fpocket.ds -c config/working  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'joined(mlig).ds' -c config/working  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh crossval chen11-fpocket.ds                          -c config/working  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS

    #test ./prank.sh eval-predict mlig-joined.ds   -c config/working -visualizations 1 -tessellation 3 -l VISUALIZATIONS_TES3 -c config/working -out_subdir TEST/TESTS
    #test ./prank.sh eval-predict mlig-joined.ds   -c config/working -visualizations 1  -l VISUALIZATIONS                     -c config/working -out_subdir TEST/TESTS
}

# test prediction on all datasets
predict() {

   title PREDICTIONS ON ALL DATASETS

   test ./prank.sh predict joined.ds       -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict holo4k.ds       -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict coach420.ds     -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict ah4h.holoraw.ds     -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT

   test ./prank.sh predict chen11.ds       -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict fptrain.ds      -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict 'joined(mlig).ds'  -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict 'holo4k(mlig).ds'  -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT

}

conservation() {

   title PREDICTIONS USING CONSERVATION

   test ./prank.sh predict joined.ds   -c config/conservation -conservation_dirs 'joined/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh predict coach420.ds -c config/conservation -conservation_dirs 'coach420/conservation/e5i1/scores' -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh predict holo4k.ds   -c config/conservation -conservation_dirs 'holo4k/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION

   title EVALUATING PREDICTIONS USING CONSERVATION

   test ./prank.sh eval-predict joined.ds   -c config/conservation -conservation_dirs 'joined/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh eval-predict coach420.ds -c config/conservation -conservation_dirs 'coach420/conservation/e5i1/scores' -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh eval-predict holo4k.ds   -c config/conservation -conservation_dirs 'holo4k/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION

   title TRAIN/EVAL USING CONSERVATION

   test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds   -c config/conservation -conservation_dirs 'joined/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e coach420.ds -c config/conservation -conservation_dirs 'coach420/conservation/e5i1/scores' -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds   -c config/conservation -conservation_dirs 'holo4k/conservation/e5i1/scores'   -fail_fast 0 -log_cases 1 -visualizations 0 -out_subdir TEST/CONSERVATION

}



# evaluate default model/settings on main datasets
eval_predict() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test ./prank.sh eval-predict joined.ds       -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict holo4k.ds       -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict coach420.ds     -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh predict ah4h.holoraw.ds      -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT

}

# evaluate default model/settings on main datasets
eval_predict_rest() {

    title EVALUATING PREDICTIONS ON OTHER DATASETS

    # train=test for the reference
    test ./prank.sh eval-predict chen11.ds       -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict fptrain.ds      -c config/workdef -log_cases 1   -out_subdir TEST/EVAL

    test ./prank.sh eval-predict 'joined(mlig).ds'   -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict 'coach420(mlig).ds' -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict 'holo4k(mlig).ds'   -c config/workdef -log_cases 1   -out_subdir TEST/EVAL

    #test ./prank.sh eval-predict mlig-moad-nr.ds -c config/workdef -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
    #test ./prank.sh eval-predict moad-nr.ds      -c config/workdef -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
}

eval_rescore() {

    title EVALUATING RESCORING ON ALL DATASETS

    test ./prank.sh eval-rescore joined-fpocket.ds       -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore coach420-fpocket.ds     -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore holo4k-fpocket.ds       -c config/workdef -log_cases 1  -out_subdir TEST/EVAL

    test ./prank.sh eval-rescore chen11-fpocket.ds       -c config/workdef -log_cases 1  -out_subdir TEST/EVAL

    test ./prank.sh eval-rescore 'joined(mlig)-fpocket.ds'   -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore 'coach420(mlig)-fpocket.ds' -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore 'holo4k(mlig)-fpocket.ds'   -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
}

# train and evaluate new model/settings on main datasets
eval_train() {

    title TRAIN/EVAL ON MAIN DATASETS

    test ./prank.sh crossval chen11-fpocket.ds                         -c config/working -loop 1                    -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds        -c config/working -loop 1                    -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e coach420.ds      -c config/working -loop 1                    -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds        -c config/working -loop 1  -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN


}

eval_train_rest() {

    title TRAIN/EVAL ON OTHER DATASETS

    # train=test for the reference
    test ./prank.sh traineval -t chen11-fpocket.ds -e chen11-fpocket.ds   -c config/working -loop 1                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e fptrain.ds          -c config/working -loop 1                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'joined(mlig).ds'   -c config/working -loop 1                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'coach420(mlig).ds' -c config/working -loop 1                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'holo4k(mlig).ds'   -c config/working -loop 1  -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
}

eval_ploop() {

    title GRID OPTIMIZATION

    test ./prank.sh ploop -t chen11-fpocket.ds -e coach420.ds -c config/working -loop 1 -fail_fast 1 -r_generate_plots 0 -rf_trees '(20,40,100)' -rf_features '(6,0)' -out_subdir TEST/PLOOP
    test ./prank.sh ploop -t chen11-fpocket.ds -e speed5.ds   -c config/working -loop 1 -fail_fast 1 -r_generate_plots 0 -rf_trees '[10:30:10]' -feature_filters '((-chem.*),(-chem.*,chem.atoms),(protrusion.*,bfactor.*))' -out_subdir TEST/PLOOP

    # test ability to separate normal param features (type: list) and iterative feature_filters (type: list)
    test ./prank.sh ploop -t chen11-fpocket.ds -e speed5.ds   -c config/working -loop 1 -fail_fast 1 -r_generate_plots 0 -features '(volsite,bfactor)' -feature_filters '((-volsite.*),(volsite.*,-volsite.vsCation),(volsite.*,bfactor.*))' -out_subdir TEST/PLOOP
}

analyze() {

    title PRINT/ANALYZE COMMANDS

    test ./prank.sh print features     -c config/working  -out_subdir TEST/ANALYZE
    test ./prank.sh print model-info   -c config/working  -out_subdir TEST/ANALYZE

    test ./prank.sh analyze fasta-masked -f distro/test_data/liganated/1aaxa.pdb -c config/working  -out_subdir TEST/ANALYZE

    test ./prank.sh analyze fasta-masked chen11.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked joined.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked coach420.ds      -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked holo4k.ds        -c config/working -cache_datasets 0 -out_subdir TEST/ANALYZE

    test ./prank.sh analyze binding-residues      joined.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains                joined.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains-residues       joined.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-propensities       joined.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-surf-seq-duplets   joined.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-surf-seq-triplets  joined.ds        -c config/working                   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-raw             joined.ds        -c config/working                   -out_subdir TEST/ANALYZE

}

classifiers() {

    title TRAIN/EVAL USING DIFFERENT CLASSIFIERS

    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -classifier RandomForest     -label RF    -out_subdir TEST/CLASSIFIETS
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -classifier FastRandomForest -label FRF   -out_subdir TEST/CLASSIFIETS
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -classifier FasterForest     -label FF    -out_subdir TEST/CLASSIFIETS
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -classifier FasterForest2    -label FF2   -out_subdir TEST/CLASSIFIETS

}

feature_importances() {

    title CALCULATING FEATURE IMPORTANCES

    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -feature_importances 1 -classifier RandomForest     -label RF    -out_subdir TEST/IMPORTANCES
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -feature_importances 1 -classifier FastRandomForest -label FRF   -out_subdir TEST/IMPORTANCES
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -feature_importances 1 -classifier FasterForest     -label FF    -out_subdir TEST/IMPORTANCES
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -feature_importances 1 -classifier FasterForest2    -label FF2   -out_subdir TEST/IMPORTANCES
}

###################################################################################################################


quick_train_new() {
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'joined(mlig).ds'   -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'holo4k(mlig).ds'   -c config/new -loop 1   -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e      joined.ds   -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e      holo4k.ds   -c config/new -loop 1   -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh crossval chen11-fpocket.ds                         -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
}


# evaluate particular config on main datasets
# usage: ./testsets.sh traineval_config <config> <label>
traineval_config() {
    CONFIG=$1
    LABEL=$2
    test ./prank.sh traineval -t chen11.ds -e 'joined(mlig).ds'   -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e 'holo4k(mlig).ds'   -c ${CONFIG} -loop 3   -cache_datasets 0 -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e      joined.ds   -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e      holo4k.ds   -c ${CONFIG} -loop 3   -cache_datasets 0 -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh crossval chen11.ds                         -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
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

    misc/test-scripts/benchmark.sh 5  "FPTRAIN" "1 4 6 12"     "./prank.sh predict fptrain.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 15 "1FILE"   "1"            "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}

speed_basic() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "PREDICT"   "1 8"        "./prank.sh predict fptrain.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 1 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -loop 3 -out_subdir TEST/SPEED"
}

speed_ff() {

    title SPEED TESTS - FASTER FOREST

    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -classifier FasterForest -loop 1 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -classifier FasterForest -loop 3 -out_subdir TEST/SPEED"
}

speed_ff_quick() {

    title SPEED TESTS - FASTER FOREST

    misc/test-scripts/benchmark.sh 3  "TRAINEVAL" "1 8"        "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds -c config/working -classifier FasterForest -loop 1 -out_subdir TEST/SPEED"
}

speed_quick() {

    title "SPEED TESTS (QUICK)"

    misc/test-scripts/benchmark.sh 3  "FPTRAIN"   "1 2 4 5 6 8 9 12 13 24"  "./prank.sh predict fptrain.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                           "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}

speed_quick_tes3() {

    title "SPEED TESTS (QUICK)"

    misc/test-scripts/benchmark.sh 3  "FPTRAIN"   "1 2 4 5 6 8 9 12 13 24"  "./prank.sh predict fptrain.ds -c config/workdef -tessellation 3 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                           "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -tessellation 3 -out_subdir TEST/SPEED"
}

speed_joined() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 3 4 5 6 7 8 9 10 11 12 13"   "./prank.sh predict joined.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                                  "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}

speed_joined16() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 3 4 5 6 7 8 9 10 12 15 16 17"  "./prank.sh predict joined.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                                    "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}


dummy_speed() {
    misc/test-scripts/benchmark.sh 10 "JOINED"   "1 2 4 8 12 16 20 24"  "echo dummy"
    misc/test-scripts/benchmark.sh 50 "1FILE" "1"                       "echo dummy"
}

###################################################################################################################

eval_predict_all() {
    eval_predict
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

rm $RUN_LOG
rm $DEBUG_LOG
rm $SUMMARY_LOG

xstart=`date +%s`

print_env >> $SUMMARY_LOG

# colors are stripped from stream that goes to the file
#run > >( tee >( sed -u 's/\x1B\[[0-9;]*[JKmsu]//g' >> $SUMMARY_LOG ) )
#run $@ > >( tee >( sed -u 's/\x1B\[[0-9;]*[JKmsu]//g' >> $SUMMARY_LOG ) )
run $@ > >( tee >( sed -u 's/\x1B\[[0-9;]\{1,\}[A-Za-z]//g' >> $SUMMARY_LOG ) )

xend=`date +%s`
runtime=$((xend-xstart))
ftime=`format_time runtime`

printf "\nDONE in $ftime\n" | tee -a $SUMMARY_LOG

echo
echo "ERRORS (from stdout):"
echo
cat $RUN_LOG | grep --color=always ERROR
echo
echo "ERRORS (from summary):"
echo
cat $SUMMARY_LOG | grep --color=always ERROR

echo "summary saved to [$SUMMARY_LOG]"
echo "stdout went to [$RUN_LOG]"
echo "stderr went to [$DEBUG_LOG]"
