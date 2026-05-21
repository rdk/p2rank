# Cofactors as Protein Surface - Dev Notes

Engineering record for the `-cofactors` feature (GitHub Issue #79, Part 2).

For users: see [`../cofactors.md`](../cofactors.md).

## Status

- [x] Phase 1 - Params, CofactorHandler, LoaderParams (core infrastructure)
- [x] Phase 2 - Dataset wiring (`cofactors` column, `getEffectiveCofactorDefinitions`)
- [x] Phase 3 - Behaviour (Ligands guard + filter, Protein.loadStructure cofactor block)
- [x] Phase 4 - Validation, CsvFileFeature fix (R17), aa_mapping warning (R19)
- [x] Phase 5 - Tests (CofactorHandlerTest / CofactorIntegrationTest / CofactorPipelineTest)
- [x] Phase 6 - Manual smoke test + drop-in safety benchmark (R18)
- [x] Companion plan - `analyze cofactors` subcommand + PyMOL teal-stick highlight (`vis_highlight_cofactors`)
- [x] Post-merge audit pass (21 findings addressed) - see "Post-merge audit fixes" below

## Design Choices (R1–R24)

Each entry corresponds to a refinement during design. This table is the as-implemented record.

| ID  | Choice | As-implemented note |
|-----|--------|---------------------|
| R1  | `CofactorHandler` is single source of truth on `LoaderParams` | Implemented as planned. |
| R2  | Single-scan extraction via `ExtractionResult` | Implemented as planned. |
| R3  | `"ZZZZ"` is the never-present test specifier | Used in `missingCofactorDoesNotFail` test and in the benchmark script. |
| R4  | Unmatched specifiers logged at DEBUG | Implemented in `logResult`. |
| R5  | `Ligand.contactDistance` side-effect documented | Documented in user doc; behaviour inherited (no code change). |
| R6  | Altloc + modified-name handling: no special code | Inherited from BioJava defaults. |
| R7  | Per-feature atom-level behaviour table | Documented in user doc. |
| R8  | Original CSV "treated as 0.0" claim - superseded by R17 | R17 supersedes; this entry is historical. |
| R9  | 1-arg `Protein.load(String)` note for library users | Documented in user doc. |
| R10 | Cofactor > explicit ligand precedence | Implemented in `Ligands.isRelevantLigandGroup` (cofactor guard fires first). Plus the upstream filter at `Ligands.loadForProtein` - see "Deviation from plan" below. |
| R11 | Distant-cofactor WARN + `cofactor_max_protein_dist` param (default 15Å) | Implemented in `CofactorHandler.warnDistantCofactors`. |
| R12 | Pipeline tests on existing `1t7qa.pdb` (COA) | `CofactorPipelineTest` exists; tests pass. |
| R13 | `ExtractionResult` stored on `Protein.secondaryData` | `Protein.cofactorExtractionResult` accessor added. |
| R14 | Chain-reduction "lost cofactors" WARN | `warnChainExcludedCofactors` implemented; name-only matching (documented limitation). |
| R15 | `load_ligands_from_separate_files` interaction is cosmetic | Not exercised by tests; documented in user doc Known Limitations. |
| R16 | Reuse `Dataset.LigandDefinition` for specifier syntax + identity-based `isCofactor(Group)` | Implemented as planned. Identity set is `Collections.newSetFromMap(new IdentityHashMap<>())`. |
| R17 | `CsvFileFeature` cofactor bypass | Implemented as a 5-line guard at top of `calculateForAtom`. Uses `loaderParams?.isCofactor(group)`. |
| R18 | Drop-in safety benchmark | `benchmark/cofactors_dropin_safety.sh` written and run - see "Benchmark Results" below. |
| R19 | `aa_mapping`-collision WARN at startup | Implemented in `Main.initParams`. |
| R20 | `ConservationFeature` GroupType-guard fallback verified, regression test added | `ConservationScore.getScoreForResidueSafe` uses `?: 0d`; regression test `conservationFeatureSafeOnCofactorAtoms` injects a `ConservationScore` containing a "poison" value under the cofactor's residue number, then asserts the cofactor atom returns 0.0 (proving the AMINOACID type-guard short-circuited the lookup) while a polymer-atom control returns its real score. |
| R21 | **Structured `RenderingModel.cofactorResult` (Option A) instead of flat `cofactorResult` list** | Renderer receives the full `ExtractionResult`. `NewPymolRenderer.cofactorPymolBlock` emits one PyMOL selection per cofactor name (`cofactor_FAD`, `cofactor_PLP`, …) plus an aggregate `cofactor_atoms`. Per-name selections give users a PyMOL handle for toggling individual cofactors; unmatched specifiers go into a header comment as a diagnostic. `PymolRenderer.renderCofactors` delegates to the same shared block to keep the two renderers in sync. Tests: `pymolRendererEmitsPerNameSelections`, `unmatchedSpecifierAppearsInPymolComment`. |
| R22 | **Centralized cofactor specifier parsing** in `CofactorHandler.parseAndValidate` | Single entry point for both CLI list (`List<String>`) and dataset-column string (`String`). Joins list elements with `,`, re-splits with `Sutils.splitRespectInnerParentheses(...,',','[',']')` so a `contact_res_ids:A,B,C` specifier that was over-split upstream by naive comma-splitters is correctly reassembled. Case-normalizes group name (so `-cofactors fad` matches `FAD` groups instead of silently zero-matching). Wraps `LigandDefinition.parse` errors with cofactor-specific context so the message doesn't say "ligand definition in the dataset file" for a CLI source. Tests: `contactResIdsCommasArePreservedFromList`, `contactResIdsCommasArePreservedFromString`, `lowercaseGroupNameIsNormalized`, `mixedCaseGroupNameIsNormalized`, `specifierBodyPreservesCase`, `errorMessageMentionsCofactor`. |
| R23 | **Per-item resolution in `Dataset.resolveCofactorDefinitions(Item)`** | Single helper, package-visible. Used by both `getLoaderParams()` (so protein loading respects the per-row column) and `AnalyzeRoutine.cmdCofactors` (so analyze cofactors' dry-run mode reflects the same effective specifiers - column overrides global - that prediction would). |
| R24 | **CSV cell quoting in `DataTable.toCsv`** | RFC 4180 quoting: cells containing comma / double-quote / newline get wrapped in double-quotes with inner double-quotes doubled. Fixes the `cofactor_matches.csv` round-trip issue for specifiers like `FAD[contact_res_ids:A,B]`. Tests: `DataTableCsvTest`. |

## Post-merge audit fixes

After the initial feature was complete, a deep audit (5 parallel agents on independent angles)
surfaced 21 findings. They were addressed in three passes:

**Pass 1 - documentation/comment corrections**
1. `documentation/cofactors.md` Config File - removed the precise-specifier example (per user rule, that syntax isn't expected in config files).
2. `documentation/cofactors.md` Known Limitations - removed stale `ligands_separated_by_TER` bullet (symbol doesn't exist in code).
14. This file's `(R1–R20)` header now reads `(R1–R24)` (contents already covered R21-R24).
15. `documentation/cofactors.md` sample analyze output - aligned with actual code output format (added `- N groups total`, fixed "matched in" prefix, "← warning" → "← matched no structures").
16. `Main.groovy` comment - renamed reference `getEffectiveCofactorDefinitions` → `resolveCofactorDefinitions`.

**Pass 2 - code bug fixes**
3. `CofactorHandler.extractCofactorAtoms` now uses `Struct.getLigandGroups(protein)` instead of `getHetGroups(structure)`, aligning with `Ligands.loadForProtein`. Previously, GDP/GTP/ATP/SHR-style groups (BioJava-classified as `NUCLEOTIDE`/`AMINOACID`) silently failed to match as cofactors.
4. `CofactorHandler.extractCofactorAtoms` clears `matchedGroups` on each invocation - `Protein.transformedCopy` reuses the same handler with a deep-copied structure, so stale Group identity references must be discarded.
5. `DataTable.csvCell` now also quotes on `\r` (audit found gap in RFC-4180 compliance).
6. `NewPymolRenderer.cofactorPymolBlock` uses `set_color cofactor_col, [rgb]` (canonical PyMOL comma form) instead of `set_color cofactor_col = [rgb]`.
17. `CofactorHandler.parseOne` now `trim()`s the group-name prefix before upper-casing, so `"fad [group_id:A_500]"` (accidental space) matches `FAD` instead of silently never-matching as `"FAD "`.
18. `warnDistantCofactors` and `warnChainExcludedCofactors` log at `WARN` (not `INFO`) - these are advisory but warn-worthy conditions.

**Pass 3 - test hardening** (see `src/test/groovy/cz/siret/prank/test/Log4jCapture.groovy` for the log-capture helper)
7. `csvFeatureDoesNotThrowOnCofactorAtoms` now picks a real polymer atom, writes a CSV covering its serial only, and asserts the polymer control reads 0.5 (proves the lookup path is live) before asserting the cofactor atom returns 0.0 without throwing.
8. `conservationFeatureSafeOnCofactorAtoms` now injects a `ConservationScore` (via reflection on its package-private constructor) containing both a polymer score and a "poison" score keyed by the cofactor's residue number; asserts cofactor returns 0.0 (type-guard fired) and polymer returns its real score (lookup is live).
9. `distantCofactorWarningRespectsThreshold` uses `Log4jCapture` to assert the WARN is actually emitted at `maxDist=0.001` (previously only checked "doesn't throw").
10. `chainExcludedCofactorDiagnosticDoesNotThrow` now uses `@EnabledIf("has1AHP")` instead of silently returning - a missing test file no longer looks like a pass.
11. New `aaMappingCollisionWarningEmitted` test verifies R19 detection logic with `Log4jCapture`.
12. `benchmark/cofactors_dropin_safety.sh` now compares all `*.csv`, `*.pml`, `*.pdb`, and `*.cif` files (previously only `*_predictions.csv` and `*_residues.csv`); also runs with `-visualizations 1` so PyMOL output is included.
13. New `CofactorAnalyzeTest.groovy` covers the building blocks of `cmdCofactors` (per-item resolution precedence, bracket-aware specifier parsing). The full `cmdCofactors` invocation is exercised end-to-end via `misc/test-scripts/testsets.sh`.
19. New `concurrencySafeWithMultipleThreads` test loads the same structure on 8 parallel threads with independent `LoaderParams` and asserts identical cofactor atom counts.
20. `DataTableCsvTest` gained 4 cases: newline-in-value, CR-in-value, null cell renders as empty, pre-quoted value gets escaped.
21. `pymolRendererEmitsPerNameSelections` now regex-validates each `select X, BODY` line - empty bodies and malformed selections would now fail the test.

## Deviation from original design

**Cofactors are now filtered out at `Ligands.loadForProtein` (before `splitByPredicate`),
not only inside `Ligands.isRelevantLigandGroup`.**

The original design called for a single guard at the top of `isRelevantLigandGroup`. That guard alone
sends cofactors into `ignoredLigands` (because `splitByPredicate(ligandGroups, predicate)`
puts predicate-false items in the "negative" bucket, which is then materialised as
ignored ligands). The user-facing contract is "cofactors do not appear in any
`*_predictions.csv` or `*_residues.csv` ligand listings" - including the ignored-ligands
listing. So we added an upstream filter:

```groovy
// Ligands.loadForProtein:
List<Group> ligandGroups = Struct.getLigandGroups(protein)
        .findAll { !loaderParams.isCofactor(it) }
```

The `isRelevantLigandGroup` guard is retained as defence-in-depth for direct callers.

## Benchmark Results

### Drop-in safety (R18, part a) - byte-equality on never-present specifier

```
Date:     2026-05-13 (re-run after audit fixes A–G applied)
Dataset:  distro/test_data/concavity.ds (~22 structures)
Seed:     42
Threads:  1
Command:  ./benchmark/cofactors_dropin_safety.sh
Result:   PASS - predictions byte-identical between baseline and with -cofactors ZZZZ.
```

Only `params.txt` and `run.log` differ (legitimate noise: `cofactors = []` vs
`cofactors = [ZZZZ]`, plus outdir paths and timestamps). The script filters those.

### Effect benchmark (R18, part b) - informational, with a present HETATM

Not run yet. Recommended next step: pick a dataset where many structures contain a
real cofactor (e.g. `MG` or a flavoenzyme set) and capture DCA top-1/top-3/top-5 deltas.
Use the result to decide whether Mitigation A or B (see "Planned Future Improvements" below) is justified.

### Manual smoke test

```
Command:  ./distro/prank predict -f distro/test_data/1AHP.pdb -cofactors PLP
Result:
  [INFO] Cofactors to include as protein surface: [PLP]
  [INFO] Structure 1AHP.pdb: included 1 cofactor type(s) as protein surface (PLP: 30 atoms (2 instances))
  [INFO] protein   atoms: 12502   (vs 12472 baseline - exactly +30, 2 × 15 heavy atoms)
  grep -c PLP 1AHP.pdb_predictions.csv → 0   (cofactor not in pocket prediction)
```

## Known Limitations Shipped

1. `load_ligands_from_separate_files = true` + cofactor in primary file - cofactor still
   on surface but appears in `ignoredLigands` listing (cosmetic, R15). Rare combination;
   not exercised by tests.
2. Chain-reduction "lost cofactors" diagnostic uses name-only matching - may over-report
   when a precise specifier wouldn't have matched anyway (R14). Documented in user doc.
3. Case-sensitive group-name matching - by design, matches `ligands`-column behaviour.
   PDB/CIF files use uppercase residue names.
4. AA-property feature dilution at SAS points near cofactors (R18). Drop-in safety
   confirmed; effect-benchmark deferred. Mitigations A/B sketched under
   "Planned Future Improvements" below.

## Planned Future Improvements

Ordered by likely user value. Each has a trigger that would justify the work.

1. ~~**`analyze cofactors`** subcommand~~ - **Shipped.** See `cmdCofactors` in `AnalyzeRoutine.groovy`.
   Outputs: `het_groups.csv`, `het_groups_summary.txt`; with `-cofactors` also `cofactor_matches.csv`.
2. ~~**PyMOL teal-stick highlight**~~ - **Shipped.** `vis_highlight_cofactors` parameter (default `true`).
   New field `RenderingModel.cofactorResult`; `renderCofactors()` in both `NewPymolRenderer` and `PymolRenderer`.
   Five call sites updated to pass `cofactorResult` from `protein.cofactorExtractionResult`.
3. **ChimeraX renderer support** - current cofactor highlight is PyMOL-only.
   *Trigger:* ChimeraX user community feedback.
4. **`analyze cofactor-pockets`** - per-dataset with/without prediction delta.
   *Trigger:* user request, or benchmarking the feature on >1 release.
5. **CSV column for cofactor metadata in prediction output** - would expose which atoms
   were on the surface in a structured form. *Trigger:* downstream-tool integration ask.
6. **Mitigation A / B** - only if R18 effect benchmark shows score regression.
7. **Per-cofactor weighting** - relative weight for cofactor atoms in feature aggregation.
   *Trigger:* if dilution turns out to need a finer-grained knob than mitigation A.
8. **fpocket-rescore cofactor support** - fpocket has a hard-coded cofactor list. Plumbing
   this through would need either patching fpocket or pre-processing the input.
   *Trigger:* explicit user request for cofactor-aware rescoring.

## File Map

Production code (modified):
```
src/main/groovy/cz/siret/prank/program/params/Params.groovy                       - +cofactors, +cofactor_max_protein_dist, +vis_highlight_cofactors
src/main/groovy/cz/siret/prank/domain/Dataset.groovy                              - +COLUMN_COFACTORS, +getEffectiveCofactorDefinitions, getLoaderParams
src/main/groovy/cz/siret/prank/domain/loaders/LoaderParams.groovy                 - +cofactorHandler, +isCofactor(Group)
src/main/groovy/cz/siret/prank/domain/Protein.groovy                              - cofactor block in loadStructure, +getCofactorExtractionResult
src/main/groovy/cz/siret/prank/domain/Ligands.groovy                              - cofactor filter in loadForProtein, guard in isRelevantLigandGroup
src/main/groovy/cz/siret/prank/program/Main.groovy                                - parseAndValidate + aa_mapping collision WARN
src/main/groovy/cz/siret/prank/features/implementation/csv/CsvFileFeature.groovy  - R17 cofactor bypass
src/main/groovy/cz/siret/prank/program/visualization/RenderingModel.groovy        - +cofactorResult field
src/main/groovy/cz/siret/prank/program/visualization/renderers/NewPymolRenderer.groovy  - +renderCofactors()
src/main/groovy/cz/siret/prank/program/visualization/renderers/PymolRenderer.groovy     - +renderCofactors()
src/main/groovy/cz/siret/prank/program/routines/analyze/AnalyzeRoutine.groovy           - +cmdCofactors() + 4 caller-site wirings of cofactorResult (3 existing routines + cmdCofactors itself)
src/main/groovy/cz/siret/prank/program/routines/analyze/DataTable.groovy                - RFC-4180 quoting in toCsv (audit #6)
src/main/groovy/cz/siret/prank/program/routines/traineval/EvalResiduesRoutine.groovy    - caller-site wiring of cofactorResult
```

Production code (new):
```
src/main/groovy/cz/siret/prank/domain/CofactorHandler.groovy          - handler + ExtractionResult + parseAndValidate
```

Tests (new):
```
src/test/groovy/cz/siret/prank/domain/CofactorHandlerTest.groovy                       - parser, case norm, bracket-aware split, error wrapping
src/test/groovy/cz/siret/prank/domain/CofactorIntegrationTest.groovy                   - 1AHP integration (PLP)
src/test/groovy/cz/siret/prank/domain/CofactorPipelineTest.groovy                      - 1t7qa pipeline + R17/R20 regressions + Option-A PML test + audit-driven cases + R19 collision + concurrency
src/test/groovy/cz/siret/prank/program/routines/analyze/DataTableCsvTest.groovy        - RFC-4180 CSV quoting (audit #6)
src/test/groovy/cz/siret/prank/program/routines/analyze/CofactorAnalyzeTest.groovy     - building blocks of cmdCofactors (per-item resolution, bracket-aware parsing)
src/test/groovy/cz/siret/prank/test/Log4jCapture.groovy                                - test-only log4j2 capture helper
```

Test data (downloaded):
```
distro/test_data/1AHP.pdb           - gated by @EnabledIf("has1AHP"); 1AHP contains PLP
```

Scripts (new):
```
benchmark/cofactors_dropin_safety.sh   - R18 drop-in safety benchmark
```

Docs (new):
```
documentation/cofactors.md            - user-facing
documentation/dev/cofactors.md        - this file
```
