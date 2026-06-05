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

###################################################################################################################
# Launcher for short prediction-class commands (predict / eval-predict / rescore / analyze /
# transform / export-points / print). distro/prank_faster (AppCDS + C1-only JIT) starts faster
# than ./prank.sh on these short runs, and predictions are identical. Long training/eval
# (traineval / crossval / ploop) deliberately stays on ./prank.sh, whose local-env config
# (big heap + full C2 + threads) is what those long runs need. Override to compare, e.g.
# FASTER=./prank.sh or FASTER=distro/prank.
FASTER="${FASTER:-distro/prank_faster}"
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

# Like test() but asserts the command must FAIL. Used for fail-fast validation
# checks (bad params, missing prerequisites) where a non-zero exit code is the
# success criterion. Green [OK] means the command failed as expected; red
# [ERROR] means it unexpectedly succeeded.
test_expect_fail() {
    CMD="$@"

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

    test $FASTER predict -f distro/test_data/1fbl.pdb                          -out_subdir TEST/TESTS
    test $FASTER predict -f distro/test_data/1fbl.pdb.gz                       -out_subdir TEST/TESTS
    test $FASTER predict -f distro/test_data/1fbl.pdb.zst -c alphafold         -out_subdir TEST/TESTS
    test $FASTER predict -f distro/test_data/1fbl.cif     -c alphafold         -out_subdir TEST/TESTS
    test $FASTER predict -f distro/test_data/1fbl.cif.gz  -c alphafold         -out_subdir TEST/TESTS
    test $FASTER predict -f distro/test_data/1fbl.cif.zst                      -out_subdir TEST/TESTS
    test $FASTER predict test.ds                                               -out_subdir TEST/TESTS
    test $FASTER eval-predict -f distro/test_data/liganated/1aaxa.pdb          -out_subdir TEST/TESTS
    test $FASTER eval-predict test.ds                                          -out_subdir TEST/TESTS

    test $FASTER rescore fpocket.ds                                            -out_subdir TEST/TESTS
    test $FASTER rescore fpocket3.ds                                           -out_subdir TEST/TESTS
    test $FASTER rescore concavity.ds                                          -out_subdir TEST/TESTS
    test $FASTER eval-rescore fpocket.ds                                       -out_subdir TEST/TESTS
    test $FASTER eval-rescore concavity.ds                                     -out_subdir TEST/TESTS

    test ./prank.sh traineval -t fpocket.ds -e test.ds       -loop 1 -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh crossval  fpocket.ds  -folds 4           -loop 1 -fail_fast 1 -out_subdir TEST/TESTS

    # test grid optimization
    test ./prank.sh ploop -t fpocket.ds -e test.ds -loop 1 -fail_fast 1 -r_generate_plots 0 -feature_filters '((-chem.*),(-chem.*,chem.atoms),(protrusion.*,bfactor.*))' -out_subdir TEST/TESTS
    # test different tessellation
    test ./prank.sh traineval -t fpocket.ds -e test.ds -loop 1 -fail_fast 1 -tessellation 1 -train_tessellation 3 -out_subdir TEST/TESTS

    # test export_points feature
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv.gz     -out_subdir TEST/TESTS

    # test export-points command (no model)
    test $FASTER export-points -f distro/test_data/1fbl.pdb -export_points_format csv.gz                -out_subdir TEST/TESTS

    # test pocket grid + descriptors + PyMOL viz (all three exports in one go)
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -export_pocket_descriptors 1 -vis_pocket_grid 1     -out_subdir TEST/TESTS

    # cofactors feature smoke test
    test $FASTER predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA                         -out_subdir TEST/TESTS
    test $FASTER analyze cofactors -f distro/test_data/liganated/1t7qa.pdb                              -out_subdir TEST/TESTS

}

quick_train() {
    test ./prank.sh traineval -loop 1 -t fpocket.ds -e test.ds       -fail_fast 1 -out_subdir TEST/TESTS
}

