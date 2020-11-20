package cz.siret.prank.utils;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for debugging and profiling cutoffAtoms methods
 */
public class CutoffAtomsCallLog {

    public static CutoffAtomsCallLog INST = new CutoffAtomsCallLog();


    static final int MAX = 5000;

    List<int[]> calls = new ArrayList<>(100000);

    long[] ncalls = new long[MAX + 1];
    long[] times = new long[MAX + 1];
    long[] returned = new long[MAX + 1];


    public void addCall(int atomsSize, int resultSize, long time) {
        calls.add(new int[]{atomsSize, resultSize});

        if (atomsSize > MAX) atomsSize = MAX;

        ncalls[atomsSize]++;
        times[atomsSize] += time;

        returned[atomsSize] += resultSize;
    }

    public void printOut(String fnamePrefix) {
        String csv = Joiner.on('\n').join(calls.stream().map(a -> "" + a[0] + "," + a[1]).collect(Collectors.toList()));
        Futils.writeFile(fnamePrefix + "_calls.csv", csv);

        StringBuilder ss = new StringBuilder();
        ss.append("atoms,calls,sum_time,avg_returned\n");
        for (int i = 0; i <= MAX; ++i) {
            double avgret = (ncalls[i] == 0) ? 0 : ((double)returned[i]) / ncalls[i];
            ss.append("" + (i) + "," + ncalls[i] + "," + times[i] + "," + PerfUtils.formatDouble(avgret) + "\n");
        }
        Futils.writeFile(fnamePrefix + "_stats.csv", ss.toString());
    }

}
