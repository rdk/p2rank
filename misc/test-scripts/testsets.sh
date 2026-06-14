#!/usr/bin/env bash

###################################################################################################################

# Args are parsed near the bottom (the "run" section), after all routine
# functions are defined -- so a token can be recognised as a routine name via
# `declare -F`. See the usage comment there.

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

###################################################################################################################
# BURST = distro/prank_burst -- used ONLY for single-protein commands (predict / eval-predict /
# export-points / analyze / transform on one -f input file). Its AppCDS + C1-only JIT give the
# lowest startup/warmup cost, which wins when the whole run is over in seconds. Override to
# compare, e.g. BURST=./prank.sh or BURST=distro/prank.
#
# Everything else (datasets, multi-protein predict/eval/rescore, training, ploop) calls ./prank.sh
# directly: its local-env config (big heap + full C2 + threads) is what those longer runs need,
# and prank_burst's 2g heap + C1-only JIT would undersell them.
BURST="${BURST:-distro/prank_burst}"
###################################################################################################################

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
    CMD="$@ ${EXTRA_PARAMS[*]}"

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

# Like test() but asserts the command must FAIL. Used for fail-fast validation
# checks (bad params, missing prerequisites) where a non-zero exit code is the
# success criterion. Green [OK] means the command failed as expected; red
# [ERROR] means it unexpectedly succeeded.
test_expect_fail() {
    CMD="$@ ${EXTRA_PARAMS[*]}"

    echo "${reset}testing command (expect fail) [${blue}$CMD${reset}]${reset}"

    start=$(date +%s)

    $CMD &>> $RUN_LOG
    EXIT_CODE=$?

    end=$(date +%s)
    runtime=$((end-start))
    ftime=$(format_time runtime)

    if [[ $EXIT_CODE != 0 ]]; then
        echo "${green}[OK]${reset} (expected fail, exit code: $EXIT_CODE)" "time: $ftime"
    else
        echo "${red}[ERROR]${reset} (expected fail but command succeeded)" "time: $ftime"
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

    test $BURST predict -f distro/test_data/1fbl.pdb                          -out_subdir TEST/TESTS
    test $BURST predict -f distro/test_data/1fbl.pdb.gz                       -out_subdir TEST/TESTS
    test $BURST predict -f distro/test_data/1fbl.pdb.zst -c alphafold         -out_subdir TEST/TESTS
    test $BURST predict -f distro/test_data/1fbl.cif     -c alphafold         -out_subdir TEST/TESTS
    test $BURST predict -f distro/test_data/1fbl.cif.gz  -c alphafold         -out_subdir TEST/TESTS
    test $BURST predict -f distro/test_data/1fbl.cif.zst                      -out_subdir TEST/TESTS
    test ./prank.sh predict test.ds                                               -out_subdir TEST/TESTS
    test $BURST eval-predict -f distro/test_data/liganated/1aaxa.pdb          -out_subdir TEST/TESTS
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

    # test export_points feature
    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv.gz     -out_subdir TEST/TESTS

    # test export-points command (no model)
    test $BURST export-points -f distro/test_data/1fbl.pdb -export_points_format csv.gz                -out_subdir TEST/TESTS

    # test pocket grid + descriptors + PyMOL viz (all three exports in one go)
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -export_pocket_descriptors 1 -vis_pocket_grid 1     -out_subdir TEST/TESTS

    # cofactors feature smoke test
    test $BURST predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA                         -out_subdir TEST/TESTS
    test $BURST analyze cofactors -f distro/test_data/liganated/1t7qa.pdb                              -out_subdir TEST/TESTS

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
# doesn't make sense since default is already flat
#predict_flattened() {
#
#   title PREDICTIONS WITH FLATTENED FOREST
#
#   test ./prank.sh predict joined.ds         -c config/test-default  -rf_flatten 1  -rf_flatten_target FlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test ./prank.sh predict holo4k.ds         -c config/test-default  -rf_flatten 1  -rf_flatten_target LegacyFlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test ./prank.sh predict coach420.ds       -c config/test-default  -rf_flatten 1  -rf_flatten_target ShortFlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test ./prank.sh predict ah4h.holoraw.ds   -c config/test-default  -rf_flatten 1  -rf_flatten_target SuperShortLegacyFlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#
#   test ./prank.sh predict chen11.ds         -c config/test-default  -rf_flatten 1  -rf_flatten_target FlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test ./prank.sh predict fptrain.ds        -c config/test-default  -rf_flatten 1  -rf_flatten_target FlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test ./prank.sh predict 'joined(mlig).ds' -c config/test-default  -rf_flatten 1  -rf_flatten_target InterleavedBfsForest -out_subdir TEST/PREDICT_FLATTENED
#   test ./prank.sh predict 'holo4k(mlig).ds' -c config/test-default  -rf_flatten 1  -rf_flatten_target InterleavedBfsForest -out_subdir TEST/PREDICT_FLATTENED
#
#}


conservation() {

   title PREDICTIONS USING CONSERVATION

   test ./prank.sh predict joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh predict coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/hmm/scores' -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh predict holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION

   title EVALUATING PREDICTIONS USING CONSERVATION

   test ./prank.sh eval-predict joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh eval-predict coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/hmm/scores' -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh eval-predict holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION

   title TRAIN/EVAL USING CONSERVATION

   test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/hmm/scores' -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION

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

    test ./prank.sh eval-predict joined.ds       -c config/test-default  -rf_flatten 1 -rf_flatten_target FlatBinaryForest  -out_subdir TEST/EVAL_FLATTENED
    test ./prank.sh eval-predict coach420.ds     -c config/test-default  -rf_flatten 1 -rf_flatten_target FlatBinaryForest  -out_subdir TEST/EVAL_FLATTENED
    test ./prank.sh eval-predict holo4k.ds       -c config/test-default  -rf_flatten 1 -rf_flatten_target FlatBinaryForest  -out_subdir TEST/EVAL_FLATTENED -l FlatBinaryForest

    test ./prank.sh eval-predict holo4k.ds       -c config/test-default  -rf_flatten 1 -rf_flatten_target NativePanamaForest  -out_subdir TEST/EVAL_FLATTENED_NATIVE
}


# Quick 2x3 comparison on holo4k along two axes (6 predict runs total):
#   surface strategy: latest (packed_distinct_v4, the default) vs the legacy "faster" engine
#                     (FasterNumericalSurface, the pre-distinct default up to mid-2026). The distinct
#                     engine yields the same SAS points as faster after sparsification, so pockets match.
#   flattened forest: legacy faithful (LegacyFlatBinaryForest, "as before") vs its SoA layout
#                     (SoaLegacyFlatBinaryForest, bit-exact) vs the int16/uint16-quantized SoA legacy
#                     (Int16LeafSoaLegacyFlatBinaryForest).
# All forest variants pass -rf_flatten 1 so each run pays the flatten cost -- a fair timing comparison
# (the int16 variant is ranking-equivalent to legacy, safe with the default threshold).
# Uses ./prank.sh (big heap + full JIT, -threads 16), NOT prank_burst: holo4k is large and this is
# a heavy compute benchmark -- same rationale as surface_strategies/pocket_grid_bench. Not in all().
quick_compare() {

    title QUICK COMPARE - SURFACE STRATEGY x FLATTEN ON HOLO4K [./prank.sh]

    local surfaces="packed_distinct_v4 faster"
    local flatten_targets="LegacyFlatBinaryForest SoaLegacyFlatBinaryForest Int16LeafSoaLegacyFlatBinaryForest"

    for surf in $surfaces; do
        for flat in $flatten_targets; do
            test ./prank.sh predict holo4k.ds  -c config/test-default  -surface_strategy $surf  -rf_flatten 1 -rf_flatten_target $flat  -threads 16  -cache_datasets 0  -l ${surf}__${flat}  -out_subdir TEST/QUICK_COMPARE
        done
    done
}

# Same 2x3 holo4k matrix as quick_compare(), but driven by the distro/prank launcher
# (standard distro JVM settings: default heap, full JIT) instead of ./prank.sh's local-env
# (big heap + tuned flags). Separate -out_subdir so the two launchers' outputs don't collide.
# Not in all().
quick_compare_distro() {

    title QUICK COMPARE - SURFACE STRATEGY x FLATTEN ON HOLO4K [distro/prank]

    local surfaces="packed_distinct_v4 faster"
    local flatten_targets="LegacyFlatBinaryForest SoaLegacyFlatBinaryForest Int16LeafSoaLegacyFlatBinaryForest"

    for surf in $surfaces; do
        for flat in $flatten_targets; do
            test distro/prank predict holo4k.ds  -c config/test-default  -surface_strategy $surf  -rf_flatten 1 -rf_flatten_target $flat  -threads 16  -cache_datasets 0  -l ${surf}__${flat}  -out_subdir TEST/QUICK_COMPARE_DISTRO
        done
    done
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

fpocket_rescore() {

    title "EVALUATING FPOCKT-RESCORE (RUNNING FPOCKET AD-HOC)"

    test ./prank.sh fpocket-rescore joined.ds           -c config/test-default  -out_subdir TEST/RESCORE
    test ./prank.sh fpocket-rescore coach420.ds         -c config/test-default  -out_subdir TEST/RESCORE
    test ./prank.sh fpocket-rescore holo4k.ds           -c config/test-default  -out_subdir TEST/RESCORE
    test ./prank.sh fpocket-rescore chen11.ds           -c config/test-default  -out_subdir TEST/RESCORE

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

    test $BURST analyze fasta-masked -f distro/test_data/liganated/1aaxa.pdb -c config/train-default  -out_subdir TEST/ANALYZE

    test ./prank.sh analyze fasta-masked chen11.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked joined.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked coach420.ds            -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-masked holo4k.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

    test ./prank.sh analyze residues              joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze binding-residues      joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze labeled-residues      joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains                joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains-residues       joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-propensities       joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-surf-seq-duplets   joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze aa-surf-seq-triplets  joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze fasta-raw             joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

    test ./prank.sh analyze residues              holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze binding-residues      holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze labeled-residues      holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains                holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze chains-residues       holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

    test ./prank.sh analyze cofactors             joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test ./prank.sh analyze cofactors             holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

}


# benchmark + bit-equality-verify all surface strategies (cdk|faster|packed) on the major datasets.
# Optional args override the dataset list (default: the 5 major datasets), e.g. surface_strategies fptrain.ds
surface_strategies() {

    title SURFACE STRATEGIES BENCHMARK AND EQUALITY

    local datasets="${@:-chen11.ds fptrain.ds coach420.ds joined.ds holo4k.ds}"

    # Uses ./prank.sh (local-env big heap + full C2/Graal JIT), NOT prank_burst: this is a heavy
    # compute benchmark, and the command pre-loads all proteins (large datasets like holo4k need the
    # big heap). prank_burst's C1-only JIT and 2g heap would undersell the optimized strategies.
    for ds in $datasets; do
        test ./prank.sh analyze surface-strategies $ds  -c config/test-default  -threads 16  -cache_datasets 0  -out_subdir TEST/SURFACE_STRATEGIES
    done
}


# density stats (point redundancy, mesh spacing, sparsification reduction) of EACH surface strategy
# on the major datasets (mlig variants excluded). surface-density analyzes one strategy per run
# (selected via -surface_strategy), so we loop over every strategy x every dataset. Not in all().
# Optional args override the dataset list (default: the 5 major datasets), e.g. surface_density fptrain.ds
surface_density() {

    title SURFACE DENSITY PER STRATEGY

    # ./prank.sh (big heap + full JIT) for the heavy compute / full C2 JIT, same as surface_strategies.
    # NOTE: unlike surface-strategies, surface-density streams proteins (processItems, ~threads loaded
    # at once) and keeps only a small per-protein density record, so it does NOT need the big heap for
    # memory -- the local-env launcher is used here only for the JIT/thread tuning. Output is segregated
    # per strategy so runs don't overwrite each other.
    local strategies="cdk faster packed faster_distinct packed_distinct"
    local datasets="${@:-chen11.ds fptrain.ds coach420.ds joined.ds holo4k.ds}"

    for strat in $strategies; do
        for ds in $datasets; do
            test ./prank.sh analyze surface-density $ds  -surface_strategy $strat  -c config/test-default  -threads 16  -cache_datasets 0  -out_subdir TEST/SURFACE_DENSITY/$strat
        done
    done
}


# all surface analyses: strategy benchmark/equality + per-strategy density. Not in all() -- opt-in.
surfaces() {
    surface_strategies
    surface_density
}


# same as surfaces() but only on the smallest dataset (fptrain) for a quick check. Not in all().
surfaces_fast() {
    surface_strategies fptrain.ds
    surface_density fptrain.ds
}


cofactors() {

    title COFACTORS FEATURE

    # analyze cofactors: survey mode (no -cofactors) on file + dataset
    test $BURST analyze cofactors -f distro/test_data/liganated/1t7qa.pdb                                          -out_subdir TEST/COFACTORS
    test ./prank.sh analyze cofactors test.ds                                                                          -out_subdir TEST/COFACTORS

    # analyze cofactors: dry-run mode with each specifier form
    test $BURST analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors COA                           -out_subdir TEST/COFACTORS
    test $BURST analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors 'COA[atom_id:9551]'           -out_subdir TEST/COFACTORS
    test $BURST analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors 'COA[contact_res_ids:A_K258]' -out_subdir TEST/COFACTORS
    test $BURST analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors ZZZZ                          -out_subdir TEST/COFACTORS

    # predict with cofactors
    test $BURST predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA                                     -out_subdir TEST/COFACTORS
    # R18: never-present specifier must be a no-op (drop-in safety)
    test $BURST predict -f distro/test_data/1fbl.pdb -cofactors ZZZZ                                               -out_subdir TEST/COFACTORS
    # R22: case-mismatched name must still match (parser auto-uppercases the group name)
    test $BURST predict -f distro/test_data/liganated/1t7qa.pdb -cofactors coa                                     -out_subdir TEST/COFACTORS
    # R22: contact_res_ids must survive comma-splitting (bracket-aware parse)
    test $BURST predict -f distro/test_data/liganated/1t7qa.pdb -cofactors 'COA[contact_res_ids:A_K258,A_D246]'    -out_subdir TEST/COFACTORS

    # knobs
    test $BURST predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA -cofactor_max_protein_dist 0        -out_subdir TEST/COFACTORS
    test $BURST predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA -vis_highlight_cofactors 0          -out_subdir TEST/COFACTORS

    # R19: aa_mapping collision warning (MSE is in built-in minimal mapping)
    test $BURST predict -f distro/test_data/1fbl.pdb -cofactors MSE                                                -out_subdir TEST/COFACTORS

    # drop-in safety benchmark
    test ./benchmark/cofactors_dropin_safety.sh distro/test_data/concavity.ds
}

cofactors_full() {

    title COFACTOR DATASETS

    local DS_BASE="../p2rank-datasets2/other/cofactors"
    if [ ! -f "$DS_BASE/cofactors-demo-cif.ds" ]; then
        echo "  [SKIP] $DS_BASE/cofactors-demo-cif.ds not found"
        return
    fi

    # --- A. predict on the demo dataset, both formats ---
    test ./prank.sh predict cofactors-demo-cif.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS
    test ./prank.sh predict cofactors-demo-pdb.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS

    # --- B. baseline runs (no cofactors column) for delta comparison ---
    test ./prank.sh predict cofactors-demo-baseline-cif.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS
    test ./prank.sh predict cofactors-demo-baseline-pdb.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS

    # --- C. analyze cofactors on the demo dataset (matches.csv, summary) ---
    test ./prank.sh analyze cofactors cofactors-demo-cif.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS
    test ./prank.sh analyze cofactors cofactors-demo-baseline-cif.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS

    # --- D. combined with -aa_mapping pdbfixer (R19 collision on TPQ family) ---
    test ./prank.sh predict cofactors-demo-cif.ds -dataset_base_dir $DS_BASE  -aa_mapping pdbfixer  -out_subdir TEST/COF_DS

    # --- E. visualization spot-check on two well-known cofactors ---
    test $BURST predict -f $DS_BASE/structures/pdb/4REK.pdb -cofactors FAD  -visualizations 1  -out_subdir TEST/COF_DS
    test $BURST predict -f $DS_BASE/structures/pdb/4BEU.pdb -cofactors PLP  -visualizations 1  -out_subdir TEST/COF_DS

    # --- F. export-points (cofactor atoms must propagate into the points output) ---
    test $BURST export-points -f $DS_BASE/structures/pdb/4REK.pdb -cofactors FAD  -out_subdir TEST/COF_DS

    # --- G. full-dataset stress test (523 structures across 43 cofactor codes) ---
    if [ -f "$DS_BASE/cofactors-full.ds" ]; then
        test ./prank.sh predict cofactors-full.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS_FULL
        test ./prank.sh predict cofactors-full-baseline.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS_FULL
        test ./prank.sh analyze cofactors cofactors-full.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS_FULL
    fi
}

aa_mapping() {

    title AA MAPPING FEATURE

    # bundled pdbfixer preset (87-entry table)
    test $BURST predict -f distro/test_data/1fbl.pdb                                                                       -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING

    # dataset paths (predict / rescore / eval)
    test ./prank.sh predict test.ds                                                                                            -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING
    test ./prank.sh rescore fpocket.ds                                                                                         -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING
    test $BURST eval-predict -f distro/test_data/liganated/1aaxa.pdb                                                       -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING
}

transform() {

  title TRANSFORM COMMANDS

  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains A                                                  # output: <out_dir>/2W83_A.cif
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.pdb     -chains A                                                  # output: <out_dir>/2W83_A.pdb
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A,B                                                # output: <out_dir>/2W83_A,B.cif.gz
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A,B  -out_file distro/test_output/2W83_A,B.cif.gz  # output: distro/test_output/2W83_A,B.cif.gz
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains keep                                               # output: <out_dir>/2W83.cif
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains keep -out_format pdb.gz                            # output: <out_dir>/2W83.pdb.gz
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains all                                                # output: <out_dir>/2W83_all.cif
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains A    -out_format keep                              # output: <out_dir>/2W83_A.cif
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A    -out_format pdb.gz                            # output: <out_dir>/2W83_A.pdb.gz
  test $BURST transform reduce-to-chains  -f distro/test_data/2W83.pdb.gz  -chains A,B  -out_format cif                               # output: <out_dir>/2W83_A,B.cif

  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains A
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.pdb     -chains A
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A,B
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A,B  -out_file distro/test_output/1fbl_A,B.cif.gz
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains keep
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains keep -out_format pdb.gz
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains all
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains A    -out_format keep
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A    -out_format pdb.gz
  test $BURST transform reduce-to-chains  -f distro/test_data/1fbl.pdb.gz  -chains A,B  -out_format cif

}

export_points() {

    title EXPORT POINTS FEATURE

    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv       -out_subdir TEST/EXPORT_POINTS
    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv.gz    -out_subdir TEST/EXPORT_POINTS
    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv.zst   -out_subdir TEST/EXPORT_POINTS
    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format arrow     -out_subdir TEST/EXPORT_POINTS
    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format arrow.gz  -out_subdir TEST/EXPORT_POINTS
    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format arrow.zst -out_subdir TEST/EXPORT_POINTS
    test $BURST predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format parquet   -out_subdir TEST/EXPORT_POINTS

    # export-points command (no model needed)
    test $BURST export-points -f distro/test_data/2W83.cif -extra_features 'surface_protrusion' -export_points_format arrow -out_subdir TEST/EXPORT_POINTS
    test $BURST export-points -f distro/test_data/1fbl.pdb                                              -out_subdir TEST/EXPORT_POINTS
    test $BURST export-points -f distro/test_data/1fbl.pdb -export_points_format csv.gz                 -out_subdir TEST/EXPORT_POINTS
    test $BURST export-points -f distro/test_data/1fbl.pdb -export_points_format csv.zst                -out_subdir TEST/EXPORT_POINTS
    test $BURST export-points -f distro/test_data/1fbl.pdb -export_points_format arrow                  -out_subdir TEST/EXPORT_POINTS
    test $BURST export-points -f distro/test_data/1fbl.pdb -export_points_format arrow.gz               -out_subdir TEST/EXPORT_POINTS
    test $BURST export-points -f distro/test_data/1fbl.pdb -export_points_format arrow.zst              -out_subdir TEST/EXPORT_POINTS
    test $BURST export-points -f distro/test_data/1fbl.pdb -export_points_format parquet                -out_subdir TEST/EXPORT_POINTS

    # predict/rescore on datasets
    test ./prank.sh predict chen11.ds            -c config/test-default  -export_points 1 -export_points_format arrow.zst -out_subdir TEST/EXPORT_POINTS
    test ./prank.sh rescore coach420-fpocket.ds  -c config/test-default  -export_points 1 -export_points_format csv.zst   -out_subdir TEST/EXPORT_POINTS
    test ./prank.sh rescore chen11-fpocket.ds    -c config/test-default  -export_points 1 -export_points_format parquet   -out_subdir TEST/EXPORT_POINTS

    # export-points command  on datasets
    test ./prank.sh export-points chen11.ds            -c config/test-default  -export_points_format arrow.zst -out_subdir TEST/EXPORT_POINTS
    test ./prank.sh export-points coach420-fpocket.ds  -c config/test-default  -export_points_format csv.zst   -out_subdir TEST/EXPORT_POINTS
    test ./prank.sh export-points chen11-fpocket.ds    -c config/test-default  -export_points_format parquet   -out_subdir TEST/EXPORT_POINTS
}

pocket_grid() {

    title POCKET GRID + DESCRIPTORS EXPORT

    # Basic combinations on a single file
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1                                                                                            -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1                                                                                     -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -export_pocket_descriptors 1                                                               -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -vis_pocket_grid 1                                                                  -out_subdir TEST/POCKET_GRID

    # All grid file formats
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format csv                                                                    -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format csv.gz                                                                 -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format csv.zst                                                                -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format arrow                                                                  -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format arrow.gz                                                               -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format arrow.zst                                                              -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format parquet                                                                -out_subdir TEST/POCKET_GRID

    # Full descriptor menu (all seven shipped, including principal_moments)
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1 -pocket_descriptors 'volume,sphericity,radius_of_gyration,num_residues,num_surface_atoms,num_grid_points,principal_moments'  -out_subdir TEST/POCKET_GRID

    # Per-grid-point descriptors (volsite, volsite_smooth), each at least once
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite'                                                       -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite_smooth'                                                -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite,volsite_smooth'                                        -out_subdir TEST/POCKET_GRID

    # Per-grid-point descriptor knobs
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite'        -pocket_grid_volsite_radius 5.0                 -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite_smooth' -pocket_grid_volsite_sigma 1.5                  -out_subdir TEST/POCKET_GRID

    # Full combo: per-pocket descriptors + per-grid-point descriptors + viz in one run
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_grid_point_descriptors 'volsite,volsite_smooth' -vis_pocket_grid 1   -out_subdir TEST/POCKET_GRID

    # Grid-free descriptors only — exercises the grid-build short-circuit (no
    # "PocketGrid built" log line should appear for these invocations).
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1 -pocket_descriptors 'num_residues,num_surface_atoms' -export_pocket_grid 0           -out_subdir TEST/POCKET_GRID

    # Fill strategies (only morph_closing and none are valid; convex_hull is rejected at startup)
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_fill morph_closing                                                            -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_fill none                                                                     -out_subdir TEST/POCKET_GRID

    # Knobs: include unassigned, tighter spacing, larger reach, custom atom buffer
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_include_unassigned 1                                                          -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_spacing 0.75                                                                  -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_max_dist 8                                                                    -out_subdir TEST/POCKET_GRID
    test $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_atom_buffer 1.0                                                               -out_subdir TEST/POCKET_GRID

    # Validation: fail-fast checks (each must exit non-zero — uses test_expect_fail so the green [OK] is shown only when the command fails as designed)
    test_expect_fail $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_fill cubist                                                                   -out_subdir TEST/POCKET_GRID
    test_expect_fail $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1 -pocket_descriptors 'not_a_descriptor'                                              -out_subdir TEST/POCKET_GRID
    test_expect_fail $BURST predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'not_a_descriptor'                                         -out_subdir TEST/POCKET_GRID
    test_expect_fail $BURST predict -f distro/test_data/1fbl.pdb -vis_pocket_grid 1                                                                                        -out_subdir TEST/POCKET_GRID

    # Rescore + dataset paths
    test ./prank.sh rescore  fpocket.ds  -export_pocket_grid 1 -export_pocket_descriptors 1                                                                               -out_subdir TEST/POCKET_GRID
    test ./prank.sh predict  test.ds     -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_grid_format parquet                                                   -out_subdir TEST/POCKET_GRID

    # Cofactor interaction — cofactor atoms must show up in the grid's exclusion zone
    test $BURST predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA -export_pocket_grid 1 -export_pocket_descriptors 1                                     -out_subdir TEST/POCKET_GRID


    # Single timed run per dataset at the env default thread count (PRANK_LOCALENV_PARAMS,
    # typically -threads 16). No -threads override here on purpose — measures real
    # end-to-end wall time on each dataset rather than isolating per-protein cost.
    test ./prank.sh predict joined.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    test ./prank.sh predict holo4k.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    test ./prank.sh predict coach420.ds   -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    test ./prank.sh predict chen11.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    #test ./prank.sh predict fptrain.ds    -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID

    test ./prank.sh predict fptrain.ds    -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID


}

pocket_grid_bench() {

    title POCKET GRID BENCHMARK

    test ./prank.sh predict joined.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test ./prank.sh predict holo4k.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test ./prank.sh predict coach420.ds   -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test ./prank.sh predict chen11.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test ./prank.sh predict fptrain.ds    -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH

}

# Long-running sweep: full descriptor menu (per-pocket + per-grid-point) on the
# 4 main datasets with visualizations enabled. Not part of all() — opt-in only.
pocket_grid_long() {

    title POCKET GRID LONG - ALL DESCRIPTORS ON MAIN DATASETS

    PG_DESCRIPTORS='volume,sphericity,radius_of_gyration,num_residues,num_surface_atoms,num_grid_points,principal_moments'
    PG_POINT_DESCRIPTORS='volsite,volsite_smooth'

    test ./prank.sh predict joined.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG
    test ./prank.sh predict holo4k.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG
    test ./prank.sh predict coach420.ds   -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG
    test ./prank.sh predict chen11.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG

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

    misc/test-scripts/benchmark.sh 1  "FPTRAIN" "1 2 4 8 16"   "./prank.sh predict fptrain.ds -c config/test-default -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 5  "1FILE"   "1"            "./prank.sh predict -f distro/test_data/liganated/1aaxa.pdb -c config/test-default -out_subdir TEST/SPEED"
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

    misc/test-scripts/benchmark.sh 1  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 0 -rf_batch_prediction 0 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 1  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 1 -rf_batch_prediction 0 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 1  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 0 -rf_batch_prediction 1 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 1  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 1 -rf_batch_prediction 1 -out_subdir TEST/SPEED"
}

speed_joined_flattening3() {

    title SPEED TESTS

    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 0 -rf_batch_prediction 0 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 1 -rf_batch_prediction 0 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 0 -rf_batch_prediction 1 -out_subdir TEST/SPEED"
    misc/test-scripts/benchmark.sh 3  "JOINED"   "1 2 4 8 12 16 17 18"   "./prank.sh predict joined.ds -c config/test-default  -rf_flatten 1 -rf_batch_prediction 1 -out_subdir TEST/SPEED"
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
    cofactors
    cofactors_full
    aa_mapping
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
    export_points
    pocket_grid
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
    printcmd echo $JAVA_HOME
    printcmd lscpu
    printcmd WMIC CPU Get /Format:List
}

###################################################################################################################

run() {
    local routine="" tok
    local -a args=()
    for tok in "${ROUTINE_TOKENS[@]}"; do
        if declare -F "$tok" > /dev/null; then
            # a defined function starts a new routine; dispatch the previous one first
            [[ -n "$routine" ]] && { title "ROUTINE: $routine ${args[*]}"; "$routine" "${args[@]}"; }
            routine="$tok"; args=()
        elif [[ -n "$routine" ]]; then
            args+=("$tok")                       # positional arg for the current routine
        else
            echo "${red}unknown routine: '$tok'${reset}" >&2; exit 1
        fi
    done
    if [[ -n "$routine" ]]; then
        title "ROUTINE: $routine ${args[*]}"; "$routine" "${args[@]}"
    else
        echo "${red}no routine specified${reset}" >&2; exit 1
    fi
}

rm -f $RUN_LOG
rm -f $DEBUG_LOG
rm -f $SUMMARY_LOG
rm -f $ERRORS_LOG

xstart=`date +%s`

print_env >> $SUMMARY_LOG

# Parse args here, after all routine functions are defined:
#   ./testsets.sh <routine> [args...] [<routine> [args...]] ... [p2rank params...]
# One or more routines run sequentially (a token naming a function starts a new
# routine; following non-option tokens are that routine's positional args, e.g.
# datasets or config + label). The routine section ends at the first option-like
# token (starting with `-`) or an explicit `--`; from there on, everything plus
# $EXTRA is appended to every p2rank call (see test()) and overrides per-command
# defaults. So just append the flags -- no separator needed:
#   ./testsets.sh quick basic -threads 8 -fail_fast 0
# `--` is only needed to force the split for the rare routine arg that starts with `-`.
ROUTINE_TOKENS=()
while [[ $# -gt 0 && "$1" != "--" && "$1" != -* ]]; do ROUTINE_TOKENS+=("$1"); shift; done
[[ "$1" == "--" ]] && shift
EXTRA_PARAMS=($EXTRA "$@")

# colors are stripped from stream that goes to the file
run > >( tee >( sed 's/\x1B\[[0-9;]*[A-Za-z]//g' | sed 's/[\x01-\x1F\x7F]//g' | sed 's/(B//g' >> $SUMMARY_LOG ) )


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