basic() {

    title RUNNING BASIC TESTS

    # -fail_fast 0 because of missing ligands
    test $FASTER eval-predict chen11.ds                              -c config/train-default                        -out_subdir TEST/TESTS
    test $FASTER eval-predict 'joined(mlig).ds'                      -c config/train-default                        -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e chen11-fpocket.ds -c config/train-default  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS
    test ./prank.sh traineval -t chen11-fpocket.ds -e 'joined(mlig).ds' -c config/train-default  -loop 1  -fail_fast 0 -out_subdir TEST/TESTS
    test ./prank.sh crossval chen11-fpocket.ds                          -c config/train-default  -loop 1  -fail_fast 1 -out_subdir TEST/TESTS

    #test $FASTER eval-predict mlig-joined.ds   -c config/train-default -visualizations 1 -tessellation 3 -l VISUALIZATIONS_TES3 -c config/train-default -out_subdir TEST/TESTS
    #test $FASTER eval-predict mlig-joined.ds   -c config/train-default -visualizations 1  -l VISUALIZATIONS                     -c config/train-default -out_subdir TEST/TESTS
}

# test prediction on all datasets
predict() {

   title PREDICTIONS ON ALL DATASETS

   test $FASTER predict joined.ds          -c config/test-default    -out_subdir TEST/PREDICT
   test $FASTER predict holo4k.ds          -c config/test-default    -out_subdir TEST/PREDICT
   test $FASTER predict coach420.ds        -c config/test-default    -out_subdir TEST/PREDICT
   test $FASTER predict ah4h.holoraw.ds    -c config/test-default    -out_subdir TEST/PREDICT

   test $FASTER predict chen11.ds          -c config/test-default    -out_subdir TEST/PREDICT
   test $FASTER predict fptrain.ds         -c config/test-default    -out_subdir TEST/PREDICT
   test $FASTER predict 'joined(mlig).ds'  -c config/test-default    -out_subdir TEST/PREDICT
   test $FASTER predict 'holo4k(mlig).ds'  -c config/test-default    -out_subdir TEST/PREDICT

}

# test prediction with flattened forest
# doesn't make sense since default is already flat
#predict_flattened() {
#
#   title PREDICTIONS WITH FLATTENED FOREST
#
#   test $FASTER predict joined.ds         -c config/test-default  -rf_flatten 1  -rf_flatten_target FlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test $FASTER predict holo4k.ds         -c config/test-default  -rf_flatten 1  -rf_flatten_target LegacyFlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test $FASTER predict coach420.ds       -c config/test-default  -rf_flatten 1  -rf_flatten_target ShortFlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test $FASTER predict ah4h.holoraw.ds   -c config/test-default  -rf_flatten 1  -rf_flatten_target SuperShortLegacyFlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#
#   test $FASTER predict chen11.ds         -c config/test-default  -rf_flatten 1  -rf_flatten_target FlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test $FASTER predict fptrain.ds        -c config/test-default  -rf_flatten 1  -rf_flatten_target FlatBinaryForest -out_subdir TEST/PREDICT_FLATTENED
#   test $FASTER predict 'joined(mlig).ds' -c config/test-default  -rf_flatten 1  -rf_flatten_target InterleavedBfsForest -out_subdir TEST/PREDICT_FLATTENED
#   test $FASTER predict 'holo4k(mlig).ds' -c config/test-default  -rf_flatten 1  -rf_flatten_target InterleavedBfsForest -out_subdir TEST/PREDICT_FLATTENED
#
#}


conservation() {

   title PREDICTIONS USING CONSERVATION

   test $FASTER predict joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test $FASTER predict coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/hmm/scores' -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test $FASTER predict holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION

   title EVALUATING PREDICTIONS USING CONSERVATION

   test $FASTER eval-predict joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test $FASTER eval-predict coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/hmm/scores' -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test $FASTER eval-predict holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION

   title TRAIN/EVAL USING CONSERVATION

   test ./prank.sh traineval -t chen11-fpocket.ds -e joined.ds   -c config/test-conservation -conservation_dirs 'joined/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e coach420.ds -c config/test-conservation -conservation_dirs 'coach420/conservation/hmm/scores' -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION
   test ./prank.sh traineval -t chen11-fpocket.ds -e holo4k.ds   -c config/test-conservation -conservation_dirs 'holo4k/conservation/hmm/scores'   -fail_fast 0 -log_cases 1  -out_subdir TEST/CONSERVATION

}



