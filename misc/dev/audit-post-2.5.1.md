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
  compensates; comment is misleading.

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

- **Removed flags/commands still referenced in docs:**
  - `documentation/random-examples.md:7,12,17,22` — singular `-conservation_dir`
    (removed in 2.2, renamed to `-conservation_dirs`).
  - `documentation/hidden-commands.md:65` — `fasta-mask` (actual: `fasta-masked`).
  - `documentation/hidden-commands.md:83` — `analyze reduce-to-chains`
    (actual: `transform reduce-to-chains`).
  - `documentation/training-score-transformers.md:4` — `*_pockets.csv` (actual:
    `*_predictions.csv`).

- **`src/main/resources/help.txt` is severely outdated.** Lists only 5 commands;
  `Main.groovy:621-645` registers 12+. Typos `dafault`, `comamnd`. Doesn't
  mention any 2.6 loader rescorers despite `documentation/rescoring.md`
  describing 10.

- **`distro/config/default.groovy:128-130`** comments `ignore_het_groups` as
  "considered cofactor". After `-cofactors` shipped in 2.6, this is actively
  misleading. Same comment in `distro/config/default_rescore.groovy:120-122`.

- **`distro/config/default.groovy` is missing new params:**
  `cofactors`, `cofactor_max_protein_dist`, `aa_mapping`, `vis_highlight_cofactors`.

- **`documentation/cofactors.md:190,389`** say "logs an INFO warning"; code
  logs `WARN` (`CofactorHandler.groovy:222,261`).
  `documentation/dev/cofactors.md:36,39` also describe R11/R14 as "INFO"
  while line 69 of the same file documents the promotion to WARN.

- **`Params.groovy:536-537`** doc on cofactor matching is stale (claims
  case-sensitive — actually normalized).

- **`feature-setup.md:8`** still says "P2Rank version: 2.4"; collapsibles
  hardcode `P2Rank 2.3-dev.1`. Line 120 says "providing custom tables is not
  implemented yet (as for P2Rank 2.4.2)" while the very next bullet describes
  the `csv` feature that does this.

- **`feature-setup.md:125`** typo `{peorein_file_name}`.

- **`documentation/aa-mapping.md:21`** typo `ommited`, extra space before comma.

- **`documentation/readme.md`** index misses `cofactors.md`, `conservation.md`,
  `export-pocket-grid.md`, `export-pocket-descriptors.md`.

- **CI matrix is `17,21,25,26` only** (`.github/workflows/develop.yml:23`).
  README claims "Java 17 or later (tested up to Java 25)"; 18–20/22/23/24 not
  exercised; "tested up to Java 25" lags the now-present 26.

- **Distribution switched temurin → oracle** (`develop.yml:31`, commit
  `1997ab94`). No doc note explains the choice.

- **`PocketDescriptor.java:29-31` "fits in i32" contract** is unenforced;
  a descriptor producing `1e20` for an INT column silently emits
  `Integer.MAX_VALUE` or wraps. Add `Math.toIntExact` at the writer.

- **`PointExportData.create()` doc says "(for predict mode)"**
  (`PointExportData.groovy:141`) but it's also used by `rescore`
  (`ModelBasedRescorer.groovy:97,168`).

---

## Stale comments / dead code

- **"Immutable calculator instance" comments in 6 energy feature files** after
  the lazy-init refactor: `MethylEnergyFeature.groovy:22`,
  `MethylEnergyCloud{,X,XFull,X2,X2Full}SF.groovy:27/30`.

- **`EnergyCalculatorConfig.roleRulesCSV` + `role-rules.csv` resource are
  wired but read nowhere.** `EnergyCalculatorConfig.groovy:28,40,143,162-164`.
  `AtomRole.classify` is hardcoded.

- **`PocketShapeFiller.java:24` and `Params.groovy:896-897`** use the term
  "surface atom"; actual inputs are SAS points (pre-SAS-revision wording).

- **`LoaderParams.groovy:60-66`** commented-out copy constructor;
  `:20-22` stale `TODO get rid of this global variable` on `ignoreLigandsSwitch`.

- **`FPocketLoader.groovy:149`**: `pocket.centroid = pocket.voronoiCenters.centerOfMass`
  is dead because the `getCentroid` override at line 42 always recomputes.

