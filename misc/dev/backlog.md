# Tech Debt Backlog

Active punch-list of small bugs, inconsistencies, and follow-up items. New
entries get added as they're found; resolved entries are removed.

Companion to [`technical-debt.md`](technical-debt.md), which holds long-form
analyses (issue + workaround + proper fix + trigger) for items that need more
than a one-liner.

Originated from a 10-agent post-2.5.1 audit but has been continuously
maintained since; the file is the live backlog, not an audit archive.

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

---

## Inconsistencies / parity gaps

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

- **`AbstractScalarPocketDescriptor.java:21-23`** comment says "both shipped
  descriptors are multi-column" — accurate today, will silently lie when a
  scalar grid-point descriptor is added.

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

