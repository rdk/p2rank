# Tech Debt Backlog

Active punch-list of small bugs, inconsistencies, and follow-up items. New
entries get added as they're found; resolved entries are removed.

Companion to [`technical-debt.md`](technical-debt.md), which holds long-form
analyses (issue + workaround + proper fix + trigger) for items that need more
than a one-liner.

Originated from a 10-agent post-2.5.1 audit but has been continuously
maintained since; the file is the live backlog, not an audit archive.
Expanded 2026-06 from a 25-agent audit of `2.5.1..HEAD` (v2.6-alpha.5); that
audit's HIGH findings (AmberCharges united amide-H, crpos modified-residue NPE)
and several MEDIUM crashes (analyze binding-sites center, energy empty-cloud
fallback length, AHoJ-UBS malformed-token abort) are already fixed on `develop`.

File paths are repo-relative; line numbers may drift as the surrounding files
evolve. Items marked **Not wanted** are explicit decisions to keep current
behaviour — kept in the file so they don't get re-raised.

---

## Real bugs (conditional / non-default paths)

- **Coulomb plumbing is dead code.** `EnergyCalculator.getAtomCharge` always
  returns 0 (`EnergyCalculator.groovy:351-357`). `enableCoulomb`,
  `dielectricConstant`, `coulombConstant` are structurally inert.
  `testCationProbeIncludesBothLJAndCoulombTerms`
  (`EnergyCalculatorTest.groovy:194-209`) is a false positive against its own
  name. Either wire charges, or rip the Coulomb path out.

- **Aromatic-probe energy cap is applied before the cosine switch.**
  `EnergyCalculator.groovy:248-250` clips pre-switch, then multiplies by
  `neighbor.switchValue` at line 291. May be intended, but contradicts inline
  doc and `testAromaticRingEnergyCap` (`EnergyCalculatorTest.groovy:132-146`)
  doesn't exercise the divergence. Decide per-atom vs per-point capping and
  align doc + test.

- **Pocketeer uses the server-supplied centroid; all other loaders derive
  geometric.** `PocketeerLoader.groovy:67-72` reads
  `pocketMap.get('centroid')` from the upstream JSON, while FPocket / Concavity
  / PUResNet / Seq2Pocket / SwinSite all converge on a geometric centroid
  (FPocket via `voronoiCenters.centerOfMass` where the points are all-Carbon ≡
  geometric; ConcavityLoader sets every grid atom's element to C before the
  centroid call; `Atoms.getCentroid()` itself is unweighted). The Pocketeer
  upstream centroid may be mass-weighted or otherwise differently defined.
  Either document the contract or normalize Pocketeer to geometric.

- **`Sutils.parseList` not bracket-aware** — see the long-form entry in
  `misc/dev/technical-debt.md`. Mitigated by `CofactorHandler` defensive
  recovery; trigger to fix: a third bracketed list param appears.

- **`pred_min_pocket_probability` with no `probatp_transformer` drops every
  pocket.** `Prediction.groovy:100-104`: with no transformer all pockets keep
  the default `auxInfo.probaTP=0`, so any positive threshold filters them all
  and the prediction is silently empty.

- **`PLBIndexRescorer` yields NaN for single-pocket predictions.**
  `PLBIndexRescorer.groovy:59-70`: M=1 gives sig=0 so ZPLB=0/0=NaN ->
  `newScore=NaN`, leaving the downstream sort order undefined.

- **`bench_skip_rescoring` leaves `pocket.sasPoints` null -> NPE.**
  `ModelBasedRescorer.groovy:165-174` returns early; if
  `eval_output_prediction_files` is also on, `PredictionSummary.toCSV`
  (`PredictionSummary.groovy:62`) dereferences the null.

- **`AtomKdTreeV2.findNearestNDifferentAtoms(sorted=true)` throws.**
  `AtomKdTreeV2.groovy:72-79`: `removeIf` on the immutable `List.of(...)`
  returned by `KdTree3D.findNearestN(sorted=true)` -> UnsupportedOperationException.
  Only the `sorted=true` path is affected.

- **`GetcleftOutputCalculator` NPEs when a pocket has no SAS points.**
  `GetcleftOutputCalculator.groovy:38-48`: `pocket.sasPoints` (nullable for
  non-PrankPocket types) is dereferenced without a guard. (likely)

