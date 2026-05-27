# P2Rank — Repo Notes

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
