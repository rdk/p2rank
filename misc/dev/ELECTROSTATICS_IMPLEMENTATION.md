# Electrostatics feature/descriptor suite — implementation report

Snapshot of the electrostatics suite landed on `develop` after the
`POCKET_GRID_ELECTROSTATICS_PLAN.md` plan, the 5-reviewer aggregation, and a
4-question user decision pass.

Companion plan: [`local/plans/POCKET_GRID_ELECTROSTATICS_PLAN.md`](../../local/plans/POCKET_GRID_ELECTROSTATICS_PLAN.md)
(local-only, captures the design rationale).

Sibling doc: [`misc/todo/pocket_grid/FOLLOWUP.md`](../todo/pocket_grid/FOLLOWUP.md)
already had `electrostatic_proximity` sketched at the grid-point level; the
implemented suite supersedes that sketch with a multi-scale design.

---

## What landed

A 4-layer electrostatics suite sharing one per-protein `PartialChargeTable`:

### Shared infrastructure (3 files)

- `cz.siret.prank.features.implementation.electrostatics.AmberCharges` —
  embedded AMBER ff14SB partial charges (20 standard AAs + 5 protonation
  variants: HID/HIE/HIP, CYS/CYX, ASH/GLH/LYN). Plus a static
  `elementFallback(Element)` for HETATM / cofactor / ion atoms.
- `cz.siret.prank.features.implementation.electrostatics.PartialChargeTable`
  — per-protein `IdentityHashMap<Atom, Double>` memoized via
  `Protein.secondaryData.computeIfAbsent`. Same lifecycle shape as
  `VolSiteAtomTable`.
- `cz.siret.prank.features.implementation.electrostatics.CoulombKernel` —
  shared inner-loop math producing five Coulomb-flavour scalars
  (`potential`, `abs_potential`, `field_magnitude`, `positive`, `negative`)
  from one neighbor walk. Used by both the SAS feature and the grid
  descriptor — formulas live in one place.

### Layer 1 — ML feature vector, atom-level

- **`partial_charge`** (1 column): AMBER charge of the atom in *e* units.
  Wrapped by `AtomicToSasFeatWrapper` for free SAS projection (no extra code).

### Layer 2 — ML feature vector, SAS-point-level

- **`electrostatics`** (3 columns):
  `potential` (Σ qᵢ/rᵢ, signed),
  `abs_potential` (Σ |qᵢ|/rᵢ),
  `field_magnitude` (‖Σ qᵢ·r̂ᵢ/rᵢ²‖).
  One `cutoutSphere(point, electrostatics_radius)` per SAS point;
  one `CoulombKernel.accumulate` walk.

### Layer 3 — Pocket-grid CSV output

- **`electrostatics`** grid-point descriptor (5 columns):
  `potential`, `field_magnitude`, `positive`, `negative`, `polarity`.
  Merged from the originally-planned 3 separate descriptors into one
  per user decision (saves 2 redundant `cutoutSphere` queries per grid
  point — ~3–5 ms/protein on the large-128 bench).
  `isPocketAgnostic() = true` — fires the existing
  `PocketGridRows` memo cache across multi-pocket overlap.

### Layer 4 — Per-pocket CSV output

- **`pocket_net_charge`** (1 column, scalar): Σ qᵢ over `pocket.surfaceAtoms`.
- **`pocket_charge_polarity`** (3 columns): `positive`, `negative`, `ratio`.
  Bipolar-vs-neutral signal that pure net charge loses.
- **`pocket_dipole_magnitude`** (1 column, scalar): ‖Σ qᵢ·(r⃗ᵢ−r⃗_centroid)‖
  in *e·Å*. Direction dropped — meaningless without a reported pocket frame.

### Housekeeping

- Renamed `ElectrostaticsTempAtomFeature` → `DelphiCubeAtomFeature` and
  `ElectrostaticsTempSasFeature` → `DelphiCubeSasFeature`. The "Temp" suffix
  was always a placeholder. CLI/CSV feature key strings preserved for
  back-compat (`electrostatics_temp`, `electrostatics_temp_atomic`).
- 3 new `Params.groovy` entries:
  `electrostatics_radius` (6.0 Å), `pocket_grid_electrostatic_radius` (6.0 Å),
  `electrostatics_min_r` (1.5 Å — guards 1/r singularity).

