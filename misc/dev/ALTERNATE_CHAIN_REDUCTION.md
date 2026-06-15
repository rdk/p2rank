# Alternate-conformation chain reduction

Investigation and implementation report.

**Status:** implemented on branch `feat/alternate-chain-reducer` (2026-06-15),
default on via `reduce_alternate_conformation_chains`. holo4k-validated; full-PDB
footprint measured.

**Code:** `src/main/groovy/cz/siret/prank/geom/AlternateChainReducer.groovy`,
`PdbUtils.structureWithChains`, call site in `Protein.loadStructure`, param in
`Params.groovy`. Tests: `AlternateChainReducerTest`.

---

## 1. Symptom that started this

On `pdb-10k`, prediction output differed between two surface strategies that are
supposed to agree on pockets:

- `surface_strategy = faster` (the old default: tessellate + 0.05 A sparsify)
- `surface_strategy = packed_distinct_v4` (the current default: distinct
  directions, no sparsify)

Across 10,000 proteins the two agreed almost everywhere (median per-protein SAS
point ratio 1.000), but a handful diverged, dominated by one extreme outlier,
**PDB 6een**, where `v4` produced 1.84x the SAS points of `faster` (9379 vs
5298), gained 7 pockets (43 -> 50), and inflated the top pocket score from
95.5 to 156.5.

Only **3 of 10,000** proteins were materially affected: 6een (dramatic), plus
1xjz and 8fz5 (one extra low-rank pocket each).

---

## 2. Root cause (and what it is NOT)

### Not within-residue altLocs

The obvious first hypothesis (ordinary alternate side-chain conformations
inflating the surface) is **wrong**. BioJava (`org.biojava:biojava-structure:7.2.5-rdk.1`)
already collapses within-residue altLocs at parse time: the primary conformation
goes into `Group.getAtoms()`, the alternates into a detached `Group.getAltLocs()`
list that P2Rank never reads. Both P2Rank atom collections confirm this:

- `Atoms.allFromStructure` uses BioJava `AtomIterator` -> `GroupIterator`, which
  walks `Group.getAtom(i)` over `Group.size()` and never descends into
  `getAltLocs()`.
- `Atoms.allFromGroups` reads `group.getAtoms()` directly.

Empirically on `distro/test_data/2W83.pdb` (ordinary within-residue altLocs):
5359 atom records, 26 with a non-blank altLoc (13 positions x 2 conformers);
P2Rank loads **5346** atoms = 5359 - 13. The 13 alternates are excluded. So
~43% of the PDB carrying "some altLoc" is already handled and is **not** a
problem.

### The real cause: alternate-conformation whole chains

6een ("...microheterogeneity") deposits its protein as **four alternate
conformations stored as four separate chains**, each uniformly tagged with one
altLoc letter:

| chains | content | atoms each | altLoc |
|--------|---------|-----------:|:------:|
| A,B,C,D | polymer 1, 4 conformations | ~2444 | A / B / C / D |
| E,F,G,H | polymer 2, 4 conformations | ~200 | A / B / C / D |
| I-P | waters | small | blank |

`getAltLocs()` is empty on every group; each chain is internally a clean single
conformation. The duplication is at **chain** granularity, so the naive
per-group / per-atom-name dedup removes **zero** atoms. P2Rank loads all four
copies: `proteinAtoms = 9668` (~4x one conformation ~2417).

`faster` + sparsify collapses the resulting near-coincident *surface points*
(5298); `packed_distinct_v4` keeps them (9379). Proof: applying P2Rank's own
0.05 A sparsification to v4's 9379 points yields exactly 5298 = `faster`. The
whole divergence is 4081 near-duplicate points that the distinct strategy keeps
and sparsify drops.

> [!IMPORTANT]
> Neither strategy was "correct" on 6een: both surfaced four overlapping chains.
> The features (atom density, protrusion, contact counts) were also ~4x inflated
> under both strategies. v4 just made the pre-existing inflation visible by also
> keeping the duplicate surface points.

---

## 3. How we know the chains are redundant