# evaluate default model/settings on main datasets
eval_predict() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test $FASTER eval-predict joined.ds       -c config/test-default    -out_subdir TEST/EVAL
    test $FASTER eval-predict coach420.ds     -c config/test-default    -out_subdir TEST/EVAL
    test $FASTER eval-predict holo4k.ds       -c config/test-default    -out_subdir TEST/EVAL
    # test $FASTER predict ah4h.holoraw.ds      -c config/test-default    -out_subdir TEST/PREDICT
    
}


eval_predict_flattened() {

    title EVALUATING PREDICTIONS WITH FLATTENED FOREST

    test $FASTER eval-predict joined.ds       -c config/test-default  -rf_flatten 1 -rf_flatten_target FlatBinaryForest  -out_subdir TEST/EVAL_FLATTENED
    test $FASTER eval-predict coach420.ds     -c config/test-default  -rf_flatten 1 -rf_flatten_target FlatBinaryForest  -out_subdir TEST/EVAL_FLATTENED
    test $FASTER eval-predict holo4k.ds       -c config/test-default  -rf_flatten 1 -rf_flatten_target FlatBinaryForest  -out_subdir TEST/EVAL_FLATTENED -l FlatBinaryForest

    test $FASTER eval-predict holo4k.ds       -c config/test-default  -rf_flatten 1 -rf_flatten_target NativePanamaForest  -out_subdir TEST/EVAL_FLATTENED_NATIVE
}




eval_predict_uop() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test $FASTER eval-predict joined.ds       -c config/test-default  -use_only_positive_score 1  -out_subdir TEST/EVAL_UOP
    test $FASTER eval-predict holo4k.ds       -c config/test-default  -use_only_positive_score 1  -out_subdir TEST/EVAL_UOP
    test $FASTER eval-predict coach420.ds     -c config/test-default  -use_only_positive_score 1  -out_subdir TEST/EVAL_UOP
    # test $FASTER predict ah4h.holoraw.ds      -c config/test-default    -out_subdir TEST/PREDICT

}
eval_predict_flattened_uop() {

    title EVALUATING PREDICTIONS WITH FLATTENED FOREST

    test $FASTER eval-predict joined.ds       -c config/test-default  -rf_flatten 1 -use_only_positive_score 1 -out_subdir TEST/EVAL_FLATTENED_UOP
    test $FASTER eval-predict holo4k.ds       -c config/test-default  -rf_flatten 1 -use_only_positive_score 1 -out_subdir TEST/EVAL_FLATTENED_UOP
    test $FASTER eval-predict coach420.ds     -c config/test-default  -rf_flatten 1 -use_only_positive_score 1 -out_subdir TEST/EVAL_FLATTENED_UOP

}





eval_predict_alphafold() {

    title EVALUATING PREDICTIONS ON MAIN DATASETS

    test $FASTER eval-predict joined.ds       -c config/test-alphafold    -out_subdir TEST/EVAL_ALPHAFOLD
    test $FASTER eval-predict holo4k.ds       -c config/test-alphafold    -out_subdir TEST/EVAL_ALPHAFOLD
    test $FASTER eval-predict coach420.ds     -c config/test-alphafold    -out_subdir TEST/EVAL_ALPHAFOLD

}

# evaluate default model/settings on main datasets
eval_predict_rest() {

    title EVALUATING PREDICTIONS ON OTHER DATASETS

    # train=test for the reference
    test $FASTER eval-predict chen11.ds           -c config/test-default   -out_subdir TEST/EVAL
    test $FASTER eval-predict fptrain.ds          -c config/test-default   -out_subdir TEST/EVAL

    # -fail_fast 0 because of missing ligands
    test $FASTER eval-predict 'joined(mlig).ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test $FASTER eval-predict 'coach420(mlig).ds' -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test $FASTER eval-predict 'holo4k(mlig).ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL



    #test $FASTER eval-predict mlig-moad-nr.ds -c config/test-default -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
    #test $FASTER eval-predict moad-nr.ds      -c config/test-default -log_cases 1  -fail_fast 1  -out_subdir TEST/EVAL
}