- **`RescorePocketsRoutine` grid export uses `item.protein`.**
  `RescorePocketsRoutine.groovy:133`: with `cache_datasets=false` (the rescore
  default) this re-parses the structure and exports from the un-rescored pair;
  the predict path was fixed to use `pair.protein`.

- **`DataTable.formatSummaryTable` does not filter NaN.** `DataTable.groovy:147-201`:
  a single NaN in a column corrupts the overall min/max/avg/sum/median; the
  grouped variant (`:279`) filters NaN, so the two tables disagree.

- **`TableExporter.formatDouble` emits scientific notation for `|d|>9.2e8`.**
  `TableExporter.groovy:380-396`: falls back to `Double.toString` (`1e9 ->
  "1.0E9"`), violating the documented legacy `"0.#######"` plain-decimal CSV
  contract.

- **`PocketChargeStats` does not null-guard `pocket.surfaceAtoms`.**
  `PocketChargeStats.java:16-25`: the net-charge and polarity descriptors NPE on
  a null `surfaceAtoms`; the sibling dipole descriptor guards it.

- **`export_points_format` not validated at startup.** `Main.groovy:209-218`:
  unlike `pocket_grid_format`, an unknown value silently produces a CSV-content
  file with the wrong extension.

- **`SITE_UNREACHABLE` not divided by runs in multi-seed aggregation.**
  `EvalResults.groovy:191-199`: it is an absolute cumulative count, so
  aggregating a seed loop via `addSubResults` inflates it by x(runs).

---

## NaN / divide-by-zero / empty-input (introduced since 2.5.1)

- **`ConservationScore.calculateZScores` throws on an empty score map.**
  `ConservationScore.groovy:134-145`: `new StatSample([])` trips a Groovy
  power-assert (active regardless of `-ea`) which propagates into features.

- **`StatSample.getVariance` off-by-one: `size==1` yields NaN.**
  `StatSample.groovy:49-57`: the added `size<1` guard is dead (the ctor asserts
  non-empty); the real divide-by-zero is `xx/(size-1)` at `size==1`.

- **`HybridizationFeature` Tier-1 lookup NPEs on `hyb_sp2` present but
  `hyb_sp3` null.** `HybridizationFeature.groovy:78-82`: unboxing null into a
  `double[]`. Latent on table data. (WIP-light area)

- **`AnmModel` divides by sqrt of a possibly-negative eigenvalue.**
  `AnmModel.groovy:166-196`: EJML noise on the 3N×3N Hessian can leave a
  negative non-zero eigenvalue in the kept modes -> NaN. (physics, WIP-light)

- **energy2/energy3 `nearestPointEnergy` not NaN-guarded.**
  `AbstractProbeEnergyFeature.groovy:144,149-159`: the guard added to the
  single-probe path was not applied to the 9-dim or energy3 direct outputs.
  (energy, WIP-light)

- **`EnergyCalculator` HB override uses Groovy `?:` truthiness.**
  `EnergyCalculator.groovy:376-378`: an HB override row with `r0` or `epsilon`
  exactly `0.0` is silently ignored, falling back to the probe default.
  (energy, WIP-light)

---

## Error surfacing / swallowed failures

- **`eval-rescore` discards the dataset Result.** `Main.groovy:536-549` never
  calls `finalizeDatasetResult`, so per-item failures produce no error CSV and
  the run exits 0.

- **`classifier_train_stats` silently returns empty stats.**
  `TrainEvalRoutine.groovy:105-125`: the body is commented out and now only logs
  "not implemented"; 2.5.1 computed real per-instance train metrics.

- **`Parallel.eachParallel`/`collectParallel` swallow all but the first task
  exception.** `Parallel.groovy:32-38,58-66`: only the first failing `f.get()`
  propagates; exceptions from other completed tasks are lost.

- **`PocketPredictor.pocketScore` broad try/catch swallows all scoring
  errors.** `PocketPredictor.groovy:49-85`: any failure in point scoring /
  sorting / score-limit handling yields a silent `score=0` with a misleading
  "conservation" warning.