Chain IDs alone do not tell us whether A/B/C/D are alternate conformations (to be
collapsed) or four legitimate molecules in the asymmetric unit (to be kept). Two
independent signals settle it:

1. **Declarative (altLoc field).** Each chain is uniformly tagged with a single
   non-blank altLoc letter. The altLoc field exists specifically to mark
   alternate locations of the same atoms; genuinely distinct molecules use blank
   altLoc.
2. **Geometric (overlap).** The decisive, self-validating test: alternate
   conformations occupy the **same** volume. For 6een, chains B/C/D superimpose
   on A with **median nearest-atom distance 0.002 A**, 99.6-100% of atoms within
   1 A, and identical centroids. A genuine homo-oligomer would tile **distinct**
   space (most atoms far apart, different centroids).

The geometric test is the safety guard: it cannot be fooled by mislabeling and
cleanly separates true redundancy from legitimate copies.

---

## 4. Solution

`AlternateChainReducer.reduceAlternateConformationChains(Structure, name)`,
called in `Protein.loadStructure` **after** model-0 and `onlyChains` reduction,
**before** `calculateResidues()`, gated by `reduce_alternate_conformation_chains`
(`@RuntimeParam`, default `true`).

### Algorithm

1. Classify each chain. A chain is a **candidate** when at least
   `MIN_ALTLOC_FRACTION` (0.5) of its atoms carry one and the same non-blank
   altLoc letter. Blank chains, waters, ligands, and ordinary within-residue
   altLoc chains fall below this and are non-candidates (always kept, used as
   overlap references).
2. Consider candidates in (altLoc letter, chain id) order. A candidate is
   **dropped** if at least `MIN_OVERLAP_FRACTION` (0.7) of its atoms lie within
   `OVERLAP_DISTANCE` (2.0 A) of a kept chain that is either a non-candidate or a
   candidate with a strictly **lower** altLoc letter. Otherwise it is kept and
   becomes a reference for higher letters.
3. If nothing is dropped, the **same** `Structure` instance is returned (true
   no-op). Otherwise a new structure is built via `PdbUtils.structureWithChains`,
   which reuses the existing Chain/Group/Atom objects by reference (no cloning),
   reusing the same `cleanCopyWithMetadata` + `copyChains` path as
   `reduceStructureToChains`.

### Safety properties

- **Strictly-lower-letter comparison** means two chains sharing the same altLoc
  letter are never collapsed against each other (they are distinct molecules).
- **Geometric overlap** means chains that occupy distinct space are never
  collapsed, regardless of altLoc labeling.
- **Lowest letter / blank is the primary** that survives each cluster.
- Atom object references are preserved, so downstream reference-equality (KD-tree
  snapshots, `VolSiteAtomTable`, PDB-serial-keyed feature caches) is intact. PDB
  serials are never renumbered (dropping duplicate atoms only ever reduces the
  serial-collision risk noted in `backlog.md`).

Thresholds are class constants (not params); promote to params only if tuning is
ever needed.

---

## 5. Validation

### Targeted behavior

| case | result |
|------|--------|
| 6een, reducer on | drops chains B/C/D/F/G/H; `proteinAtoms` 9668 -> **2417** |
| 6een, `faster` vs `v4` after reduction | **identical** SAS clouds (5309 = 5309) and identical residues; divergence gone |
| 2W83 (within-residue altLocs) | untouched (no-op; same `Structure` instance) |
| 102l (normal) | predictions + residues **byte-identical** reducer on vs off |
| 1xjz, 8fz5 | reducer does **not** fire (their trivial 1-pocket delta is the inherent distinct-superset behavior, not alternate chains) |

Tests: `AlternateChainReducerTest` (5, in-memory structures + 2W83 no-op) plus
`FeatureVectorGoldenTest`, `CofactorPipelineTest`, `ChainReductionTest`,
`StructTest`, `SurfaceStrategyTest`, and others (92 tests total, 0 failures).

### holo4k benchmark (eval-predict, default model, default v4 surface)

Only `reduce_alternate_conformation_chains` toggled:

| metric | ON | OFF |
|--------|----|----|
| **DCA(4.0)** top-n / top-(n+2) | **73.2 / 78.4** | **73.2 / 78.4** (identical) |
| P / R / F1 / MCC | 0.4421 / 0.5043 / 0.4712 / 0.4583 | 0.4422 / 0.5043 / 0.4712 / 0.4583 |
| proteins / ligands / pockets | 4009 / 9265 / 31943 | 4009 / 9264 / 31943 |

The canonical DCA(4.0) is identical; every `success_rates.csv` criterion differs
by <= 0.1 pp (single-protein rounding out of 4009). The reducer fired on **27**
holo4k proteins (each dropping one small superimposed chain) yet success rates
did not move.

> [!NOTE]
> Ligand count shifted by 1 (9265 -> 9264): on rare occasions an alternate chain
> is itself a ligand conformation, so collapsing it is a negligible ground-truth
> wobble. It changed no success metric. This is the only ground-truth side effect
> observed and it is within noise.

### Whole-PDB footprint (`analyze parse-proteins`, 249,522 structures, 16 threads, ~22 min)

**3,349 affected (1.34%).** Distribution by chains dropped:

| chains dropped | proteins | share |
|---:|---:|---:|
| 1 | 2,020 | 60.3% |
| 2 | 876 | 26.2% |
| 3-4 | 349 | 10.4% |
| >= 5 (heavy, 6een-style) | 104 | 3.1% |
| (of which >= 6) | 84 | 2.5% |
| max: 6uwi | 56 of 112 | -- |

Median structure sheds only 14% of its chains (p99 = 40%, never 100%). 86.5% of
affected structures drop just 1-2 chains, the same benign profile as the 27
holo4k cases that moved zero metrics. Only ~104 structures (**0.04% of the PDB**)
are heavy 6een-style collapses whose predictions change materially, consistent
with the pdb-10k rate (3/10,000).

The heaviest case (6uwi, 56 of 112 chains) was independently verified as genuine
2-fold microheterogeneity: altLoc `1`/`2`, 56 chains each, the dropped `2`-set
overlapping the kept `1`-set at 0.642 A median. Not a false positive.

---

## 6. Hidden issues found during the investigation

- **`PocketeerLoader` positional atom indices.**
  `PocketeerLoader.groovy:98-110` resolves atoms by 0-based position into
  `allAtoms.list` (from external Pocketeer JSON). Any pre-`calculateResidues`
  atom removal shifts those indices. This is a real break **only** for the
  Pocketeer-rescore workflow (already fragile to atom ordering), not for normal
  predict/train/eval. Guard or document if Pocketeer inputs become in scope.
- **Do not use `StructureTools.cleanUpAltLocs`.** The vendored BioJava's altLoc
  helper is **additive** (`Group.addAtom` for names absent from the primary), the
  opposite of reduction.
- **`FileParsingParameters` has no altLoc option** in this fork, so post-load
  reduction is the only lever.

---

## 7. Not addressed (deliberately)

- **Residual distinct-vs-sparsify divergence** on 1xjz / 8fz5 (one extra
  low-rank pocket each). This is the inherent "distinct strategies are a superset
  of faster+sparsify" behavior, already documented on `surface_strategy`; it is
  not an alternate-chain issue and the reducer leaves it alone.
- **Ligand altLoc reduction beyond chains.** The reducer is chain-level; a ligand
  modeled as alternate copies *within one chain* is left to BioJava's within-group
  handling. Whole-structure ligand-atom altLoc reduction was rejected (Agent 3
  analysis) because it would move ligand-based ground truth (labels, DCA/DCC
  centers).

---

## 8. References

- Param: `Params.reduce_alternate_conformation_chains`
- Class: `cz.siret.prank.geom.AlternateChainReducer`
- Breaking change: `breaking-changes.md` (2.6, "Alternate-conformation chain reduction")
- Follow-up tracking: `backlog.md` ("Inconsistencies / parity gaps")
- Surface divergence background: `surface_strategy` javadoc; memory note
  `surface-distinct-not-bit-identical`
- Scan artifacts: `p2rank-results/altchain_pdbscan/altchain_events.log` (per-protein
  dropped chains); `p2rank-results/altchain_validation/{on,off}` (holo4k eval)