eval_rescore() {

    title EVALUATING RESCORING ON ALL DATASETS

    test $FASTER eval-rescore joined-fpocket.ds           -c config/test-default  -out_subdir TEST/EVAL
    test $FASTER eval-rescore coach420-fpocket.ds         -c config/test-default  -out_subdir TEST/EVAL
    test $FASTER eval-rescore holo4k-fpocket.ds           -c config/test-default  -out_subdir TEST/EVAL
                                                             
    test $FASTER eval-rescore chen11-fpocket.ds           -c config/test-default  -out_subdir TEST/EVAL

    # -fail_fast 0 because of missing ligands
    test $FASTER eval-rescore 'joined(mlig)-fpocket.ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test $FASTER eval-rescore 'coach420(mlig)-fpocket.ds' -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
    test $FASTER eval-rescore 'holo4k(mlig)-fpocket.ds'   -c config/test-default  -fail_fast 0  -out_subdir TEST/EVAL
}

fpocket_rescore() {

    title "EVALUATING FPOCKT-RESCORE (RUNNING FPOCKET AD-HOC)"

    test $FASTER fpocket-rescore joined.ds           -c config/test-default  -out_subdir TEST/RESCORE
    test $FASTER fpocket-rescore coach420.ds         -c config/test-default  -out_subdir TEST/RESCORE
    test $FASTER fpocket-rescore holo4k.ds           -c config/test-default  -out_subdir TEST/RESCORE
    test $FASTER fpocket-rescore chen11.ds           -c config/test-default  -out_subdir TEST/RESCORE

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

    test $FASTER print features     -c config/train-default  -out_subdir TEST/ANALYZE
    test $FASTER print model-info   -c config/train-default  -out_subdir TEST/ANALYZE

    test $FASTER analyze fasta-masked -f distro/test_data/liganated/1aaxa.pdb -c config/train-default  -out_subdir TEST/ANALYZE

    test $FASTER analyze fasta-masked chen11.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze fasta-masked joined.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze fasta-masked coach420.ds            -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze fasta-masked holo4k.ds              -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

    test $FASTER analyze residues              joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze binding-residues      joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze labeled-residues      joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze chains                joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze chains-residues       joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze aa-propensities       joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze aa-surf-seq-duplets   joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze aa-surf-seq-triplets  joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze fasta-raw             joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

    test $FASTER analyze residues              holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze binding-residues      holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze labeled-residues      holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze chains                holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze chains-residues       holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

    test $FASTER analyze cofactors             joined.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE
    test $FASTER analyze cofactors             holo4k.ds     -c config/train-default  -cache_datasets 0   -out_subdir TEST/ANALYZE

}