- **`PreloadConservationRoutine` leaks its thread pool on `fail_fast`.**
  `PreloadConservationRoutine.groovy:82-86`: `future.get()` rethrows before
  `executor.shutdownNow()`. Also `collectChainTasks` (`:115-136`) discards the
  `processItems` Result, ignoring protein-load failures.

- **Fetched conservation cached via `Futils.writeFile`, which swallows write
  errors.** `ConservationLoader.groovy:165-170` (+ `Futils.groovy:403-420`): a
  partial/empty cache file is later returned as valid.

---

## External-data loader robustness

- **`SwinSiteLoader`/`PUResNetLoader` NPE on a missing prediction directory.**
  `SwinSiteLoader.groovy:69-72` (+ PUResNet): `Futils.listFiles` ->
  `File.listFiles()` returns null for a non-existent / non-directory path.

- **`ConcavityLoader` derefs `gridPoints.first()` before its empty-guard.**
  `ConcavityLoader.groovy:60-67`: NoSuchElementException on an empty group; the
  `if (gridPoints.empty)` guard just below is dead.

- **`Seq2PocketLoader` aborts the whole load on a bad score/serial field.**
  `Seq2PocketLoader.groovy:94-103`: `Double.parseDouble` / `Integer.parseInt`
  are uncaught, while short/malformed lines are skipped (uneven handling).

- **`PocketeerLoader` assumes fully-formed JSON.** `PocketeerLoader.groovy:60-95`:
  missing fields / short arrays NPE or IndexOutOfBounds; the sibling loaders
  validate shape.

- **`AhojUbsSiteParser` throws on blank/non-numeric coordinates.**
  `AhojUbsSiteParser.groovy:49-74`: only `chain_resi` blankness is checked;
  `center_x/y/z` are parsed unconditionally, and the "skipped empty" warning
  overstates what is validated.

- **Rewritten coordinate loaders ignore `GeometricTransformation`.**
  `ConcavityLoader.groovy:39-86` (+ SwinSite, Pocketeer): FPocket applies
  `transformation.applyToAtoms` to its grid groups; these do not, so pockets are
  mis-registered on transformed inputs.

- **`FPocketLoader` non-contiguous pocket numbering can insert null.**
  `FPocketLoader.groovy:118-122`: `for (i=1..size) res.add(groups.get(i))`
  assumes contiguous keys 1..N; a gap inserts a null, NPE'd by the new
  empty-guard. (likely)

---

## Inconsistencies / parity gaps

- **Alternate-conformation chain reducer: full-benchmark validation pending.**
  `AlternateChainReducer` (default on via `reduce_alternate_conformation_chains`)
  collapses microheterogeneity-as-chains (6een); verified by unit tests + the 3
  PDB-10k structures it affects (only 6een materially). Still TODO: a holo4k /
  chen11 `eval-predict` on/off run to confirm aggregate DCA/DCC don't move
  (expected flat: ~3/10000 structures affected). Detection thresholds
  (`MIN_ALTLOC_FRACTION`, `OVERLAP_DISTANCE`, `MIN_OVERLAP_FRACTION`) are class
  constants, not params - promote if tuning is ever needed. Note: the reducer
  does NOT touch the residual trivial `distinct`-vs-`faster+sparsify` divergence
  on 1xjz/8fz5 (1 extra low-rank pocket each) - that is the inherent distinct
  superset behaviour, already documented on `surface_strategy`.

- **`NewPymolRenderer` class name is stale** — `NewPymolRenderer.groovy:28`.
  Two distinct active classes (`NewPymolRenderer` vs `PymolRenderer`). The
  "New" prefix predates a refactor. The cofactor block depends on a static
  method on the misnamed class (`PymolRenderer.groovy:147`). Rename.

- **Sort-direction comment in `PrincipalMomentsDescriptor.java:96-101`** says
  "Sort descending." while `Arrays.sort` is ascending. Downstream indexing
  compensates. **Not wanted** — current behaviour is
  correct, the comment ambiguity is acceptable.

- **`atomRoleCache`/`atomChargeCache` keyed by BioJava `Atom` identity.**
  `EnergyCalculator.groovy:131-132`. Atoms across proteins are distinct
  identities; cache never hits across proteins and grows monotonically — slow
  leak for long-running calculators.

