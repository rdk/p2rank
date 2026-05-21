# Post-2.5.1 Audit Findings

Punch-list captured from a 10-agent audit of all changes between tag `2.5.1`
and `develop` (branch `develop`, ~218 commits, ~523 files). Each item is
grouped by severity. File paths are repo-relative; line numbers reflect
state at audit time and may drift.

This is a tech-debt / triage backlog — items here have not been fixed.
Resolve items individually as the surrounding code is touched, or schedule
focused cleanups.

---

## Highest-priority bugs (real or near-real)

- **`VoxelHashAssigner` cell-prune lower bound is too aggressive.**
  `src/main/groovy/cz/siret/prank/program/routines/predict/output/grid/assign/VoxelHashAssigner.java:58`
  uses `(di²+dj²+dk²)·spacing²` as a lower bound that ignores the sub-cell offset
  of `q`. Concrete miss with shipping defaults: `q=(0.59,0,0)`, `spacing=1.2`,
  `cutoff=2.5` skips a cell whose true distance is ~2.17 Å. Silently breaks the
  "both assigners agree" invariant documented on `PocketAssigner.java:23-26`.
  Existing test (`PocketGridBuilderTest.groovy:158`) misses it because its
  single SAS point lands exactly on a lattice center. Fix: correct the lower
  bound (`max(0, |di|*spacing − spacing/2)` per axis), or drop the prune.

- **Lazy-init race on singleton energy features.**
  `MethylEnergyFeature.groovy:28-40`, `MethylEnergyCloud{,X,XFull,X2,X2Full}SF.groovy:67-80`,
  `AbstractProbeEnergyFeature.groovy:47-59,66`.
  `calculator`/`config` are non-volatile fields on registry singletons; multiple
  worker threads can construct (and observe partially constructed) calculators.
  `ConcurrencyTest.groovy:27-28` constructs the calculator on the main thread
  before spawning threads, so the test does NOT cover the race it was meant to
  validate. Fix: `volatile` + DCL, or do one-shot init under a synchronized guard
  in `preProcessProtein`; update the concurrency test to construct under contention.

- **Coulomb plumbing is dead code.**
  `EnergyCalculator.getAtomCharge` always returns 0
  (`src/main/groovy/cz/siret/prank/features/implementation/energy2/calc/EnergyCalculator.groovy:351-357`).
  `enableCoulomb`, `dielectricConstant`, `coulombConstant` are structurally
  inert. `testCationProbeIncludesBothLJAndCoulombTerms`
  (`EnergyCalculatorTest.groovy:194-209`) is a false positive against its own
  name. Either wire charges, or rip the Coulomb path out.

- **PUResNet `surfaceAtoms` come from a separate `Structure`.**
  `src/main/groovy/cz/siret/prank/domain/loaders/pockets/PUResNetLoader.groovy:50,55`.
  The Concavity fix (`c143e0fa`) only re-routed the `Prediction` binding;
  PUResNet has the same identity-mismatch class still uncaught (atoms are from
  a sub-PDB, not from `queryProtein`). ConcavityLoader's own `cutoutShell` still
  runs against the residue subset rather than `queryProtein.exposedAtoms`. Fix:
  re-link atoms by PDB serial against `queryProtein.allAtoms.getByID`, mirroring
  `FPocketLoader.groovy:137`.

- **`AhojUbsSiteParser` will crash on "older full" CSVs.**
  `src/main/groovy/cz/siret/prank/domain/loaders/AhojUbsSiteParser.groovy:46`
  uses `pocket_class` as the sole marker but then unconditionally reads
  `rg`/`n_unp_pockets`/`n_unp_pockets_multichain` in `AhojSiteInfo.fromCsvRecord`.
  Commons CSV throws `IllegalArgumentException` for unmapped header names.
  Fix: add `record.isMapped(name)` guards, or pick a marker column guaranteed
  present in every variant.

- **`Ligands.loadLigandsFromSeparateFiles` doesn't filter cofactors.**
  `src/main/groovy/cz/siret/prank/domain/Ligands.groovy:140-198`.
  When `load_ligands_from_separate_files=true`, the cofactor branch in
  `Protein.loadStructure` (`Protein.groovy:574-592`) adds cofactor atoms to
  `proteinAtoms`, but the loader has no `.findAll { !loaderParams.isCofactor(it) }`
  guard. The cofactor becomes an *ignored Ligand* with contact distance ≈ 0.
  Self-acknowledged as cosmetic in `documentation/cofactors.md:421-429` and
  dev doc Known Limitation #1, but no test exercises it.

