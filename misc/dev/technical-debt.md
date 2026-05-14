# Technical Debt

Long-form notes on known suboptimal designs in this codebase that haven't been
worth fixing yet. Each entry should state the issue, why it matters, the current
workaround (if any), what a proper fix would look like, and the trigger that would
justify the work.

---

## CLI list-param parsing: `Sutils.parseList` is not bracket-aware

**Where:** `src/main/groovy/cz/siret/prank/utils/Sutils.groovy` (`parseList`, `split`).

**Issue.** `Params.updateFromCommandLine` routes every `List<String>` CLI argument
through `Sutils.parseList`, which falls back to `Sutils.split(str, ",")` - a Guava
splitter on the literal comma character. The splitter has no awareness of bracketed
content, so a value like

```
-cofactors 'FAD[contact_res_ids:A_D246,A_T259,A_E423]'
```

gets shredded into three list elements:

```
["FAD[contact_res_ids:A_D246", "A_T259", "A_E423]"]
```

This is a real footgun for any list-typed parameter whose elements can contain
commas inside brackets/parens - historically only the `ligands` *column* (parsed
separately via `Sutils.splitRespectInnerParentheses` in `Dataset.parseLigandsColumn`),
and now `cofactors` (Issue #79 part 2).

**Why it matters.**

- Silent corruption: the user sees no error; their specifier just fails to match.
- The over-split values often pass `LigandDefinition.parse` because `partBetween`
  falls back to `str.length()` when no closing `]` is found - producing nonsense
  definitions that match nothing.
- Any future list-typed param that wants to support bracketed structure (precise
  identifiers, contact-res lists, nested expressions) will hit the same bug.

**Current workaround.** `CofactorHandler.parseAndValidate(List<String>)` is tolerant
of upstream damage: it re-joins the elements with `,`, then re-splits with
`Sutils.splitRespectInnerParentheses(str, ',', '[', ']')`. So cofactors specifically
recover. The same `parseAndValidate(String)` overload is used for the dataset
column, where `Dataset.resolveCofactorDefinitions(Item)` passes the raw column
string straight in - no naive split happens there.

Workaround is documented in `documentation/dev/cofactors.md` (R22). Tests pin the
behaviour: `CofactorHandlerTest.contactResIdsCommasArePreservedFromList`.

**Proper fix (sketch).**

Two viable approaches:

1. **Make `Sutils.parseList` bracket-aware globally.** Add a `splitter` parameter
   or detect brackets and switch to `splitRespectInnerParentheses` when the input
   contains them. Risk: any existing list-typed param whose values happen to
   contain brackets without intending them as scope markers (e.g. a string with
   `[debug]` prefix) would now parse differently. Audit needed.

2. **Per-param parsing registry.** Annotate (or table-driven) which params want
   bracket-aware splitting. Touch only `Params.updateFromCommandLine`. Lower
   blast radius, more boilerplate.

Either fix would let downstream callers (like `CofactorHandler.parseAndValidate`)
drop their defensive re-join/re-split. The "centralized recovery" is functional
but inelegant: it asks downstream layers to undo damage done upstream.

**Trigger.** Add a third list-typed param with bracketed structure, OR a user
reports unexpected behaviour with `-cofactors '...[contact_res_ids:...]'` despite
the recovery (e.g., the recovery itself has a corner case we missed).