- **`computeEnergyForPoint` returns `List<Double>` (boxed).**
  `EnergyCalculator.groovy:146-179`. Boxing per neighbor per probe in the hot
  loop. Legacy `LJEnergyCalculator.computeEnergyForPoint` returns `double`.

- **PyMOL pocket-grid renderer iterates `1..maxRank`; ChimeraX iterates
  `perPocketBasenames.keySet()`.** `PocketGridPymolRenderer.groovy:167,201,242`.
  Cosmetic-only: P2Rank ranks pockets contiguously, and the sidecar PDB strips
  empty-BitSet ranks. PyMOL therefore emits empty
  `pocket_grid_N`/`pocket_vol_N`/`pocket_gauss_N`/`pocket_hull_N` objects when
  the assigner produced no points for a small pocket — invisible but clutters
  the Models panel. Mirror the ChimeraX pattern for parity; not a correctness
  fix.

- **`EnergyCalculator.atomDataCache` keyed by `Atom.PDBserial`.**
  `EnergyCalculator.groovy:106-144`: serial==0 is skipped, but two distinct
  heavy atoms sharing a non-zero PDBserial would return wrong cached params
  (sigma/epsilon/charge/role). (likely; WIP-light area; see also the thread-safety
  note under Concurrency)

- **`AtomKdTreeV2` vs `AtomKdTreeV1` `findNearestNDifferentAtoms` diverge.**
  `AtomKdTreeV2.groovy:72-79`: V1 queries `count` and removes the identity-equal
  atom with `==` (can return count-1); V2 queries `count+1` and removes self
  differently. Result count and equality test differ.

- **`KdTree3D` sorted k-NN tie ordering differs from the old impl.**
  `KdTree3D.java:743-766`: `PyramidFeature` reads `findNearestNAtoms(...,9,true)`
  positionally, so among equal-distance atoms the heap-sort order (and thus the
  feature values) can drift vs the previous implementation.

- **`GridGenerator` iterator yields `ny*nz` points when `nx==0`.**
  `GridGenerator.java:84-123,222-243`: `hasNext()` tests only `z<nz`, so a box
  thinner than `edge` along x still walks all cells at `originX` while
  `getCount()=nx*ny*nz=0` disagrees.

- **`ChimeraXRenderer` ignores `vis_point_gradient_pymol` /
  `vis_point_gradient_max`.** `ChimeraXRenderer.groovy:54-55` hardcodes
  `palette lime:red range 0,0.7`; both PyMOL renderers honor the params, so the
  ChimeraX output diverges under a non-default gradient.

- **`NewPymolRenderer` fragile CIF detection via `.contains(".cif")`.**
  `NewPymolRenderer.groovy:63-72`: trips on a `.cif` substring in a directory
  component (e.g. `/data/v1.cif_set/prot.pdb`).

- **`PredictionVisualizer` pocket-mode lacks the CIF -> PDB conversion residue
  mode got.** `PredictionVisualizer.groovy:76-107`: predict/rescore pocket mode
  only copies the raw input, while PyMOL cannot reliably parse BioJava CIF.

- **`FeatureSetup` filter accounting over-reports when filtering is active.**
  `FeatureSetup.groovy:90-108`: `getFilterableSubFeatureNames` /
  `getFixedSubFeatureNames` list all sub-features of surviving features, not the
  subset kept by `feature_filters`.

- **`FeatureSetup.applyFilters` mutates the shared `Params.feature_filters`.**
  `FeatureSetup.groovy:262-274`: `featureFilters.add(0, "*")` on the list passed
  straight from `params.feature_filters` when the first filter starts with `-`.

- **`ModelConverter` float-native flatten targets lack the availability
  pre-flight guard.** `ModelConverter.groovy:67-75`: only `NativePanamaForest`
  and `NativePanamaForestAvx2` are platform-checked; the `Float` variants are
  not, so an unavailable target fails late instead of with a clear message.

- **`TransformRoutine` compare-flatten-eval drops a single
  `-rf_flatten_target` override.** `TransformRoutine.groovy:285-287`: treated as
  a list only when it contains a comma; a single non-comma value falls through to
  the defaults.

- **`AminoAcidMapper.parseCsv` silently accepts a trailing-comma line.**
  `AminoAcidMapper.groovy:165-170`: `line.split(",")` drops trailing empties, so
  `LLP,LYS,` parses as a valid 2-column `LLP -> LYS`.