- **Aromatic-probe energy cap is applied before the cosine switch.**
  `EnergyCalculator.groovy:248-250` clips pre-switch, then multiplies by
  `neighbor.switchValue` at line 291. May be intended, but contradicts inline doc
  and `testAromaticRingEnergyCap` (`EnergyCalculatorTest.groovy:132-146`) doesn't
  exercise the divergence (single atom inside RON window). Decide per-atom vs
  per-point capping and align doc + test.

- **Centroid-source divergence across pocket loaders.**
  Pocketeer uses the server-supplied JSON `centroid`; FPocket uses voronoi
  centerOfMass; Concavity/PUResNet use geometric centroid post-fix;
  Seq2Pocket/SwinSite use `gridPoints.centroid`/`surfaceAtoms.centroid`. No
  documented contract for what "centroid" means across loaders. Either document
  the policy or normalize to geometric.

- **`RescorePocketsRoutine.checkDataset` has an empty truthy branch.**
  `src/main/groovy/cz/siret/prank/program/routines/predict/RescorePocketsRoutine.groovy:48-56`
  has `if (runFpocketAdHoc) { /* comment */ }` — the check appears inverted.
  Re-verify intended semantics.

---

## Inconsistencies (renames / strategy lookups / cross-format / code vs docs)

- **`NewPymolRenderer` class name is stale** — `NewPymolRenderer.groovy:28`.
  Two distinct active classes (`NewPymolRenderer` vs `PymolRenderer`). The
  "New" prefix predates a refactor. The cofactor block depends on a static
  method on the misnamed class (`PymolRenderer.groovy:147`). Rename.

- **Assigner vs filler lookup styles disagree.** Assigners go through
  `PocketAssignerRegistry`; fillers use a hand-written switch
  (`PocketGridBuilder.java:118-127`) plus a hard-coded `['morph_closing','none']`
  list (`Main.groovy:223`). Two places to keep in sync per new filler.

- **`NoOpFiller` returns input BitSet without cloning, `MorphologicalCloser`
  clones.** Mixed ownership semantics; `PocketShapeFiller.java:30` contract is
  silent. Risk of silent corruption if a future caller mutates the per-pocket
  BitSet under `fill=none`.

- **`SphericityDescriptor` degenerate convention diverges** —
  `SphericityDescriptor.java:32,48`. Empty pocket → 0.0, single-point pocket → 1.0.
  Volume / RadiusOfGyration / PrincipalMoments / NumGridPoints return 0 for both.
  Joining columns: one says "perfectly spherical", others say "empty".

- **`PocketGridPdbSidecar.write` called by both renderers** —
  `PocketGridPymolRenderer.groovy:121` + `PocketGridChimeraXRenderer.groovy:157`.
  When both are enabled, same sidecar PDB is written twice (identical content).

- **CSV `NaN`/`INF` handling is unspecified.** `Formatter` emits `NaN`/`∞`;
  INT columns silently coerce NaN to 0 (`TableExporter.groovy:142`). Document
  in `documentation/export-pocket-descriptors.md` and `export-points.md`, or
  pick a canonical sentinel.

- **Sort-direction comment in `PrincipalMomentsDescriptor.java:96-101`** says
  "Sort descending." while `Arrays.sort` is ascending. Downstream indexing
  compensates. **Not wanted** (decision 2026-05-21) — current behaviour is
  correct, the comment ambiguity is acceptable.

- **Cofactor case-sensitivity mismatch.** `CofactorHandler.parseOne`
  (`CofactorHandler.groovy:325-335`) uppercases; `Dataset.LigandDefinition.parse`
  (`Dataset.groovy:761-805`) does not. `Params.groovy:536-537` doc claims
  case-sensitive while cofactors are normalized.

- **`distro/prank.bat` misses JVM flags.** Lines 11-13 set only the three
  `--add-opens`. Missing `--enable-native-access=ALL-UNNAMED` and the
  Java-23+ `--sun-misc-unsafe-memory-access=allow` block that `distro/prank`
  and `prank.sh` apply. zstd-jni native warnings + future-Java compatibility
  gap on Windows.

- **`atomRoleCache`/`atomChargeCache` keyed by BioJava `Atom` identity.**
  `EnergyCalculator.groovy:131-132`. Atoms across proteins are distinct
  identities; cache never hits across proteins and grows monotonically — slow
  leak for long-running calculators.

- **`computeEnergyForPoint` returns `List<Double>` (boxed).**
  `EnergyCalculator.groovy:146-179`. Boxing per neighbor per probe in the hot
  loop. Legacy `LJEnergyCalculator.computeEnergyForPoint` returns `double`.

