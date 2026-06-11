#!/usr/bin/env python3
"""
Per-protein bootstrap CI on DCA success-rate deltas between a baseline forest variant and one or more
flattened/approximate variants, from a `prank transform compare-flatten-eval` output directory.

Why: the G0 gate (FlattenComparison) reports a point estimate of dDCA vs baseline. A single point can't
tell whether a small delta (e.g. the holo4k DCC 0.1pp blip) is real or sampling noise. This resamples
PROTEINS with replacement (paired: baseline and variant scored on the same resample each iteration), so
the reported interval reflects between-protein variance — the right unit, since a protein's ligands are
not independent. Output: dDCA mean + percentile CI per variant, for DCA(4.0) at each top-(n+k) tolerance.

DCA(thr) success for a ligand at tolerance k: the ligand's `dca4rank` (rank of the first predicted pocket
within `thr` Å of it) is in [1, nLigandsInProtein + k]; dca4rank == -1 means no pocket within thr (miss).
This reproduces p2rank's top-(n+k) DCA exactly (validated against success_rates.csv on load).

Usage:
  python3 dca_bootstrap_ci.py <compare-flatten-eval-outdir> [--iters 10000] [--seed 42] [--tol 0 2]
"""
import argparse
import csv
import os
import random
import sys
from collections import defaultdict

# top-(n+k) tolerances p2rank reports in success_rates.csv (the [..] columns)
DEFAULT_TOLS = [0, 1, 2, 4, 10, 99]


def load_ligands(variant_dir):
    """Return list of (protein, n_ligands_in_protein, dca4rank) for one variant's cases/ligands.csv."""
    path = os.path.join(variant_dir, "cases", "ligands.csv")
    rows = []
    with open(path, newline="") as fh:
        reader = csv.reader(fh)
        header = [h.strip() for h in next(reader)]
        ci = {name: i for i, name in enumerate(header)}
        for rec in reader:
            if not rec or not rec[0].strip():
                continue
            protein = rec[ci["file"]].strip()
            n_lig = int(rec[ci["#ligands"]].strip())
            dca4rank = int(rec[ci["dca4rank"]].strip())
            rows.append((protein, n_lig, dca4rank))
    return rows


def hits_at(rows, k):
    """Per-ligand 0/1 DCA(4.0) hit at top-(n+k), as a dict protein -> list[int]."""
    by_protein = defaultdict(list)
    for protein, n_lig, dca4rank in rows:
        hit = 1 if (dca4rank >= 1 and dca4rank <= n_lig + k) else 0
        by_protein[protein].append(hit)
    return by_protein


def dca_rate(by_protein, proteins):
    """DCA success rate (%) over the ligands of the given (possibly resampled) protein multiset."""
    num = den = 0
    for p in proteins:
        ligs = by_protein[p]
        num += sum(ligs)
        den += len(ligs)
    return 100.0 * num / den if den else float("nan")


def percentile(sorted_vals, q):
    if not sorted_vals:
        return float("nan")
    idx = min(len(sorted_vals) - 1, max(0, int(round(q / 100.0 * (len(sorted_vals) - 1)))))
    return sorted_vals[idx]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("outdir", help="compare-flatten-eval output dir (contains baseline/ + variant dirs)")
    ap.add_argument("--iters", type=int, default=10000)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--tol", type=int, nargs="*", default=DEFAULT_TOLS, help="top-(n+k) tolerances")
    args = ap.parse_args()

    variants = [d for d in sorted(os.listdir(args.outdir))
                if d != "baseline" and os.path.isdir(os.path.join(args.outdir, d, "cases"))]
    if not os.path.isdir(os.path.join(args.outdir, "baseline", "cases")):
        sys.exit(f"no baseline/cases under {args.outdir}")

    base_rows = load_ligands(os.path.join(args.outdir, "baseline"))
    proteins = sorted({p for p, _, _ in base_rows})
    print(f"# baseline: {len(proteins)} proteins, {len(base_rows)} ligands; {args.iters} bootstrap iters\n")

    for variant in variants:
        var_rows = load_ligands(os.path.join(args.outdir, variant))
        print(f"=== {variant} ===")
        print(f"{'tol(n+k)':>9}  {'DCA_base%':>9}  {'DCA_var%':>9}  {'dDCA_pp':>8}  {'95% CI (pp)':>22}")
        for k in args.tol:
            base_bp = hits_at(base_rows, k)
            var_bp = hits_at(var_rows, k)
            base_pt = dca_rate(base_bp, proteins)
            var_pt = dca_rate(var_bp, proteins)

            rng = random.Random(args.seed)
            deltas = []
            n = len(proteins)
            for _ in range(args.iters):
                sample = [proteins[rng.randrange(n)] for _ in range(n)]  # paired resample of proteins
                deltas.append(dca_rate(var_bp, sample) - dca_rate(base_bp, sample))
            deltas.sort()
            lo, hi = percentile(deltas, 2.5), percentile(deltas, 97.5)
            print(f"{k:>9}  {base_pt:>9.2f}  {var_pt:>9.2f}  {var_pt - base_pt:>+8.3f}  [{lo:>+7.3f}, {hi:>+7.3f}]")
        print()


if __name__ == "__main__":
    main()
