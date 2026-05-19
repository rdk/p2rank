# Pocket Grid — Follow-up Notes

Loose observations gathered while benchmarking; not blocking work.

## JIT Code Cache fills on long runs

On the coach420-fpocket bench at 16 threads we saw 2 Full GCs caused by
`CodeCache GC Threshold` (a JIT code cache sweep, not heap GC). Each took
~70 ms and reduced cached compiled code from ~3 GB down to ~350 MB. On a
420-protein run this is in the noise (~140 ms / 27 s ≈ 0.5%), but on
multi-hour `eval`/`crossvalidate` runs the JIT will repeatedly fill and
sweep, hurting steady-state throughput.

Mitigation if it ever shows up as a real cost: bump
`-XX:ReservedCodeCacheSize=512m` (default is 256m on most JDKs) in
`prank.sh`'s `JAVA_OPTS`. Easy to verify with `-Xlog:gc*` on a long run —
if `CodeCache GC Threshold` events disappear and steady-state time
improves, that's the fix.

## Per-protein parallelism gap

After the HPPC `LongIntHashMap` swap (commit b48caeec), coach420
pocket-grid export at 16 threads runs at ~35% CPU utilization on the
grid/writer phase, despite GC being ~1.5% (negligible). The remaining
gap is structural — grid build + write is single-threaded per protein,
and the dataset has variance in protein size so the tail straggles.

If this becomes worth chasing: parallelize the per-pocket loop inside
`PocketGridBuilder.build` (the assigner + filler calls are independent
per pocket). Likely 1.2-1.5× speedup on the multi-pocket proteins
without disturbing the single-pocket common case.
