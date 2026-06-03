# P2Rank — Repo Notes

## Sister repos

- **FasterMolecularSurface** is a permanent sister repo, always cloned next to
  this one at `../FasterMolecularSurface`
  (https://github.com/rdk/FasterMolecularSurface). It is the source of the
  `cz.cuni.cusbg:faster-molecular-surface` Maven dependency (see
  `build.gradle`); built jars are vendored here under
  `lib/local-mvn-repo/cz/cuni/cusbg/faster-molecular-surface/`. P2Rank-side
  wrappers live in `src/main/groovy/cz/siret/prank/geom/` (`SurfaceStrategy`,
  `cdksurface/`).

## Build artifacts (do not edit)

- `distro/README.md` is **generated** from the top-level `README.md` by the
  `copyDocumentation` task in `build.gradle` on every `./gradlew assemble`.
  It is gitignored. Edit the top-level `README.md` only; never edit
  `distro/README.md` directly — any changes are silently overwritten on the
  next build.

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