# benchmark + bit-equality-verify all surface strategies (cdk|faster|packed) on the major datasets.
# Optional args override the dataset list (default: the 5 major datasets), e.g. surface_strategies fptrain.ds
surface_strategies() {

    title SURFACE STRATEGIES BENCHMARK AND EQUALITY

    local datasets="${@:-chen11.ds fptrain.ds coach420.ds joined.ds holo4k.ds}"

    # Uses ./prank.sh (local-env big heap + full C2/Graal JIT), NOT prank_faster: this is a heavy
    # compute benchmark, and the command pre-loads all proteins (large datasets like holo4k need the
    # big heap). prank_faster's C1-only JIT and 2g heap would undersell the optimized strategies.
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

    # ./prank.sh (big heap + full JIT), same rationale as surface_strategies: heavy compute that
    # pre-loads all proteins. Output is segregated per strategy so runs don't overwrite each other.
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
    test $FASTER analyze cofactors -f distro/test_data/liganated/1t7qa.pdb                                          -out_subdir TEST/COFACTORS
    test $FASTER analyze cofactors test.ds                                                                          -out_subdir TEST/COFACTORS

    # analyze cofactors: dry-run mode with each specifier form
    test $FASTER analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors COA                           -out_subdir TEST/COFACTORS
    test $FASTER analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors 'COA[atom_id:9551]'           -out_subdir TEST/COFACTORS
    test $FASTER analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors 'COA[contact_res_ids:A_K258]' -out_subdir TEST/COFACTORS
    test $FASTER analyze cofactors -f distro/test_data/liganated/1t7qa.pdb -cofactors ZZZZ                          -out_subdir TEST/COFACTORS

    # predict with cofactors
    test $FASTER predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA                                     -out_subdir TEST/COFACTORS
    # R18: never-present specifier must be a no-op (drop-in safety)
    test $FASTER predict -f distro/test_data/1fbl.pdb -cofactors ZZZZ                                               -out_subdir TEST/COFACTORS
    # R22: case-mismatched name must still match (parser auto-uppercases the group name)
    test $FASTER predict -f distro/test_data/liganated/1t7qa.pdb -cofactors coa                                     -out_subdir TEST/COFACTORS
    # R22: contact_res_ids must survive comma-splitting (bracket-aware parse)
    test $FASTER predict -f distro/test_data/liganated/1t7qa.pdb -cofactors 'COA[contact_res_ids:A_K258,A_D246]'    -out_subdir TEST/COFACTORS

    # knobs
    test $FASTER predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA -cofactor_max_protein_dist 0        -out_subdir TEST/COFACTORS
    test $FASTER predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA -vis_highlight_cofactors 0          -out_subdir TEST/COFACTORS

    # R19: aa_mapping collision warning (MSE is in built-in minimal mapping)
    test $FASTER predict -f distro/test_data/1fbl.pdb -cofactors MSE                                                -out_subdir TEST/COFACTORS

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
    test $FASTER predict cofactors-demo-cif.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS
    test $FASTER predict cofactors-demo-pdb.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS

    # --- B. baseline runs (no cofactors column) for delta comparison ---
    test $FASTER predict cofactors-demo-baseline-cif.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS
    test $FASTER predict cofactors-demo-baseline-pdb.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS

    # --- C. analyze cofactors on the demo dataset (matches.csv, summary) ---
    test $FASTER analyze cofactors cofactors-demo-cif.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS
    test $FASTER analyze cofactors cofactors-demo-baseline-cif.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS

    # --- D. combined with -aa_mapping pdbfixer (R19 collision on TPQ family) ---
    test $FASTER predict cofactors-demo-cif.ds -dataset_base_dir $DS_BASE  -aa_mapping pdbfixer  -out_subdir TEST/COF_DS

    # --- E. visualization spot-check on two well-known cofactors ---
    test $FASTER predict -f $DS_BASE/structures/pdb/4REK.pdb -cofactors FAD  -visualizations 1  -out_subdir TEST/COF_DS
    test $FASTER predict -f $DS_BASE/structures/pdb/4BEU.pdb -cofactors PLP  -visualizations 1  -out_subdir TEST/COF_DS

    # --- F. export-points (cofactor atoms must propagate into the points output) ---
    test $FASTER export-points -f $DS_BASE/structures/pdb/4REK.pdb -cofactors FAD  -out_subdir TEST/COF_DS

    # --- G. full-dataset stress test (523 structures across 43 cofactor codes) ---
    if [ -f "$DS_BASE/cofactors-full.ds" ]; then
        test $FASTER predict cofactors-full.ds          -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS_FULL
        test $FASTER predict cofactors-full-baseline.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS_FULL
        test $FASTER analyze cofactors cofactors-full.ds -dataset_base_dir $DS_BASE  -fail_fast 1  -out_subdir TEST/COF_DS_FULL
    fi
}

aa_mapping() {

    title AA MAPPING FEATURE

    # bundled pdbfixer preset (87-entry table)
    test $FASTER predict -f distro/test_data/1fbl.pdb                                                                       -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING

    # dataset paths (predict / rescore / eval)
    test $FASTER predict test.ds                                                                                            -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING
    test $FASTER rescore fpocket.ds                                                                                         -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING
    test $FASTER eval-predict -f distro/test_data/liganated/1aaxa.pdb                                                       -aa_mapping pdbfixer    -out_subdir TEST/AA_MAPPING
}

transform() {

  title TRANSFORM COMMANDS

  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains A                                                  # output: <out_dir>/2W83_A.cif
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.pdb     -chains A                                                  # output: <out_dir>/2W83_A.pdb
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A,B                                                # output: <out_dir>/2W83_A,B.cif.gz
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A,B  -out_file distro/test_output/2W83_A,B.cif.gz  # output: distro/test_output/2W83_A,B.cif.gz
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains keep                                               # output: <out_dir>/2W83.cif
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains keep -out_format pdb.gz                            # output: <out_dir>/2W83.pdb.gz
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains all                                                # output: <out_dir>/2W83_all.cif
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif     -chains A    -out_format keep                              # output: <out_dir>/2W83_A.cif
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.cif.gz  -chains A    -out_format pdb.gz                            # output: <out_dir>/2W83_A.pdb.gz
  test $FASTER transform reduce-to-chains  -f distro/test_data/2W83.pdb.gz  -chains A,B  -out_format cif                               # output: <out_dir>/2W83_A,B.cif

  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains A
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.pdb     -chains A
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A,B
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A,B  -out_file distro/test_output/1fbl_A,B.cif.gz
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains keep
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains keep -out_format pdb.gz
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains all
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif     -chains A    -out_format keep
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.cif.gz  -chains A    -out_format pdb.gz
  test $FASTER transform reduce-to-chains  -f distro/test_data/1fbl.pdb.gz  -chains A,B  -out_format cif

}