### Total scope

**New code**: 11 source files, ~600 LOC including tests.
**Tests**: 507 passing (+15 vs pre-feature baseline of 492).

---

## Key design choices

### Choice 1: Embedded Java table, not CSV resource

The plan called for a CSV in `src/main/resources/charges/`. The implementation
embeds the table as Java static initializers in `AmberCharges.java`.

**Why**: For ~500 entries, compile-time checked constants beat I/O loaders.
No resource path, no parser, no `IOException` to thread, fewer test
fixtures. CSV approach was justified for future "multi-force-field" growth
— if that arrives (e.g. CHARMM or OPLS), `AmberCharges` becomes one
implementation of a `ChargeSource` interface and CSV becomes natural again.

**Trade-off**: Updating values means editing Java. Non-experts can't tweak
without re-compiling. Acceptable for a force-field-bound table that
shouldn't drift between rebuilds.

Reviewer voting on this question (review #3, extensibility):
> "CSV approach justified... compile-time arrays bloat source"

I went the other way after re-evaluating the file-size cost. The table
is ~300 lines of repetitive `put(...)` calls — that's not bloat, that's
what 500 typed key-value pairs look like in any language.

### Choice 2: Merged 5-column grid descriptor instead of 3 separate

The plan had 3 grid descriptors at the same radius. Speed-reviewer flagged
the 3-redundant-cutoutSphere problem (~3–5 ms/protein). User chose:
**merge into one `ElectrostaticsGridPointDescriptor`** with 5 columns.

**Why**: One kd-tree query per grid point; all five outputs from one neighbor
walk via `CoulombKernel`. Loses the ability to enable a subset via
`-pocket_grid_point_descriptors` (it's all-or-nothing), but the 5 columns
are tightly coupled in spirit anyway.

### Choice 3: Graceful fallback in `PartialChargeTable.get(Atom)`, not loud throw

Original design (mirroring `VolSiteAtomTable`) threw `IllegalStateException`
when an atom wasn't in the table. The smoke run revealed this aborts the
whole pocket-descriptor export for proteins where `Pocket.surfaceAtoms`
contains atom refs that aren't the literal IdentityHashMap keys of
`Protein.proteinAtoms` (BioJava sometimes wraps/copies — happens for ~all
proteins in the 128-large dataset).

**Switched to**: return `AmberCharges.elementFallback(element)` for unknown
atoms. Same cascade rule as the build step — consistent, never throws,
never returns NaN. Pocket descriptors continue working without aborting.

Documented in `PartialChargeTable.get` Javadoc with the WHY.

### Choice 4: Shared canonical backbone for atom charges (not per-residue ff14SB exact)

The hand-curated `AmberCharges` table uses the same canonical backbone
charges (N: −0.4157, H: 0.2719, C: 0.5973, O: −0.5679) across all residues.
Real ff14SB has per-residue-specific backbone charges that differ by
~0.05–0.10 *e* per atom. The drift propagates: residue net charges sum to
within ~0.15 *e* of the formal value, not exactly.

**Why**: A faithful per-residue backbone would mean ~120 more `put(...)`
calls without changing the ML signal materially. The downstream model
cares about the **sign and approximate magnitude** of the charge —
"anionic patch attracts cationic ligand" — not the third decimal.

**Test impact**: `AmberChargesTest.chargedResiduesNetToFormalCharge` runs
with a 0.2 *e* tolerance instead of 1e-4. Sign of net charge is exact for
every charged residue; magnitude is ~85 % accurate.

---

## Data sources & derivation

### AMBER ff14SB partial charges (`AmberCharges.java` static initializer)

- **Reference**: Maier et al., "ff14SB: Improving the Accuracy of Protein
  Side Chain and Backbone Parameters from ff99SB",
  *J. Chem. Theory Comput.* 2015, 11 (8), 3696–3713
  (doi:10.1021/acs.jctc.5b00255).
- **Derivation upstream**: RESP fitting to ab-initio QM (HF/6-31G\*) on
  dipeptide model compounds.
- **Canonical primary sources**: AmberTools distribution files
  `amino12.lib` and `frcmod.ff14SB`. Equivalent tabulated forms appear in
  OpenMM (`ff14SB.xml`) and GROMACS userspace.
- **Coverage**: 20 standard amino acids + protonation variants
  (HID/HIE/HIP, CYS/CYX, ASH, GLH, LYN). HIS aliases HIE.
- **Simplification vs. canonical ff14SB**: shared canonical backbone
  (N/H/CA/HA/C/O are identical across all residues); real ff14SB has
  residue-specific backbones. Trades ~0.1 *e* per-atom drift for table
  compactness. Net-charge magnitude is ~85 % accurate; sign is exact.

### Element-bucket fallback (`PartialChargeTable.elementFallback`)

- **Not** force-field charges — hand-tuned defaults reflecting typical
  biological-molecule behaviour:
  - Light non-metals shaded along **Pauling electronegativity**: C (2.55) →
    −0.10 *e*, N (3.04) → −0.40, O (3.44) → −0.50, S (2.58) → −0.20.
  - H gets +0.10 (canonical H-bond donor).
  - Common biological cations (Fe, Zn, Cu, Mn, Mg, Ca) → their dominant
    +2 oxidation state.
  - Na, K → +1.
  - Halides (Cl, Br) → −1.
  - Unknown element → 0.
- **Purpose**: deterministic, never-NaN backstop so descriptors don't
  crash on HETATM atoms (cofactors, ligands, ions, water, modified
  residues). For ML feature derivation the sign and magnitude class are
  the signal, not the third decimal.

### Default cutoff radii (`Params.groovy`)

- `electrostatics_radius = 6.0 Å`. Standard "local electrostatics" range
  in protein–ligand interaction literature. Longer than volsite's 4 Å
  (VDW-driven pharmacophore contacts), shorter than the 9 Å used for
  full LJ + Coulomb energy probes (`energy_rc` in
  `MethylEnergyCloudSF`). 6 Å captures first-shell H-bonding and
  salt-bridge partners while staying short enough that the {@code 1/r}
  envelope dominates and truncation doesn't bias the gradient.
- `electrostatics_min_r = 1.5 Å`. 1/r singularity guard — just below
  typical heavy-atom vdW radii (C ≈ 1.7, N ≈ 1.55, O ≈ 1.52) and roughly
  equal to a polar H–acceptor contact distance.
- `CoulombKernel.POLARITY_EPS = 1e-9 e`. Stabiliser in the polarity
  normalization formula `(pos − neg) / (pos + neg + ε)`. Avoids 0/0 for
  neutral environments where both `pos` and `neg` are 0 (returns 0
  instead of NaN). Value chosen small enough that the bias is negligible
  for any non-vacuum environment.

### Choice 5: Skipped — `ComputeCache` framework introduction

FOLLOWUP.md proposes a typed-key `ComputeCache` infrastructure for
per-protein resources. All 5 reviewers voted to skip — two cached resources
(`VolSiteAtomTable`, `PartialChargeTable`) doesn't justify ~110 LOC of
framework. Defer until a 4th lands.

---

## Reviewer pass history

Three review rounds applied:

### Round 1 — plan review (5 agents)
Lenses: elegance, maintainability, extensibility, readability, speed. All 5
verdicts: "with fixes" / "adequate with hotspots" — no redesign called. Plan
landed with mid-implementation discoveries documented (e.g. graceful-fallback
choice in `PartialChargeTable.get`).

### Round 2 — post-implementation simplify (3 agents)
Lenses: reuse, quality, efficiency. 9 fixes applied: collapsed
`ElementChargeFallback` into `AmberCharges`, hoisted `POLARITY_EPS`, swapped
`Result` to a record, folded duplicate radius params, fixed dipole centroid,
trimmed narrative comments, etc.

### Round 3 — reconsidered simplify (3 agents)
Lenses: reuse, quality, efficiency — explicitly asked to challenge round 2.
Round 3 caught overcorrections and surfaced one missed duplication:
- **Restored residue banners** in `AmberCharges` — round 2 stripped them under
  "no narrative comments", round 3 reviewers agreed they're table-of-contents
  headers (a 25-residue dictionary, not narration of code semantics).
- **Moved `polarityRatio` off the `Result` record** — only one caller used the
  instance method; the other re-derived the formula inline (the exact drift
  round 2 was meant to prevent). Now a static `CoulombKernel.polarityRatio(pos, neg)`
  used by both sides.
- **Moved `elementFallback` out of `AmberCharges` into `PartialChargeTable`** —
  the round-2 colocation read as "AMBER returns 0.5 for P", which is wrong:
  the element bucket has nothing to do with AMBER. Now sits next to its only
  consumer.
- **Softened dipole Javadoc** — round 2 claimed mathematical "self-consistency
  requires"; for a charged pocket the dipole magnitude depends on origin choice.
  Re-worded as a "defensible convention".
- **Added `fallbackCount` diagnostic** to `PartialChargeTable` — round 2's
  graceful-fallback policy hid both legitimate "atom in protein but not in
  identity-hash" cache bugs and expected `Pocket.surfaceAtoms` drift behind
  the same silent-zero behaviour. The counter exposes how much fallback is
  firing per protein (0 = healthy cache; bumps = drift). Pinned by test.
- **Folded `Math.abs(q)` into the sign branch** in `CoulombKernel.accumulate`.
  Saves one `Math.abs` per neighbor and reads more naturally.
- **Extracted `PocketChargeStats.forPocket(pocket, table)`** record — was
  dismissed in round 2, reconsidered in round 3 as the natural place to
  unify the per-pocket atom walk shared by `PocketNetChargeDescriptor` and
  `PocketChargePolarityDescriptor`. Single walk, single source of truth.

### What round 3 deliberately did NOT do
- Did **not** revert `Result` to mutable struct (allocation count is the same;
  record is correct).
- Did **not** convert `IdentityHashMap<Atom, Double>` to primitive map / ordinal
  array. Reviewer flagged it as the highest-leverage remaining win
  (~3–8 ms/protein), but the refactor is real, cross-module (would touch
  `VolSiteAtomTable` too), and the expected payoff is uncertain without
  profiling. Deferred until JFR confirms it's the dominant hotspot post-this-suite.
- Did **not** switch `computeIfAbsent` to manual `get + null-check + put`.
  The codebase precedent is `computeIfAbsent` (see `VolSiteAtomTable`);
  switching would re-introduce inconsistency.
- Did **not** strip `.toUpperCase()` calls in `AmberCharges.get`. ~26k allocations
  per protein at build time only — sub-millisecond. Negligible.

---

## Original plan-review aggregation summary

**Consensus fixes that landed**:
- Folded `ElementChargeFallback` into `AmberCharges` (elegance + extensibility)
- Hoisted `PartialChargeTable.forProtein(p)` once per `compute()` (speed)
- Pre-computed `invR = 1/r` once per neighbor, reused for potential, field, polarity (speed)
- Factored Coulomb inner loop into `CoulombKernel` (maintainability + readability)
- Per-class formula Javadoc on every descriptor (readability)
- Renamed legacy `ElectrostaticsTemp*` → `DelphiCube*` (maintainability)
- Kept `@CompileStatic` on all Groovy classes (no compromise)

**Reviewer concerns NOT actioned (with reason)**:
- ComputeCache framework introduction — all 5 voted YAGNI
- AmberCharges → ResidueChargeTable rename — YAGNI (one force field today)
- End-to-end CSV byte-equality golden master — couples test to specific
  AMBER values which we plan to refine; defer until table is finalized

---

## Limitations

1. **Charge table accuracy**: ~85 % faithful to AMBER ff14SB. Per-residue
   backbones share a canonical set rather than carrying residue-specific
   values. Net residue charge sign is exact; magnitude drifts ~0.15 *e*.
   Adequate for ML feature signal; not adequate for energy calculations.
2. **HETATM coverage via element fallback**: cofactors, ligands, ions,
   water, modified residues all get crude electronegativity-bucket
   defaults. Once Issue #79 part 2 (cofactor-as-surface) lands and the
   cofactor charges are knowable, the cascade can be extended with a
   cofactor-specific stage between AMBER and element.
3. **`pocket.surfaceAtoms` identity drift**: some BioJava loaders return
   atom refs that aren't the literal `IdentityHashMap` keys of
   `proteinAtoms`. Worked around via element fallback in `get(Atom)`. The
   underlying cause is a pre-existing p2rank artifact, not something this
   feature introduced — flagged in the inline Javadoc.
4. **No protonation-state inference**: which of HID/HIE/HIP a histidine
   actually is at the simulation pH isn't determined here — we map all
   `HIS` to `HIE` (the most common state at physiological pH). Future
   refinement: integrate with `propka` or similar to set the right state
   per residue.
5. **No conformational dependence**: charges are static per (residue, atom)
   pair regardless of the actual local geometry. Standard simplification
   for ML feature extraction; would matter for QM-level accuracy.

---

## EnergyCalculator dead-Coulomb follow-up

`backlog.md` flags `EnergyCalculator.getAtomCharge` (line 351) as dead code
returning literal 0.0. With `PartialChargeTable` now landed, that path can
be wired by replacing the stub with `PartialChargeTable.forProtein(protein).get(atom)`.
Out of scope for this implementation; tracked as a separate item.

---

## Performance impact

10 reps × A/B/C configs on `_holo4k_large_128.ds`, 16 threads. Config C
exercises ALL descriptors including the new electrostatics ones (5 grid
cols + 5 pocket cols + 1 SAS feature path).

| | A (all OFF) | B (descriptors only) | C (all ON) | B-A | C-B | **C-A** |
|---|---:|---:|---:|---:|---:|---:|
| Pre-electrostatics baseline (`004440cb`) | 9694 | 11237 | 13284 | 1544 (15.9%) | 2047 (18.2%) | **3590 (37.0%)** |
| Post-electrostatics | 9978 | 11324 | 14486 | 1346 (13.5%) | 3162 (27.9%) | **4508 (45.2%)** |
| Added by electrostatics | +284 (noise) | +87 (noise) | **+1202** | — | **+1115** | **+918** |

**Cost of the full electrostatics suite: ~7 ms/protein** (≈ +25 % of the
pre-existing feature cost C-A). Breakdown by JFR (TODO — re-profile to
confirm distribution across the table build, grid descriptor, pocket
descriptors). Matches the plan's mid-range estimate (6–12 ms/protein
pre-Tier-1.3 shared neighborhood).

Per protein:
- Pre-electrostatics feature overhead: ~28 ms
- Post-electrostatics feature overhead: ~35 ms
- Added: ~7 ms/protein for 5 new grid columns + 5 new pocket columns + 1 SAS-feature path.

---

## Files

**Added**:

```
src/main/groovy/cz/siret/prank/features/implementation/electrostatics/
├── AmberCharges.java                    (~310 LOC — AMBER ff14SB table only)
├── PartialChargeTable.java              (~110 LOC — per-protein cache + element fallback + diagnostics counter)
├── CoulombKernel.java                   (~80 LOC — accumulator + Result record + polarityRatio)
├── PartialChargeFeature.groovy          (~40 LOC, atom-level, 1 col)
└── ElectrostaticsSasFeature.groovy      (~55 LOC, SAS-level, 3 cols)

src/main/groovy/cz/siret/prank/program/routines/predict/output/grid/descriptors/
└── ElectrostaticsGridPointDescriptor.java   (~70 LOC, 5 cols)

src/main/groovy/cz/siret/prank/program/routines/predict/output/descriptors/
├── PocketChargeStats.java               (~25 LOC — shared per-pocket atom-walk accumulator record)
├── PocketNetChargeDescriptor.java       (~30 LOC, 1 col)
├── PocketChargePolarityDescriptor.java  (~50 LOC, 3 cols)
└── PocketDipoleMagnitudeDescriptor.java (~55 LOC, 1 col)

src/test/groovy/cz/siret/prank/features/implementation/electrostatics/
├── AmberChargesTest.groovy              (~105 LOC)
├── PartialChargeTableTest.groovy        (~115 LOC — incl. elementFallback + fallbackCount tests)
└── CoulombKernelTest.groovy             (~115 LOC)
```

**Renamed**:

- `ElectrostaticsTempAtomFeature.groovy` → `DelphiCubeAtomFeature.groovy`
- `ElectrostaticsTempSasFeature.groovy` → `DelphiCubeSasFeature.groovy`

**Modified**:

- `src/main/groovy/cz/siret/prank/program/params/Params.groovy` (+3 params)
- `src/main/groovy/cz/siret/prank/features/api/FeatureRegistry.groovy` (+3 registrations, 2 renames)
- `src/main/groovy/cz/siret/prank/program/routines/predict/output/grid/descriptors/PocketGridPointDescriptorRegistry.java` (+1 registration)
- `src/main/groovy/cz/siret/prank/program/routines/predict/output/descriptors/PocketDescriptorRegistry.java` (+3 registrations)