- **`Evaluation.closestPocket()` ignores `site_eval_sas_pts_as_atoms`.**
  `Evaluation.groovy:81` doc says "considering DCA measure" but uses
  `site.atoms.dist(p.centroid)` unconditionally. Diverges from DCA semantics
  when the param is enabled.

- **`Evaluation.getStats()` returns `LinkedHashMap`** (comment claims "keep
  insertion order"), but the immediate caller `EvalResults.getStats()` puts
  everything into a `TreeMap` (`EvalResults.groovy:189`) — insertion order is
  lost. Either drop the misleading comment or use `LinkedHashMap` downstream.

- **PyMOL pocket-grid renderer iterates `1..maxRank`; ChimeraX iterates
  `perPocketBasenames.keySet()`.**
  `PocketGridPymolRenderer.groovy:167,201,242`.
  Cosmetic-only: P2Rank ranks pockets contiguously (every `predict`-path and
  in-tree loader except `SiteHoundLoader` assign `i++`/`rank++`), and the
  sidecar PDB strips ranks whose `filled` BitSet is empty. PyMOL therefore
  emits empty `pocket_grid_N`/`pocket_vol_N`/`pocket_gauss_N`/`pocket_hull_N`
  objects when the assigner produced no points for a small pocket — they
  render as invisible but clutter the Models panel. Mirror the ChimeraX
  iteration pattern (`a3efd084`) for parity; not a correctness fix.

- **PyMOL grid `solvent_radius=0` vs ChimeraX non-zero probe.**
  `PocketGridPymolRenderer.groovy:189-190` vs `PocketGridChimeraXRenderer.groovy:264`.
  `vis_pocket_grid_volume_radius` means different things to the two renderers.
  Documented in code; not in the param help.

- **`pocket=0` (unassigned) points never reach the PDB sidecar.**
  `PocketGridPdbSidecar.java:56-60` iterates `grid.getPocketToPointIndices()`
  only. When `-pocket_grid_include_unassigned 1`, CSV/Parquet has the rows
  but visualization silently drops them. Document or extend.

---

## Doc / config drift

- **README badge stuck at 2.5.1.** `README.md:11` vs `build.gradle:25`
  (`2.6.0-dev.9`). Typo `./make-disro.sh` at `README.md:225`.

- **`distro/config/default_rescore.groovy:120-122`** still has the misleading
  "considered cofactor" wording on `ignore_het_groups` (the `default.groovy`
  copy was fixed in the 2026-05-21 cleanup; the rescore config copy still
  needs the same edit).

- **`Params.groovy:536-537`** doc on cofactor matching is stale (claims
  case-sensitive — actually normalized).

- **`documentation/dev/cofactors.md:36,39`** describe R11/R14 as "INFO"
  while line 69 of the same file documents the promotion to WARN — internal
  inconsistency in the dev doc.

- **CI matrix is `17,21,25,26` only** (`.github/workflows/develop.yml:23`) and
  distribution switched temurin → oracle (commit `1997ab94`). **Not wanted**
  (decision 2026-05-21) — Java-version coverage and CI distribution choice
  are intentional; README's "tested up to Java 25" wording will refresh at
  the 2.6 release.

- **`PocketDescriptor.java:29-31` "fits in i32" contract** is unenforced;
  a descriptor producing `1e20` for an INT column silently emits
  `Integer.MAX_VALUE` or wraps. Add `Math.toIntExact` at the writer.

- **`PointExportData.create()` doc says "(for predict mode)"**
  (`PointExportData.groovy:141`) but it's also used by `rescore`
  (`ModelBasedRescorer.groovy:97,168`).

- **`PocketDescriptor.java:29-31` "fits in i32" contract** is unenforced;
  a descriptor producing `1e20` for an INT column silently emits
  `Integer.MAX_VALUE` or wraps. Add `Math.toIntExact` at the writer.

- **`PointExportData.create()` doc says "(for predict mode)"**
  (`PointExportData.groovy:141`) but it's also used by `rescore`
  (`ModelBasedRescorer.groovy:97,168`).

---

## Stale comments / dead code

The Tier 5 cleanup pass on 2026-05-21 resolved the entries below this section.
Items kept open here are intentional carry-forwards.

- **`EnergyCalculatorConfig.roleRulesCSV` + `role-rules.csv` resource are
  wired but read nowhere.** `EnergyCalculatorConfig.groovy:28,40,143,162-164`.
  `AtomRole.classify` is hardcoded. Field is now marked `// unused:`; resolve
  by either making `AtomRole` data-driven or deleting the plumbing.

- **`LoaderParams.groovy:20-22`** stale `TODO get rid of this global variable`
  on `ignoreLigandsSwitch`. Pre-existing; still legitimate.

- **`FPocketLoader.groovy:149`** dead `pocket.centroid` write (overridden by
  `getCentroid()`). Marked `// unused:`; kept until the override is removed.

- **`FPocketLoader.groovy:155`** `// probably not needed` (years old);
  `:142` fpocket3 TODO. Both pre-existing.

- **`ConcavityLoader.groovy`** not `@CompileStatic` — every other loader is.
  Adding it risks surfacing latent type errors; do under a separate change
  with a compile + test pass.

- **`PocketeerLoaderTest`** missing `predictionIsBoundToQueryProtein` and
  empty-input tests (every other new loader test has both). Not actually a
  stale-comment item — tracked here for completeness; belongs under
  test-coverage gaps.

- **`MethylEnergyFeature.groovy:55,67-70`** commented-out try/catch skeleton +
  commented alternative neighbour-atom path. Kept intentionally (decision
  2026-05-21).

- **`misc/development-notes.md`** is down to a single 6-line note — kept
  intentionally (decision 2026-05-21).

- **`distro/prank.bat:14`** `set "JAVA_OPTS=%JAVA_OPTS%"` no-op. Kept
  intentionally (decision 2026-05-21).

- **`AbstractScalarPocketDescriptor.java:21-23`** comment says "both shipped
  descriptors are multi-column" — accurate today, will silently lie when a
  scalar grid-point descriptor is added.

---

## Test-isolation gaps

The Tier 6 cleanup pass on 2026-05-21 resolved the items below; only the
registry thread-safety carry-forward remains. Note: JUnit5 parallel execution
is not enabled in this project (no `junit-platform.properties`,
no `parallel.enabled=true`), so `@Isolated`/`@ResourceLock` annotations are
forward-compatibility documentation only. The real fix value here was the
save/restore additions.

- **`PocketDescriptorRegistry` / `PocketGridPointDescriptorRegistry`** —
  `NamedRegistryHelper`-backed `LinkedHashMap` is not synchronized. Production
  paths only mutate at class init; tests use balanced `@BeforeAll`/`@AfterAll`
  or `try`/`finally`. Latent issue only — surfaces if JUnit parallel execution
  is ever enabled and two test classes register/unregister concurrently.

---

## Performance nits

- **`computeEnergyForPoint` boxes doubles** (above).

- **`EnergyCalculator.groovy:170`** `config.probeParams[probe]` `EnumMap.get`
  per neighbor per probe; hoist to a pre-sized `ProbeParams[]` indexed by
  `probeIdx`.

- **`PocketGridBuilder.java:83`** `Map<Integer, BitSet> pocketToPointIndices`
  uses boxed `Integer` keys while the rest of the file goes to lengths to
  avoid boxing (`LongIntHashMap`, primitive `int[]`). Use `IntObjectHashMap`.

- **`MorphologicalCloser.fill` clones empty rawShell**
  (`MorphologicalCloser.java:30`); unnecessary, inconsistent with `NoOpFiller`.

- **`KdTreeAssigner.computeRawShell` dedup branch is dead**
  (`KdTreeAssigner.java:41`): every atom returned by the KD tree is in
  `latticeIndex` by construction.

---

## Top-5 if you only fix five things

1. **Fix `VoxelHashAssigner` cell-prune lower bound** (or drop it and rely on
   the post-fetch distance check). Restores the assigner-strategy equivalence
   the docs promise.
2. **Make energy-feature lazy-init actually thread-safe**
   (`MethylEnergyFeature`, `AbstractProbeEnergyFeature`); fix `ConcurrencyTest`
   to construct calculators under contention.
3. **Guard `AhojSiteInfo.fromCsvRecord` with `record.isMapped(...)`** for the
   new `rg`/`n_unp_pockets[_multichain]` columns, so the parser doesn't crash
   on older "full" CSVs.
4. **Re-link `PUResNetLoader.surfaceAtoms` to `queryProtein`** by PDB serial
   (mirror `FPocketLoader.groovy:137`); same identity-mismatch class as the
   Concavity fix.
5. **README/help.txt/`distro/prank.bat` trio**: bump the version badge, fix
   the `./make-disro.sh` typo, regenerate `help.txt` to list current commands,
   and bring Windows launcher JVM flags up to parity with the Bash launchers.