- **Explicit ligand definition matching only a cofactor group fails with a
  misleading "matches no ligands".** `Ligands.groovy:95-108`: when a cofactor is
  configured for the same group, `loadForProtein` pre-filters cofactors out
  before split, so the ligand definition never matches.

- **`mapWithIndex` helper misused for a name->Feature map.**
  `FeatureSetup.groovy:211-218`: `setSubFeatureOffsets` uses it purely to key by
  name; "index" is irrelevant. Readability nit. (refactor)

- **Machine-readable output formatting relies on a global locale pin.** CSV /
  printf number formatting is split between explicit `String.format(Locale.ROOT,
  ...)` (newer: `PocketGridPymolRenderer`, `PocketGridChimeraXRenderer`,
  `Benchmarks`) and bare default-locale formatting that only produces `.`
  decimals because `Locale.setDefault(en_US)` is pinned at the entry points
  (`Main.main:725`, and now `DafaultPrankPredictor` ctor after the cs_CZ
  `_residues.csv` bug: `%.4f` -> `0,5000` split the CSV into 10 cols not 7).
  `PredictionOutputPinningTest` now forces `cs_CZ` so a lost pin fails on every
  machine + CI. Remaining hardening, in order of leverage:
  (A) run the suite under a hostile comma-decimal locale in CI -- forward
  `user.language`/`user.country` into the test fork in `build.gradle` and add a
  matrix axis (`develop.yml` currently runs `gradlew build` under the runner's
  dot-locale, so it never exercises this); catches the whole class incl. future
  sites.
  (B) migrate the remaining bare-default output sites to `Locale.ROOT` so the
  global pin is a backstop, not load-bearing: `ResidueLabelings.groovy:143`
  (resolve its "centralize CSV formatting" TODO), `utils/csv/CSV.groovy:62`
  (`System.sprintf`), `PocketPredictor.groovy:140`, `PymolRenderer.groovy:199,213`,
  `ParamLooper.groovy:88`.
  (C) optional lint test: fail the build on new bare `sprintf(` /
  `String.format("` (no `Locale` arg) in output-writing code, with an allowlist.

---

## Doc / config drift

- **README badge stuck at 2.5.1.** `README.md:11` vs `build.gradle:25`
  (`2.6-alpha`). Kept until 2.6 leaves alpha. (The `./make-disro.sh` typo
  on `README.md:226` was fixed 2026-05-22.)

- **CI matrix is `17,21,25,26` only** (`.github/workflows/develop.yml:23`) and
  distribution switched temurin → oracle (commit `1997ab94`). **Not wanted**
  — Java-version coverage and CI distribution choice are
  intentional; README's "tested up to Java 25" wording will refresh at the
  2.6 release.

- **Em-dash (U+2014) in `breaking-changes.md:22,23,62`** violates the CLAUDE.md
  no-em-dash hard rule; the whole 2.6 section was added this cycle.

- **Em-dash (U+2014) in `documentation/export-pocket-grid.md:26,195`** -- same
  hard-rule violation in a new file.

- **`user-guide.md:1108`** frames `pred_point_threshold` default as 0.35; the
  `Params.groovy` baseline is 0.4 (alphafold/conservation predict configs also
  use 0.4, not just the rescore configs the footnote names).

- **`CurveMetrics` AUPRC javadoc is self-contradictory.** `CurveMetrics.java:12-15,80-129`:
  the class doc describes a stepwise (Davis & Goadrich) curve while the code is
  trapezoidal.

- **`SiteMetricsTest.groovy:173-178`** asserts `avgPointScore` is NaN for the
  wrong (stale) reason after the point-score-unify commit; the value is now
  computed for all site types.

- **`feature_filters` Javadoc examples stale.** `Params.groovy:149-177`: after
  the fixed-vs-filterable change, filters apply only to `extra_features`; the
  examples still imply they restrict `features`.

- **`cofactor_max_protein_dist` doc says "INFO warning"; code logs WARN.**
  `Params.groovy:618-621` (`CofactorHandler.warnDistantCofactors` uses
  `log.warn`; `documentation/cofactors.md` is correct).

- **Stale symbol `getEffectiveCofactorDefinitions`** (renamed
  `resolveCofactorDefinitions`) in `documentation/dev/cofactors.md:10,174`.