- **`FPocketLoader.groovy:155`** `// probably not needed` (years old);
  `:142` fpocket3 TODO.

- **`ConcavityLoader.groovy:74`** `// TODO XXX` is meaningless;
  `:91` `///X correctsorting…` typo log marker; the class is not `@CompileStatic`.

- **`PUResNetLoader.groovy:55`** truncated comment mid-sentence (`// not all
  of them are necessarily on the surface but it s`).
  `:35` parameter named `liganatedProtein` but expects queryProtein.
  `:23-28` javadoc is a stub.

- **`PocketeerLoaderTest`** missing `predictionIsBoundToQueryProtein` and
  empty-input tests (every other new loader test has both).

- **`PocketGridBuilder.java:97-99`** "monomorphic dispatch, JIT-friendly"
  comment is wrong — bimorphic with two impls registered.

- **`PredictionPair.groovy:61,78,107,109`** still uses `criterium` after
  class rename (commit `2de315e9`).

- **`Evaluation.groovy:484`** rank-sentinel comment says 0; actual sentinel
  is `-1` (`PredictionPair.rankOfIdentifiedPocket`).

- **`Evaluation.groovy:132`** comment "Clear SAS points cache" — actually
  clears per-site lazy lookup, not the EvalContext cache.

- **`MethylEnergyFeature.groovy:67-70`** dangling try/catch skeleton;
  `:55` commented alternative path.

- **`prank.sh:13-15`** commented `-XX:+UseConcMarkSweepGC` (removed in JDK 14).

- **`misc/development-notes.md`** is down to a single 6-line note —
  merge into `misc/dev/technical-debt.md` or delete.

- **`distro/config/default_rescore.groovy:46`** `//== FAETURES` typo.

- **`distro/prank.bat:14`** last `set "JAVA_OPTS=%JAVA_OPTS%"` is a no-op.

- **`PocketGridChimeraXRenderer.groovy:81`** says PyMOL transparency is 0.7;
  actual `PROTEIN_TRANSPARENCY` in `PocketGridPymolRenderer.groovy:76` is 0.5.

- **`PocketGridBuilder.java:54-56`** meta-commentary about
  `Atoms.union`-vs-`Atoms.join` choice — reads as a leftover review note.

- **`VolsiteGridPointDescriptorTest.groovy:81`** comment claims testing the
  default `pocket_grid_volsite_radius=4.0`, but the test pins `RADIUS=4.0`
  via `@BeforeEach`.

- **`PocketDescriptorsTest.groovy:110-112`** arithmetic in the comment
  (`≈ sqrt(2)*5 ≈ 7.07`) doesn't match the geometry (centroid at (4.5,4.5,0),
  max dist ≈ 6.36). Asserted ratio still passes.

- **`AbstractScalarPocketDescriptor.java:21-23`** comment says "both shipped
  descriptors are multi-column" — accurate today, will silently lie when a
  scalar grid-point descriptor is added.

---

## Test-isolation gaps

- **`CofactorIntegrationTest.groovy:40-43`** mutates
  `LoaderParams.ignoreLigandsSwitch` in `@BeforeAll` without
  `@Isolated`/`@ResourceLock("Params")` and without saving/restoring. Compare
  `CofactorPipelineTest.groovy:55-65` and `CofactorAnalyzeTest.groovy:32-44`
  which do both correctly.

- **`LigandMatchingTest`** snapshots `Params.INSTANCE` but is not
  `@Isolated`/`@ResourceLock`-annotated.

- **`VolsiteGridPointDescriptorTest` / `VolsiteSmoothGridPointDescriptorTest`**
  mutate `Params.INSTANCE` in `@BeforeEach/@AfterEach` without the project's
  standard `@Isolated`/`@ResourceLock("Params")` annotations.

- **`PocketDescriptorRegistry` / `PocketGridPointDescriptorRegistry`** —
  `NamedRegistryHelper`-backed `LinkedHashMap` is not synchronized. The
  public `register/unregister` is exposed for tests but lacks a
  thread-safety note. If a future `register` happens during a feature
  extractor read, behavior is undefined.

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
