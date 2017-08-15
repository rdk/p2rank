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

    start=`date +%s`

    # run command
    # echo error $CMD >> $APPLOG
    $CMD >> $RUN_LOG
    EXIT_CODE=$?

    end=`date +%s`
    runtime=$((end-start))
    ftime=`format_time runtime`

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

    test ./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb               -out_subdir TEST/TESTS
    test ./prank.sh predict test.ds                                               -out_subdir TEST/TESTS
    test ./prank.sh eval-predict -f distro/test_data/liganated/1aaxa.pdb          -out_subdir TEST/TESTS
    test ./prank.sh eval-predict test.ds                                          -out_subdir TEST/TESTS

    test ./prank.sh rescore -f distro/test_data/fpocket/1aaxa_out/1aaxa_out.pdb   -out_subdir TEST/TESTS
    test ./prank.sh rescore fpocket.ds                                            -out_subdir TEST/TESTS
    test ./prank.sh rescore concavity.ds                                          -out_subdir TEST/TESTS
    test ./prank.sh eval-rescore fpocket-pairs.ds                                 -out_subdir TEST/TESTS
    test ./prank.sh eval-rescore concavity-pairs.ds                               -out_subdir TEST/TESTS

    test ./prank.sh traineval -loop 1 -t fpocket-pairs.ds -e test.ds               -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh crossval -loop 1 fpocket-pairs.ds                              -fail_fast 1 -out_subdir TEST/TESTS
}

basic() {

    title RUNNING BASIC TESTS

    test ./prank.sh eval-predict chen11.ds                              -c config/working                        -out_subdir TEST/TESTS
    test ./prank.sh eval-predict mlig-joined.ds                         -c config/working                        -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e chen11-fpocket.ds -c config/working  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e mlig-joined.ds    -c config/working  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh crossval chen11-fpocket.ds                          -c config/working  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS

    #test ./prank.sh eval-predict mlig-joined.ds   -c config/working -visualizations 1 -tessellation 3 -l VISUALIZATIONS_TES3 -c config/working -out_subdir TEST/TESTS
    #test ./prank.sh eval-predict mlig-joined.ds   -c config/working -visualizations 1  -l VISUALIZATIONS                     -c config/working -out_subdir TEST/TESTS
}

# test predieciton on all datasets
predict() {

   title PREDICTIONS ON ALL DATASETS

   test ./prank.sh predict joined.ds       -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict holo4k.ds       -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT

   test ./prank.sh predict chen11.ds       -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict b48.ds          -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict u48.ds          -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict astex.ds        -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict dt198.ds        -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict b210.ds         -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict fptrain.ds      -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict mlig-joined.ds  -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT
   test ./prank.sh predict mlig-holo4k.ds  -c config/workdef -log_cases 1   -out_subdir TEST/PREDICT

}


# evaluate default model/settings on main datasets
eval_predict() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test ./prank.sh eval-predict joined.ds       -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict holo4k.ds       -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
}

# evaluate default model/settings on main datasets
eval_predict_rest() {

    title EVALUATING PREDICTIONS ON OTHER DATASETS

    # train=test for the reference
    test ./prank.sh eval-predict chen11.ds       -c config/workdef -log_cases 1   -out_subdir TEST/EVAL

    test ./prank.sh eval-predict b48.ds          -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict u48.ds          -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict astex.ds        -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict dt198.ds        -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict b210.ds         -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict fptrain.ds      -c config/workdef -log_cases 1   -out_subdir TEST/EVAL

    test ./prank.sh eval-predict mlig-joined.ds  -c config/workdef -log_cases 1   -out_subdir TEST/EVAL
    test ./prank.sh eval-predict mlig-holo4k.ds  -c config/workdef -log_cases 1   -out_subdir TEST/EVAL

    #test ./prank.sh eval-predict mlig-moad-nr.ds -c config/workdef -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
    #test ./prank.sh eval-predict moad-nr.ds      -c config/workdef -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
}

eval_rescore() {

    title EVALUATING RESCORING ON ALL DATASETS

    test ./prank.sh eval-rescore joined-fpocket.ds       -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore holo4k-fpocket.ds       -c config/workdef -log_cases 1  -out_subdir TEST/EVAL

    test ./prank.sh eval-rescore chen11-fpocket.ds       -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore b48-fpocket.ds          -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore u48-fpocket.ds          -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore astex-fpocket.ds        -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore dt198-fpocket.ds        -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore b210-fpocket.ds         -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore fptrain-fpocket.ds      -c config/workdef -log_cases 1  -out_subdir TEST/EVAL

    test ./prank.sh eval-rescore mlig-joined-fpocket.ds  -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
    test ./prank.sh eval-rescore mlig-holo4k-fpocket.ds  -c config/workdef -log_cases 1  -out_subdir TEST/EVAL
}

# train and evaluate new model/settings on main datasets
eval_train() {

    title TRAIN/EVAL ON MAIN DATASETS

    test ./prank.sh crossval chen11-fpocket.ds                         -c config/working -loop 10                    -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds        -c config/working -loop 10                    -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds        -c config/working -loop 3   -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN
}

