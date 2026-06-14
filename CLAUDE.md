# P2Rank — Repo Notes

## Sister repos

- **FasterMolecularSurface** is a permanent sister repo, always cloned next to
  this one at `../FasterMolecularSurface`. That single local checkout tracks two
  GitHub remotes: `origin` is the private repo
  (https://github.com/rdk/FasterMolecularSurface-private) and `public` is the
  public mirror (https://github.com/rdk/FasterMolecularSurface). It is the
  source of the `cz.cuni.cusbg:faster-molecular-surface` Maven dependency (see
  `build.gradle`); built jars are vendored here under
  `lib/local-mvn-repo/cz/cuni/cusbg/faster-molecular-surface/`. Only the single
  consumed version is kept (like the FasterForest dep below): on a bump, vendor
  the new `<ver>/faster-molecular-surface-<ver>.{jar,pom}`, update the version in
  `build.gradle`, and `git rm` the old version dir. P2Rank-side wrappers live in
  `src/main/groovy/cz/siret/prank/geom/` (`SurfaceStrategy`, `cdksurface/`).

- **FasterForest-private** is a permanent sister repo, always cloned next to
  this one at `../FasterForest-private`
  (https://github.com/rdk/FasterForest-private). It is the source of the random
  forest dependency `cz.siret.prank:FasterForest` (see `build.gradle`); built
  jars are vendored here under
  `lib/local-mvn-repo/cz/siret/prank/FasterForest/<version>/` (single version
  kept, replaced on each bump). To bump: run `./gradlew clean assemble` in the
  sister repo (native libs are pre-committed, so a Java-only build is enough),
  copy the resulting `build/libs/FasterForest-<ver>.{jar,pom}` into the vendored
  dir, update the version in `build.gradle`, remove the old version dir, then
  `./gradlew test`.

- **p2rank-dev-artefacts** is a permanent sister dev repo, always cloned next to
  this one at `../p2rank-dev-artefacts`
  (https://github.com/rdk/p2rank-dev-artefacts, private). It holds large dev
  artefacts kept out of the main repo: trained models under
  `models/<version>/` (binaries via **Git LFS**) plus their predict configs, and
  a `papers/` subdir. Predict configs there should use **relative-sibling**
  `model =` paths (`../p2rank-dev-artefacts/models/...`), not machine-absolute
  ones, so they stay portable across checkouts. Caveat: P2Rank does *not*
  resolve `model` relative to the config file (only `dataset_base_dir` /
  `output_base_dir` get that treatment); a relative `model` is tried against the
  **current working dir** first, then `$installDir/models/`. So these configs
  work only when run with cwd at this repo's root (the install dir), e.g.
  `./prank.sh predict <in> -c ../p2rank-dev-artefacts/models/<config>` from here.
  Not a Maven dependency: nothing is vendored back here.

## Dev backlog / tech debt tracking

Known small bugs, inconsistencies, and follow-ups are tracked in-repo, not just
in issues:

- `misc/dev/backlog.md`: the live punch-list (one-liner entries grouped by
  category). Check it before re-raising an issue: items marked **Not wanted**
  are deliberate keep-as-is decisions. Add newly found items here (deduped
  against existing entries), and remove entries once resolved.
- `misc/dev/technical-debt.md`: long-form companion (issue + why + workaround +
  proper fix + trigger) for items that need more than a one-liner.
- `misc/todo/pocket_grid/FOLLOWUP.md`: pocket-grid-specific future ideas and
  perf notes.

## Build artifacts (do not edit)

- `distro/README.md` is **generated** from the top-level `README.md` by the
  `copyDocumentation` task in `build.gradle` on every `./gradlew assemble`.
  It is gitignored. Edit the top-level `README.md` only; never edit
  `distro/README.md` directly — any changes are silently overwritten on the
  next build.

## Groovy gotchas

- **`BitSet.and()` / `.or()` / `.andNot()` do NOT mutate in Groovy.** Under
  `@CompileStatic`, `bitset.and(other)` binds to Groovy's
  `DefaultGroovyMethods.and(BitSet, BitSet)`, which *returns* a new intersection
  and leaves the receiver unchanged (the Java in-place semantics are shadowed).
  Silent: no error, the result just looks like the receiver's own cardinality.
  Use the operators (`a & b`, `a | b`, `a & ~b`) and assign, or do BitSet
  set-algebra in Java. The grid engine (`PocketGrid*`, fillers) is Java for this
  reason; the trap bit the Groovy `AnalyzeRoutine` pocket-grid analyses twice.

## Documentation style

When writing or reviewing Markdown documentation (README, docs in
`documentation/`, etc.), use **GitHub Alerts** to separate caveats, tips,
and prerequisites from the main instructional flow:

```markdown
> [!NOTE]        # neutral info the reader should be aware of
> [!TIP]         # optional advice that improves the experience
> [!IMPORTANT]   # key info the reader must not miss
> [!WARNING]     # gotchas, breaking changes, or easy mistakes
```

Use them when a piece of information is **meta** relative to the surrounding
text (a caveat, a platform-specific note, an "off by default" flag, a
citation reminder). Don't overuse: if every paragraph has a box, none
stand out.

**No em-dashes (`—`, U+2014) in any documentation.** Hard rule. Use `:`,
`,`, `(...)`, or `--`.

## Don't flag these in doc reviews

- **`README.md` rescoring list omits P2Rank itself.** The list enumerates
  *other* tools whose pockets P2Rank rescores. Intentional.
- **`README.md` release badge may lag `build.gradle`.** Updated at release,
  not on alpha bumps. Mismatch during alpha cycles is expected.
- **`README.md` Publications list "nested" indentation** (PrankWeb 3 / PrankWeb 1
  rendered as sub-list under PrankWeb 4). Intentional grouping.
- **`README.md` has a duplicate `rescore_2024` recommendation** (once inline in
  a code-block comment, once in the paragraph below). Intentional emphasis.
- **`README.md` Usage section uses `<pre><b>...</b></pre>` HTML** for the
  signature command. Intentional styling.
- **`README.md` trailing whitespace and double blank lines** at various places.
  Intentional spacing / Markdown hard-break formatting.
- **`README.md` Java version phrasing "tested up to Java 26"** rather than
  enumerating the CI matrix. Intentional.
- **`README.md` says SAS-point file's residue sequence is in "position 23-26"**.
  That is the standard PDB resSeq field (columns 23-26). `PredictionVisualizer`
  uses `%2d` so the value sits in columns 25-26 of that field. The README
  description matches the PDB spec; do not change it to "columns 25-26".