- **`aa_mapping` is feature-determining but annotated `@RuntimeParam`.**
  `Params.groovy:642-643`: it changes corrected residue codes feeding several
  table/charge features, so it should be `@ModelParam`.

- **Flatten params carry both `@RuntimeParam` and `@ModelParam`** (and one
  carries neither). `Params.groovy:461-508,671-672`: the annotations are
  documentation-only, so this is drift/intent risk, not a runtime bug.

- **Faithful float-split flatten variants are experimental and not yet in the
  re-flatten recommendation.** `FloatSplitSoaLegacyFlatBinaryForest`,
  `Int16LeafFloatSplitSoaLegacyFlatBinaryForest` (the fastest faithful measured,
  ~-14% vs `Int16LeafSoa`, ~-28% vs `LegacyFlat` on GraalVM), and
  `Int16LeafFloatSplitBranchlessSoaLegacyFlatBinaryForest` are selectable via
  `-rf_flatten_target` (FasterForest 2.13.0) and now listed in
  `Params.rf_flatten_target`, but are ranking-gated/experimental upstream.
  Revisit the recommended re-flatten target (and default-eligibility) when they
  graduate from experimental.

---

## Stale comments / dead code (open carry-forwards)

- **`EnergyCalculatorConfig.roleRulesCSV` + `role-rules.csv` resource are
  wired but read nowhere.** `EnergyCalculatorConfig.groovy:28,40,143,162-164`.
  `AtomRole.classify` is hardcoded. Field is now marked `// unused:`; resolve
  by either making `AtomRole` data-driven or deleting the plumbing.

- **`LoaderParams.groovy:20-22`** stale `TODO get rid of this global variable`
  on `ignoreLigandsSwitch`. Pre-existing; still legitimate.

- **`FPocketLoader.groovy:152`** dead `pocket.centroid` write (overridden by
  `getCentroid()`). Marked `// unused:`; kept until the override is removed.

- **`FPocketLoader.groovy:159`** `// probably not needed` (years old);
  `:142` fpocket3 TODO. Both pre-existing.

- **`ConcavityLoader.groovy`** not `@CompileStatic` — every other loader is.
  Adding it risks surfacing latent type errors; do under a separate change
  with a compile + test pass.

- **`misc/development-notes.md`** is down to a single 6-line note — kept
  intentionally.

- **`distro/prank.bat:14`** `set "JAVA_OPTS=%JAVA_OPTS%"` no-op. Kept
  intentionally.

- **`AbstractScalarPocketDescriptor.java:21-23`** comment says "both registered
  descriptors are multi-column" — accurate today, will silently lie when a
  scalar grid-point descriptor is added.

- **`SLinkClusterer` (V1) is now dead production code.** Only the parity oracle
  in tests references it; `AtomClusterer`/`AtomGroupClusterer` both wire
  `SLinkClustererV2`. Keep as the V2 parity oracle or delete.

- **`AAScore.chainId` is a dead field** (written, never read).
  `ConservationScore.groovy:71-83`: `loadScoreFile` gained a `chainId` param
  solely to populate it.

- **`PymolRenderer.colorExposedAtoms` is dead** (`PymolRenderer.groovy:132-136`):
  body fully commented out, returns `""`; the call site contributes nothing.

- **`cmdBindingSiteCenters` "Sites skipped" counter is never incremented.**
  `AnalyzeRoutine.cmdBindingSiteCenters` declares `totalSkippedSites` and emits
  it into the summary, but never increments it (always 0) for explicit-site
  datasets.

- **Misleading "same iteration order as V1" comment** in
  `SLinkClustererV2.java:56-66`: single-linkage output is connected-components of
  the threshold graph; the loop order is not load-bearing for matching V1.

- **`ZScoreSingleLinkageClustering.admitPoint` ignores `point.predicted`**
  (`:82-85`): a legitimate alternative strategy, but the test comment claiming
  non-predicted points are excluded is misleading.

---

## Concurrency / thread-safety (latent)

These do not bite today (shared state is not yet concurrently mutated), but are
landmines if usage changes; see also the Test-isolation gaps section.

