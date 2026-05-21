# Pocket Grid — Follow-ups

Post-ship work for the pocket-grid feature. Two flavors:

- **[Future descriptor ideas](#future-descriptor-ideas)** — concrete designs
  sketched but not implemented, deferred until the underlying perf framework
  lands or a real workload justifies them.
- **[Perf observations](#perf-observations)** — loose notes gathered while
  benchmarking; not blocking work, captured here so the mitigation isn't
  re-derived later.

---

## Future descriptor ideas

None of these are bugs or blockers. They are ideas with enough detail to
pick up later without re-deriving the design.

### Prerequisite: the `ComputeCache` framework

Three of the four ideas below assume a per-protein compute cache the
descriptors can use to share precomputed per-protein resources (e.g. an
atom-classification table). The cache is also the foundation for the
volsite performance work (Tier 4 in the post-squash audits).

Sketch shape:

```
CacheKey<T>        — typed key, static-final per resource
ComputeCache       — per-protein Map<CacheKey<?>, Object> with get/put
                     + computeIfAbsent. Threaded through both
                     PocketGridContext and PocketGridPointContext.
                     One instance per protein, created in
                     PocketGridOutputs.exportIfEnabled.
```

Per-protein resources are normal classes with a static `KEY` and a
static factory `forProtein(Protein)`. Each is independently testable.

Foundation alone is ~110 lines (CacheKey + ComputeCache + context
fields + plumbing through both rows classes + test fixtures). Adding
one resource + descriptor is ~80 lines.

**Trigger to do this:** a user reports VolSite descriptor compute as a
noticeable cost on a real protein. Then implement, in order:

1. Foundation only (no behavior change).
2. `VolSiteAtomTable` cached per protein + wire both volsite descriptors.
   Benchmark for the ~5-20× win.
3. Per-point cache for the pocket-agnostic volsite descriptors (~6-line
   idiom inside each). Benchmark for the additional ~1.5-2×.

Stop after (3) unless one of the descriptors below has a concrete user.

### Grid-point descriptor ideas

#### `local_atom_density` — 2 × i32

Counts of polar / hydrophobic protein atoms within R Å of the grid point.

- Output columns: `local_atom_density.polar`, `local_atom_density.hydrophobic`
- Per-protein resource: `AtomCategoryTable.forProtein(p)` — `boolean[2][N]`
  per atom (polar / hydrophobic) keyed by atom identity. Reusable by any
  future "atom-typed nearby" descriptor.
- Param: `pocket_grid_atom_density_radius` (default ~4.0 Å).
- Pocket-agnostic, so eligible for the per-point cache idiom.

#### `electrostatic_proximity` — 1 × f64

Coulomb sum `Σ q_i / r_i` over protein atoms within R Å, using partial
charges from an embedded force-field table (AMBER ff14SB-ish, or simpler
per-element averages as a fallback for unknown atom names).

- Output column: `electrostatic_proximity`
- Per-protein resource: `AtomChargeTable.forProtein(p)` — `double[N]`
  partial charges keyed by atom identity.
- Param: `pocket_grid_electrostatic_radius` (default ~6.0 Å — slightly
  bigger than volsite because electrostatics is longer-range).
- Pocket-agnostic, eligible for per-point cache.
- Sets a precedent for any future MM-flavored descriptor.

#### `conservation_proximity` — 2 × f64

Max + mean residue conservation score over residues within R Å of the
grid point. Useful when conservation data is loaded
(`-conservation_files`).

- Output columns: `conservation_proximity.max`, `conservation_proximity.mean`
- Per-protein resource: `ConservationByResidue.forProtein(p)` — returns
  `null` when conservation isn't configured for the protein. Descriptor
  emits `NaN` in both columns when the resource is null. Documented as
  "data-availability dependent".
- Param: `pocket_grid_conservation_radius` (default ~5.0 Å).
- Pocket-agnostic, eligible for per-point cache.

### Per-pocket descriptor idea

#### `residue_chemistry_summary` — 5 × f64

Fractions over the pocket's surface residues, broken into five chemical
classes:

- `residue_chemistry_summary.hydrophobic` — Ala, Val, Leu, Ile, Met, Pro, Cys, Gly
- `residue_chemistry_summary.polar` — Ser, Thr, Asn, Gln, His
- `residue_chemistry_summary.acidic` — Asp, Glu
- `residue_chemistry_summary.basic` — Lys, Arg
- `residue_chemistry_summary.aromatic` — Phe, Trp, Tyr

Sum to 1.0 (or close, depending on how His is split between polar and basic).

- Per-protein resource: `ResidueChemistryTable.forProtein(p)` —
  `Map<Group, ChemicalClass>` for all residues in the protein. Trivial
  to compute, but going through the cache pattern keeps every
  descriptor's data-access shape uniform.
- Cheap; no radius param.
- Per-pocket scope means no per-point cache needed.

### What's deliberately NOT here

- **`nearest_atom`** descriptor — looked tempting but it would just use
  the already-cached `Atoms.kdTree`. No new per-protein resource. Skip.
- **A grid-point descriptor that depends on the assigned pocket** —
  perfectly fine to add, just doesn't motivate the cache framework
  (can't reuse across pockets).
- **Per-pocket descriptors that need eigendecomposition** (e.g.
  `shape_eigenvectors` returning the principal axes as vectors).
  Possible follow-on to `principal_moments` but needs careful thought
  about output shape (3 × 3 numbers per pocket — a multi-column
  descriptor with 9 columns or three separate 3-column descriptors).
  Defer until someone has a use case.

### Cross-references

- Design discussion: see commit history around 2026-05; the
  ComputeCache sketch lived in conversation and is not in a source file.
- Audit findings that motivated this: the "Tier 4 efficiency"
  items (Pre-classify atoms, per-point cache, hoist Params reads) in
  the post-squash audit cycle.

---

## Perf observations

Loose observations gathered while benchmarking; not blocking work.

### JIT Code Cache fills on long runs

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

### Per-protein parallelism gap

After the HPPC `LongIntHashMap` swap (commit b48caeec), coach420
pocket-grid export at 16 threads runs at ~35% CPU utilization on the
grid/writer phase, despite GC being ~1.5% (negligible). The remaining
gap is structural — grid build + write is single-threaded per protein,
and the dataset has variance in protein size so the tail straggles.

If this becomes worth chasing: parallelize the per-pocket loop inside
`PocketGridBuilder.build` (the assigner + filler calls are independent
per pocket). Likely 1.2-1.5× speedup on the multi-pocket proteins
without disturbing the single-pocket common case.