export_points() {

    title EXPORT POINTS FEATURE

    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv       -out_subdir TEST/EXPORT_POINTS
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv.gz    -out_subdir TEST/EXPORT_POINTS
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format csv.zst   -out_subdir TEST/EXPORT_POINTS
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format arrow     -out_subdir TEST/EXPORT_POINTS
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format arrow.gz  -out_subdir TEST/EXPORT_POINTS
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format arrow.zst -out_subdir TEST/EXPORT_POINTS
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_points 1 -export_points_format parquet   -out_subdir TEST/EXPORT_POINTS

    # export-points command (no model needed)
    test $FASTER export-points -f distro/test_data/2W83.cif -extra_features 'surface_protrusion' -export_points_format arrow -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points -f distro/test_data/1fbl.pdb                                              -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points -f distro/test_data/1fbl.pdb -export_points_format csv.gz                 -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points -f distro/test_data/1fbl.pdb -export_points_format csv.zst                -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points -f distro/test_data/1fbl.pdb -export_points_format arrow                  -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points -f distro/test_data/1fbl.pdb -export_points_format arrow.gz               -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points -f distro/test_data/1fbl.pdb -export_points_format arrow.zst              -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points -f distro/test_data/1fbl.pdb -export_points_format parquet                -out_subdir TEST/EXPORT_POINTS

    # predict/rescore on datasets
    test $FASTER predict chen11.ds            -c config/test-default  -export_points 1 -export_points_format arrow.zst -out_subdir TEST/EXPORT_POINTS
    test $FASTER rescore coach420-fpocket.ds  -c config/test-default  -export_points 1 -export_points_format csv.zst   -out_subdir TEST/EXPORT_POINTS
    test $FASTER rescore chen11-fpocket.ds    -c config/test-default  -export_points 1 -export_points_format parquet   -out_subdir TEST/EXPORT_POINTS

    # export-points command  on datasets
    test $FASTER export-points chen11.ds            -c config/test-default  -export_points_format arrow.zst -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points coach420-fpocket.ds  -c config/test-default  -export_points_format csv.zst   -out_subdir TEST/EXPORT_POINTS
    test $FASTER export-points chen11-fpocket.ds    -c config/test-default  -export_points_format parquet   -out_subdir TEST/EXPORT_POINTS
}

