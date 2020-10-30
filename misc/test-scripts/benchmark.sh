#!/usr/bin/env bash

#
# benchmarks a command running it repeatedly with various numbers of threads
#

REPETITIONS=$1
LABEL="$2"
THREADS="$3"
COMMAND="$4"

OUTFILE=local-speed-${LABEL}.log
DEBUG_LOG=local-debug.log

repeat() {
    CMD="$@"
    N=${REPETITIONS}

    for (( i=1; i<=N; i++ ))
    do
        echo run ${i}/${N} command: \[${CMD}\]
        ${CMD} &>> $DEBUG_LOG
    done
}

benchmark() {
    COM="$1"
    MODIFIER="$2"

    if [ "$REPETITIONS" -ne "1" ]; then
        echo heat-up run 0
        ${COM} ${MODIFIER}
    fi

    start=$(date +%s)
    repeat "$COM $MODIFIER"
    end=$(date +%s)

    runtime=$((end-start))
    avg_time=$((runtime/REPETITIONS))

    printf "[${MODIFIER}]  avg_time: %7s s\n" "$avg_time" | tee -a ${OUTFILE}
}

main() {
    printf "\nbenchmarking command [$COMMAND]\n\n" > ${OUTFILE}

    # iterate over thread numbers
    for i in ${@}
    do
        benchmark "$COMMAND" "-threads $i" &>> $DEBUG_LOG
    done

    cat ${OUTFILE}
}



main ${THREADS}