eval_train_rest() {

    title TRAIN/EVAL ON OTHER DATASETS

    # train=test for the reference
    test ./prank.sh traineval -t chen11-fpocket.ds -e chen11-fpocket.ds -c config/working -loop 10                    -out_subdir TEST/EVAL_TRAIN

    test ./prank.sh traineval -t chen11-fpocket.ds -e b48.ds           -c config/working -loop 10                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e u48.ds           -c config/working -loop 10                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e astex.ds         -c config/working -loop 10                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e dt198.ds         -c config/working -loop 10                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e b210.ds          -c config/working -loop 10                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e fptrain.ds       -c config/working -loop 10                     -out_subdir TEST/EVAL_TRAIN
    
    test ./prank.sh traineval -t chen11-fpocket.ds -e mlig-joined.ds   -c config/working -loop 10                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e mlig-holo4k.ds   -c config/working -loop 3   -cache_datasets 0  -out_subdir TEST/EVAL_TRAIN
}

###################################################################################################################


quick_train_new() {
    test ./prank.sh traineval -t chen11-fpocket.ds -e mlig-joined.ds   -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e      joined.ds   -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e mlig-holo4k.ds   -c config/new -loop 1   -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh traineval -t chen11-fpocket.ds -e      holo4k.ds   -c config/new -loop 1   -cache_datasets 0 -out_subdir TEST/EVAL_TRAIN
    test ./prank.sh crossval chen11-fpocket.ds                         -c config/new -loop 3                     -out_subdir TEST/EVAL_TRAIN
}


# evaluate particular config on main datasets
# usage: ./testsets.sh traineval_config <config> <label>
traineval_config() {
    CONFIG=$1
    LABEL=$2
    test ./prank.sh traineval -t chen11.ds -e mlig-joined.ds   -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e      joined.ds   -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e mlig-holo4k.ds   -c ${CONFIG} -loop 3   -cache_datasets 0 -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e      holo4k.ds   -c ${CONFIG} -loop 3   -cache_datasets 0 -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh crossval chen11.ds                         -c ${CONFIG} -loop 10                    -out_subdir EVAL/CONFIG_${LABEL}
}

# evaluate particular config on all datasets
# usage: ./testsets.sh traineval_config_all <config> <label>
traineval_config_all() {
    traineval_config $@

    CONFIG=$1
    LABEL=$2
    test ./prank.sh traineval -t chen11.ds -e b48.ds      -c ${CONFIG} -loop 10      -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e u48.ds      -c ${CONFIG} -loop 10      -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e astex.ds    -c ${CONFIG} -loop 10      -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e dt198.ds    -c ${CONFIG} -loop 10      -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e b210.ds     -c ${CONFIG} -loop 10      -out_subdir EVAL/CONFIG_${LABEL}
    test ./prank.sh traineval -t chen11.ds -e fptrain.ds  -c ${CONFIG} -loop 10      -out_subdir EVAL/CONFIG_${LABEL}

}


###################################################################################################################


speed() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 5  "U48"   "1 2 4 8 12 16 20 24"  "./prank.sh predict u48.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 25 "1FILE" "1"                    "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}

speed_basic() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "PREDICT"   "1 8"        "./prank.sh predict u48.ds -c config/workdef -out_subdir TEST/SPEED"
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

    misc/test-scripts/benchmark.sh 3  "U48"   "1 2 4 5 8 9 12 13 24"  "./prank.sh predict u48.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                     "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}

speed_joined() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "U48"   "1 2 3 4 5 6 7 8 9 10 11 12 13"  "./prank.sh predict joined.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                              "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}

speed_joined16() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "U48"   "1 2 3 4 5 6 7 8 9 10 12 15 16 17"  "./prank.sh predict joined.ds -c config/workdef -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 10 "1FILE" "1"                                 "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/workdef -out_subdir TEST/SPEED"
}


dummy_speed() {
    misc/test-scripts/benchmark.sh 10 "U48"   "1 2 4 8 12 16 20 24"  "echo dummy"
    misc/test-scripts/benchmark.sh 50 "1FILE" "1"                    "echo dummy"
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
    eval_predict_all
    eval_rescore
    eval_train_all
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
    printcmd ./distro/prank.sh -version
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

# colors are stripped from stream that goes to the file
#run > >( tee >( sed -u 's/\x1B\[[0-9;]*[JKmsu]//g' >> $SUMMARY_LOG ) )
run $@ > >( tee >( sed -u 's/\x1B\[[0-9;]*[JKmsu]//g' >> $SUMMARY_LOG ) )

xend=`date +%s`
runtime=$((xend-xstart))
ftime=`format_time runtime`

printf "\nDONE in $ftime\n" | tee -a $SUMMARY_LOG
print_env >> $SUMMARY_LOG

echo
echo ERRORS:
echo
cat $DEBUG_LOG | grep --color=always ERROR

echo "summary saved to [$SUMMARY_LOG]"
echo "stdout went to [$RUN_LOG]"
echo "stderr went to [$DEBUG_LOG]"