pocket_grid() {

    title POCKET GRID + DESCRIPTORS EXPORT

    # Basic combinations on a single file
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1                                                                                            -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1                                                                                     -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -export_pocket_descriptors 1                                                               -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -vis_pocket_grid 1                                                                  -out_subdir TEST/POCKET_GRID

    # All grid file formats
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format csv                                                                    -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format csv.gz                                                                 -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format csv.zst                                                                -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format arrow                                                                  -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format arrow.gz                                                               -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format arrow.zst                                                              -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_format parquet                                                                -out_subdir TEST/POCKET_GRID

    # Full descriptor menu (all seven shipped, including principal_moments)
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1 -pocket_descriptors 'volume,sphericity,radius_of_gyration,num_residues,num_surface_atoms,num_grid_points,principal_moments'  -out_subdir TEST/POCKET_GRID

    # Per-grid-point descriptors (volsite, volsite_smooth), each at least once
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite'                                                       -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite_smooth'                                                -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite,volsite_smooth'                                        -out_subdir TEST/POCKET_GRID

    # Per-grid-point descriptor knobs
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite'        -pocket_grid_volsite_radius 5.0                 -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'volsite_smooth' -pocket_grid_volsite_sigma 1.5                  -out_subdir TEST/POCKET_GRID

    # Full combo: per-pocket descriptors + per-grid-point descriptors + viz in one run
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_grid_point_descriptors 'volsite,volsite_smooth' -vis_pocket_grid 1   -out_subdir TEST/POCKET_GRID

    # Grid-free descriptors only — exercises the grid-build short-circuit (no
    # "PocketGrid built" log line should appear for these invocations).
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1 -pocket_descriptors 'num_residues,num_surface_atoms' -export_pocket_grid 0           -out_subdir TEST/POCKET_GRID

    # Fill strategies (only morph_closing and none are valid; convex_hull is rejected at startup)
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_fill morph_closing                                                            -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_fill none                                                                     -out_subdir TEST/POCKET_GRID

    # Knobs: include unassigned, tighter spacing, larger reach, custom atom buffer
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_include_unassigned 1                                                          -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_spacing 0.75                                                                  -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_max_dist 8                                                                    -out_subdir TEST/POCKET_GRID
    test $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_atom_buffer 1.0                                                               -out_subdir TEST/POCKET_GRID

    # Validation: fail-fast checks (each must exit non-zero — uses test_expect_fail so the green [OK] is shown only when the command fails as designed)
    test_expect_fail $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_fill cubist                                                                   -out_subdir TEST/POCKET_GRID
    test_expect_fail $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_descriptors 1 -pocket_descriptors 'not_a_descriptor'                                              -out_subdir TEST/POCKET_GRID
    test_expect_fail $FASTER predict -f distro/test_data/1fbl.pdb -export_pocket_grid 1 -pocket_grid_point_descriptors 'not_a_descriptor'                                         -out_subdir TEST/POCKET_GRID
    test_expect_fail $FASTER predict -f distro/test_data/1fbl.pdb -vis_pocket_grid 1                                                                                        -out_subdir TEST/POCKET_GRID

    # Rescore + dataset paths
    test $FASTER rescore  fpocket.ds  -export_pocket_grid 1 -export_pocket_descriptors 1                                                                               -out_subdir TEST/POCKET_GRID
    test $FASTER predict  test.ds     -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_grid_format parquet                                                   -out_subdir TEST/POCKET_GRID

    # Cofactor interaction — cofactor atoms must show up in the grid's exclusion zone
    test $FASTER predict -f distro/test_data/liganated/1t7qa.pdb -cofactors COA -export_pocket_grid 1 -export_pocket_descriptors 1                                     -out_subdir TEST/POCKET_GRID


    # Single timed run per dataset at the env default thread count (PRANK_LOCALENV_PARAMS,
    # typically -threads 16). No -threads override here on purpose — measures real
    # end-to-end wall time on each dataset rather than isolating per-protein cost.
    test $FASTER predict joined.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    test $FASTER predict holo4k.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    test $FASTER predict coach420.ds   -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    test $FASTER predict chen11.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID
    #test $FASTER predict fptrain.ds    -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID

    test $FASTER predict fptrain.ds    -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID


}

pocket_grid_bench() {

    title POCKET GRID BENCHMARK

    test $FASTER predict joined.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test $FASTER predict holo4k.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test $FASTER predict coach420.ds   -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test $FASTER predict chen11.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH
    test $FASTER predict fptrain.ds    -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1  -out_subdir TEST/POCKET_GRID/BENCH

}

# Long-running sweep: full descriptor menu (per-pocket + per-grid-point) on the
# 4 main datasets with visualizations enabled. Not part of all() — opt-in only.
pocket_grid_long() {

    title POCKET GRID LONG - ALL DESCRIPTORS ON MAIN DATASETS

    PG_DESCRIPTORS='volume,sphericity,radius_of_gyration,num_residues,num_surface_atoms,num_grid_points,principal_moments'
    PG_POINT_DESCRIPTORS='volsite,volsite_smooth'

    test $FASTER predict joined.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG
    test $FASTER predict holo4k.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG
    test $FASTER predict coach420.ds   -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG
    test $FASTER predict chen11.ds     -c config/test-default  -export_pocket_grid 1 -export_pocket_descriptors 1 -pocket_descriptors "$PG_DESCRIPTORS" -pocket_grid_point_descriptors "$PG_POINT_DESCRIPTORS" -visualizations 1 -vis_pocket_grid 1  -out_subdir TEST/POCKET_GRID_LONG

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