- **`SLinkClustererV2` stores union-find state in instance fields.**
  `SLinkClustererV2.java:16-17,50-54`: `parent`/`rank` are instance arrays
  mutated by `cluster()`, so one shared instance across threads clobbers state
  silently. V1 used method-local arrays and was reentrant.

- **`EnergyCalculator.atomDataCache` is an unsynchronized `HashMap`.**
  `EnergyCalculator.groovy:106-144`: mutated by `getAtomData()`; its
  `ConcurrencyTest` bypasses the cache (serial==0 path), so it has zero coverage.

- **`Protein.surfaceCache` is a plain `HashMap` with unsynchronized lazy init.**
  `Protein.groovy:75,168-177`: safe only while one thread requests surfaces per
  Protein. (likely)

- **`ConservationProviderFactory` caches a JVM-lifetime singleton.**
  `ConservationProviderFactory.groovy:59-99`: built from `Params.INSTANCE` at
  first call; `reset()` is never invoked from main code, so it goes stale if the
  conservation params change mid-process (e.g. ploop).

- **`Parallel.groovy` creates a fresh `ForkJoinPool` per call.**
  `Parallel.groovy:27,53`: GPars reused a current pool; `new ForkJoinPool(...)`
  per call means nested `eachParallel`/`collectParallel` oversubscribe threads.
  (refactor/perf)

---

## Test-isolation gaps

JUnit5 parallel execution is not enabled in this project (no
`junit-platform.properties`, no `parallel.enabled=true`), so
`@Isolated`/`@ResourceLock` annotations are forward-compatibility
documentation only.

- **`PocketDescriptorRegistry` / `PocketGridPointDescriptorRegistry`** —
  `NamedRegistryHelper`-backed `LinkedHashMap` is not synchronized. Production
  paths only mutate at class init; tests use balanced
  `@BeforeAll`/`@AfterAll` or `try`/`finally`. Latent issue only — surfaces if
  JUnit parallel execution is ever enabled and two test classes
  register/unregister concurrently.

---

## Performance nits

- **`computeEnergyForPoint` boxes doubles** (above).

- **`EnergyCalculator.groovy:173`** `config.probeParams[probe]` `Map.get` per
  neighbor per probe; hoist to a pre-sized `ProbeParams[]` indexed by
  `probeIdx`.

- **`PocketGridBuilder.java:83`** `Map<Integer, BitSet> pocketToPointIndices`
  uses boxed `Integer` keys while the rest of the file goes to lengths to
  avoid boxing. **Not wanted** — traced ~200-400 box
  operations per protein at this site versus ~10⁵-10⁶ in the hot path; the
  perf gain is in the noise (~0.01%) and the API refactor across renderers
  + exporters + descriptors isn't justified.

- **`KdTreeAssigner.computeRawShell` dedup branch is dead**
  (`KdTreeAssigner.java:41`): every atom returned by the KD tree is in
  `latticeIndex` by construction.

- **`SLinkClustererV2` logs the full per-cluster size list at INFO** on every
  `cluster()` call (`SLinkClustererV2.java:77-78`): noisy on the SAS-point
  prediction path with many ligandable points. (likely)

---

## Build / repo hygiene

- **Stale vendored jars.** `lib/local-mvn-repo` still tracks FasterForest
  `2.11.0` (build pins `2.13.0`) and biojava `7.2.2-rdk.1` + `7.2.4-rdk.1`
  (build pins `7.2.5-rdk.1`). CLAUDE.md's keep-single-version policy: `git rm`
  the unused versions. (faster-molecular-surface is clean: only `1.8`.)

- **Committed tutorial `params.txt` leaks developer absolute paths.**
  `documentation/notebooks/.../predict_1fbl/params.txt:39,96,122`
  (`/mnt/ssd/prank/...` in `dataset_base_dir`/`installDir`/`output_base_dir`).

- **`distro/prank.bat:24,35,49`** version-detection hardcodes
  `%JAVA_HOME%\bin\java.exe`; with JAVA_HOME unset (java on PATH only) both
  probes fail silently and the new JVM flags are no-ops. (distinct from the
  `prank.bat:14` JAVA_OPTS no-op above)

- **`distro/prank_burst:125-131`** first-run AppCDS dump
  (`-XX:ArchiveClassesAtExit`) has a benign race for concurrent cold starts.
