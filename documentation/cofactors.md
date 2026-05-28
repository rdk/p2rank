# Cofactors as Protein Surface

PDB structures often contain non-protein groups that are biologically
inseparable from the protein - flavins (FAD, FMN), pyridoxal phosphate (PLP),
hemes, NAD/NADP, coenzyme A, metal cofactors. When P2Rank's default behaviour
drops these groups as "ligands", any pocket that sits next to the cofactor
ends up underestimated or missed. The `-cofactors` parameter lets you mark
selected HETATM groups as **part of the protein surface** so pockets are
generated and ranked correctly in their presence.

## What it does

For each group named in `-cofactors` (or in the dataset `cofactors` column):

1. The group's heavy atoms are added to the protein surface - they contribute
   to SAS-point generation and pocket detection.
2. The group is **excluded** from ligand detection - it does not appear in
   any `*_predictions.csv` or `*_residues.csv` ligand listings, and does not
   affect pocket ranking as a target.

> [!NOTE]
> The same trained model is used; this is a runtime configuration only.
> With the default empty list (`cofactors = []`) behaviour is byte-for-byte
> identical to earlier P2Rank versions.

## When to Use This

| Scenario | Typical cofactors |
|---|---|
| Pocket prediction around a covalently attached flavin | `FAD`, `FMN`, `FDA` (reduced FAD) |
| Pocket prediction in PLP-dependent enzymes (Schiff-base) | `PLP`, `PMP` |
| Heme proteins (cytochromes, P450s) where docking targets sit adjacent to the heme | `HEM`, `HEC`, `HEA`, `HEB` |
| Acyl-binding pockets in CoA-dependent enzymes | `COA` |
| NAD(P)-binding pockets in dehydrogenases | `NAD`, `NAP` (NADP) |
| Tightly bound metals at the entrance of the active site | `ZN`, `MG`, `MN`, `FE`, `CA` |

Use this when the cofactor is biologically part of the active site, not when
it is the *ligand of interest* (in that case it stays a regular ligand).

## Quick Start

```bash
prank predict -f protein.pdb -cofactors FAD                    # all FAD groups
prank predict -f protein.pdb -cofactors 'FAD[group_id:A_500]'  # one specific FAD
prank predict cofactor-dataset.ds                              # per-row via .ds column
```

See [Command-Line Usage](#command-line-usage) for the full grid of forms, and
[Dataset Files](#dataset-files-ds) for the dataset-column layout.

## Command-Line Usage

```bash
# Single cofactor type
prank predict -f protein.pdb -cofactors FAD

# Multiple types
prank predict -f protein.pdb -cofactors FAD,NAD,PLP,HEM

# Precise selection - only the FAD in chain A at residue 600
prank predict -f protein.pdb -cofactors 'FAD[group_id:A_600]'

# Mix bare names and precise specifiers
prank predict -f protein.pdb -cofactors 'FAD,HEM[group_id:A_300]'
```

> [!TIP]
> Quote the argument when it contains square brackets so the shell does not
> interpret them.

## Dataset Files (`.ds`)

Add a `cofactors` column to per-structure dataset files to give each
structure its own cofactor selection. A non-blank value overrides the
global `-cofactors` setting for that row.

```
HEADER: protein     cofactors
        1ahp.pdb    PLP
        1eli.pdb    FAD,NAD
        2v61.pdb    FAD[group_id:A_600]
        4ri7.pdb    GSH[contact_res_ids:A_C158,A_R171,A_E186]
        1fbl.pdb
```

> [!WARNING]
> A **blank** cofactors column for a row means "inherit the global `-cofactors`
> setting for this structure" - it does **not** mean "no cofactors". To force no
> cofactors for a specific row, leave `-cofactors` off entirely and leave the
> column blank, or remove the row from the dataset.

Combined with other columns:

```
HEADER: protein     chains    cofactors
        1ahp.pdb    A         PLP
        1eli.pdb    A,B       FAD,NAD
```

## Config File

Set a default for a config-driven workflow:

```groovy
// config/my-config.groovy
cofactors = ["FAD", "PLP", "HEM"]
```

```bash
prank predict -c my-config.groovy -f protein.pdb
```

## Cofactor Specifier Syntax

The `-cofactors` parameter and the dataset `cofactors` column accept the
**same identifier syntax as the dataset `ligands` column**. Each entry is
either a bare residue name or a residue name followed by a square-bracketed
specifier.

| Form | Matches |
|------|---------|
| `FAD` | All groups whose PDB residue name is `FAD` |
| `FAD[group_id:A_500]` | The FAD group in chain `A` at residue number `500` |
| `FAD[A_500]` | Shorthand for `[group_id:A_500]` |
| `FAD[atom_id:12345]` | The FAD group containing the atom with PDB atom serial `12345` |
| `FAD[contact_res_ids:A_D246,A_T259,A_E423]` | The FAD group whose surrounding polymer residues match this set |

### Chain and residue identifiers (PDB vs mmCIF)

Specifiers like `group_id:A_500` and `contact_res_ids:A_D246` reference the
**author** chain ID and residue number - what 3D viewers display, and what
the PDB file uses directly. In mmCIF files these correspond to the
`auth_asym_id` + `auth_seq_id` columns, NOT to `label_asym_id` /
`label_seq_id`; the two can differ.

| File format | Chain ID source | Residue number source |
|---|---|---|
| PDB | Column 22 (single character; what you see in PyMOL/Chimera) | Columns 23-26 + insertion code |
| mmCIF | `_atom_site.auth_asym_id` (NOT `label_asym_id`) | `_atom_site.auth_seq_id` (NOT `label_seq_id`) |

If you don't want to read the raw file, the discovery command
`prank analyze cofactors -f protein.pdb`
(see [Discovery & Diagnostics](#discovery--diagnostics))
prints every HETATM group with its exact `group_id` value ready to paste
into a specifier.

### `group_id`

`chain_resNumber`, e.g. `A_500` for chain A residue 500. Insertion codes
are part of the residue number: `A_500A` matches residue 500A.

### `atom_id`

Integer PDB atom serial. Useful when residue numbering is unstable across
re-deposits but the structure team kept atom serials. Only one atom needs
to match - the whole group is then included.

### `contact_res_ids`

A list of residue identifiers that must all be present in the polymer
neighbourhood (within 7 Å) of the cofactor. Each entry is
`chain_aaCode_resNumber` (e.g. `A_D246` = chain A, Asp 246) or
`chain_resNumber` (e.g. `A_246`) when you don't want to check the residue
type. Useful when the cofactor's `group_id` is unstable but its binding
pocket residues are constant.

### Case

PDB residue names (the prefix like `FAD`) are normalized to uppercase
automatically: `fad`, `Fad`, and `FAD` all match the same groups. The
contents of `[...]` are passed through as-is and remain case-significant:
`A_D246` (chain A, Asp 246) is not the same as `a_d246`. PDB and mmCIF
files generally use uppercase chain IDs and canonical case for AA codes,
so in practice copy-paste from the file works.

## Precedence

When the same cofactor configuration source could be set in multiple places,
the highest-priority setting wins:

1. Dataset `cofactors` column (per row)
2. `-cofactors` command-line argument
3. `cofactors` setting in a config file
4. Default - empty list

If a group matches **both** a cofactor specifier and an explicit ligand
definition in the dataset, the cofactor wins: the group is treated as
surface and excluded from ligand detection. This is intentional -
cofactors describe surface, not targets.

## Distant Cofactors

By default, P2Rank logs a `WARN` when a matched cofactor's centre
of mass is more than `15 Å` from the nearest protein atom. This usually
means the cofactor is a crystallization artifact or a free molecule in
solvent, and you may want to exclude it.

The cofactor **is still included in the surface**; the warning is advisory.
Tune the threshold or disable the check:

```bash
prank predict -f protein.pdb -cofactors FAD -cofactor_max_protein_dist 25   # relax
prank predict -f protein.pdb -cofactors FAD -cofactor_max_protein_dist 0    # disable
```

## Discovery & Diagnostics

Use `prank analyze cofactors` to inspect what HETATM groups exist in a
structure or dataset, and to dry-run a `-cofactors` configuration before
running a prediction.

### Survey mode (no `-cofactors`)

List every HETATM group with its chain, residue number, atom count, and
distance to the protein:

```bash
prank analyze cofactors -f protein.pdb
prank analyze cofactors dataset.ds
```

Output files (in the analyze output directory):

| File | Content |
|---|---|
| `het_groups.csv` | One row per HETATM group instance. Columns: `protein`, `het_name`, `chain`, `res_num`, `group_id`, `n_heavy_atoms`, `dist_to_protein`, `currently_classified_as` (`relevant_ligand` / `ignored` / `cofactor` / `other`), `would_be_cofactor` (always present; constant 0 without `-cofactors`, populated 0/1 in dry-run mode) |
| `het_groups_summary.txt` | Per-name frequency table across the dataset, sorted by structure-coverage |
| `visualizations/<protein>.pml` | PyMOL script highlighting any configured cofactors (if `-visualizations 1`) |

The `group_id` column gives the exact string for a precise specifier -
copy it into `-cofactors 'FAD[<group_id>]'`.

### Dry-run mode (with `-cofactors`)

Add `-cofactors` to the analyze command and P2Rank will report which
specifiers match, structure by structure, without running any prediction:

```bash
prank analyze cofactors -f protein.pdb -cofactors FAD,PLP
prank analyze cofactors -f protein.pdb -cofactors 'FAD[group_id:A_600]'
prank analyze cofactors dataset.ds      -cofactors FAD,PLP
```

Additional outputs:

| File | Content |
|---|---|
| `cofactor_matches.csv` | One row per (structure, specifier) pair. Columns: `protein`, `specifier`, `matched_count`, `matched_group_ids`, `unmatched_reason` |

In dry-run mode the `would_be_cofactor` column in `het_groups.csv` is
populated with 0/1 values (the column is always declared, but stays 0
without `-cofactors`) so you can diff it against `currently_classified_as`
to confirm the proposed change.

Console summary (also written to `het_groups_summary.txt`):

```
HETATM Survey for dataset.ds (87 structures)

Most frequent HETATM groups:
  HOH       87 structures (100.0%) - 412 groups total
  SO4       42 structures ( 48.3%) - 89 groups total
  FAD       28 structures ( 32.2%) - 31 groups total
  PLP       13 structures ( 14.9%) - 13 groups total
  ...

Cofactor specifier match (per-item resolution, column overrides global):
  FAD                            28/87 structures, 31 groups total
  PLP[group_id:A_300]             9/87 structures,  9 groups total
  HEM                             0/87 structures, 0 groups total   ← matched no structures
```

This is the fastest way to verify a precise specifier before running on a
large dataset.

## Visualizations

When `-visualizations` is enabled, every routine that writes PyMOL output
(`prank predict`, `prank analyze binding-sites`, `prank analyze
labeled-residues`, `prank analyze cofactors`, …) highlights matched
cofactor atoms as **teal sticks**, separate from the standard renderings.

Default style summary (with `-vis_highlight_ligands` / `-vis_highlight_cofactors`
left at their defaults):

| Element | Default style |
|---|---|
| Protein chain | Surface (`color bluewhite`) |
| Ligand atoms | Red spheres (selection `ligand_atoms`, rendered by default) |
| Ligand atoms when `-vis_highlight_ligands 1` | Red/violet spheres (separate selections in the older renderer) |
| **Cofactor atoms** (when `-cofactors` set) | **Teal sticks** (`#49A8C7`) |
| Pocket pseudoatoms | Per-pocket palette |

### Per-cofactor PyMOL selections

The renderer emits one PyMOL selection per cofactor name, plus an aggregate.
For `-cofactors FAD,PLP` you get:

```
select cofactor_FAD, id 123 or id 124 or id 125 or ...
select cofactor_PLP, id 456 or id 457 or id 458 or ...
select cofactor_atoms, cofactor_FAD or cofactor_PLP
```

Each atom appears in its own `id <serial>` clause; PyMOL does not accept a
comma-separated atom-id list, so the renderer joins the clauses with `or`.

In PyMOL you can address `cofactor_FAD` and `cofactor_PLP` directly to,
e.g., colour them differently, hide one of them, or zoom in. The bracketed
selection name is sanitised: any non-alphanumeric character in the residue
name becomes an underscore (rare - PDB names are 1–4 alphanumeric anyway).

If any specifier matched nothing, the .pml file also contains a diagnostic
comment listing the unmatched specifiers, so a user inspecting the
visualization can see which patterns didn't apply.

### Disabling the highlight

```bash
prank predict -f protein.pdb -cofactors FAD -vis_highlight_cofactors 0
```

`-vis_highlight_cofactors` defaults to `true`. Setting it to `false` does
not change pocket prediction - cofactor atoms are still part of the
surface; only the colour highlight is removed.

## Common Cofactors Reference

### Frequently used

| Name | Full name | Heavy atoms | Typical role |
|------|-----------|-------------|--------------|
| FAD | Flavin adenine dinucleotide | ~53 | Redox |
| FMN | Flavin mononucleotide | ~31 | Electron transfer |
| NAD | Nicotinamide adenine dinucleotide | ~44 | Redox |
| NAP | NADP (phosphorylated NAD) | ~48 | Redox |
| PLP | Pyridoxal 5'-phosphate | ~15 | Amino-acid metabolism |
| HEM | Heme | ~43 | Oxygen / electron transport |
| HEC | Heme C (covalent) | ~43 | Cytochrome c |
| COA | Coenzyme A | ~51 | Acyl carrier |
| TPP | Thiamine pyrophosphate | ~25 | Decarboxylation |
| SAM | S-adenosylmethionine | ~27 | Methylation |
| GSH | Glutathione | ~20 | Redox / detoxification |

### Modified variants

Many cofactors have variant codes for chemically modified forms - these
are **not aliases** in P2Rank; you must specify the exact code that appears
in your structure file.

| Standard | Modified variant | Notes |
|----------|------------------|-------|
| FAD | FDA | Reduced FAD (FADH₂) |
| NAD | NAI, NAJ | Modified NAD |
| HEM | HEC, HEA, HEB | Heme C, A, B variants |

To list every HETATM code present in a structure:

```bash
grep '^HETATM' protein.pdb | awk '{print $4}' | sort -u
```

For mmCIF:

```bash
grep '^HETATM' protein.cif | awk '{print $6}' | sort -u
```

### Metal ions

Single-atom HETATM "groups" can also be used as cofactors. They contribute
one heavy atom to the surface each, which is small but can matter for
adjacent metal-coordinated pockets.

| Name | Element | Notes |
|------|---------|-------|
| ZN | Zinc | Catalytic or structural |
| MG | Magnesium | ATP binding, phosphoryl transfer |
| MN | Manganese | Metalloenzymes |
| FE | Iron | Non-heme iron |
| FE2 | Iron (II) | Ferrous iron |
| CU | Copper | Electron transfer |
| CA | Calcium | Signaling, structural |

> [!NOTE]
> `CA` is also the PDB atom name for protein C-alpha backbone atoms. The
> cofactor specifier matches on residue name (HETATM group name), not on
> atom name, so `-cofactors CA` correctly selects calcium ions and does
> not touch backbone atoms. The shared spelling can still be confusing
> when reading PDB files by hand.

## Interactions with Other Features

### Explicit ligand definitions (`ligands` column)

> [!IMPORTANT]
> If the same group is named in both the `ligands` and `cofactors` columns,
> the cofactor side wins - the group is treated as surface. This is the only
> sensible reading of "this group is a cofactor": it must not also be a
> prediction target.

### `chains` column / `-chains`

Chain reduction runs before cofactor matching. A cofactor on an excluded
chain is simply not found. P2Rank logs a `WARN` line when it detects that
the **name** of a missing cofactor exists in the unreduced structure
(name-only check, see Known Limitations).

### `aa_mapping`

`-aa_mapping` (non-canonical residue mapping) operates on polymer-chain
residues; `-cofactors` operates on HETATM groups. They never interact -
**unless** a custom `aa_mapping` CSV contains an entry whose 3-letter code
matches a cofactor's group name. In that case the cofactor atoms inherit
the mapped amino acid's feature-table entries instead of zeros, and P2Rank
emits a startup warning:

```
WARN  Cofactor specifier(s) name(s) [PLP] are also covered by the active
aa_mapping. Cofactor atom features will be computed using the mapped AA's
table entries instead of cofactor defaults. Remove the entry from
aa_mapping or change the cofactor specifier to fix.
```

If you see this warning and didn't intend the override, either:
- remove the entry from your custom `aa_mapping` CSV, or
- change the cofactor specifier to use a different code.

The bundled `pdbfixer` mapping only contains canonical AA aliases, so this
warning typically appears only for custom mappings.

Normal combined use is unaffected:

```bash
prank predict -f protein.pdb -aa_mapping pdbfixer -cofactors FAD,PLP
```

### `load_ligands_from_separate_files`

When this mode is on, all HETATMs from the primary structure file are
moved to the ignored-ligand list without consulting the cofactor settings.
Cofactor atoms are still added to the surface correctly, but a cofactor
present in the primary file will also appear in the ignored-ligand listing.
This is a cosmetic issue and does not affect predictions; it is rare in
practice. If you need a clean ignored-ligand listing in this mode, remove
the cofactor from the primary file or split it into a separate ligand file.

### `csv` feature (`-feat_csv_columns`)

Cofactor atoms are not expected to have entries in user-provided per-atom
or per-residue CSV files (those describe polymer atoms / residues). When
both `-cofactors` and `-feat_csv_columns` are set, cofactor atoms receive
a zero vector for every CSV column. This bypass is unconditional -
`-feat_csv_ignore_missing` retains its strict default for polymer atoms.

## Feature Values for Cofactor Atoms

When `-cofactors` is set, the matched HETATM atoms become full citizens of
the protein surface for the purposes of pocket prediction. Different
features handle them in different ways. The table below summarises what
each feature does for an atom that is part of a cofactor (e.g. a nitrogen
of FAD). Users running with `-cofactors` should know these behaviours
because they affect the feature distribution the model sees near the
cofactor.

| Feature | What cofactor atoms contribute |
|---|---|
| `chem` (AA chemistry) | Only the element-based atom counters (`atomC` / `atomO` / `atomN`) and `atomDensity`. All AA-property fields (`hydrophobic`, `polar`, `acidic`, `basic`, …) are zero. |
| `atom_table` / `residue_table` / `aa` / `ares` | Zero values - the cofactor's residue code isn't in the AA-keyed lookup tables. |
| `volsite` (pharmacophore) | Zero - the lookup cascade only matches AA atom names. |
| `bfactor` | The cofactor atom's actual B-factor (real data). |
| `hybridization` | Element-based fallback (sp2 for N, sp3 for C / O / S / P / Se). |
| `sidechain` | Always 1 (cofactor atoms are not backbone). |
| `exposed` | 1 if the cofactor atom is solvent-accessible, 0 otherwise. |
| `csv` | Zero (see [csv feature](#csv-feature--feat_csv_columns) above). |
| `conservation` | Zero - cofactors are not in the conservation score file. |
| `cres1` / `cres1pos` (SAS-level) | **Unaffected.** Uses nearest polymer residue, never sees the cofactor name. |
| `protrusion` (SAS-level) | Cofactor atoms count toward protrusion at nearby SAS points - correct, the cofactor adds bulk. |
| Energy / Lennard-Jones (SAS-level) | Element-based LJ parameter lookup, works identically for polymer and cofactor atoms. |

### What this means in practice

At a SAS point that sits *on* the cofactor surface, the neighbourhood is
mostly cofactor atoms, so the AA-property features are near zero. This is
biologically correct - the local surface really isn't amino-acid-like.

At a SAS point on the polymer *near* a cofactor, the neighbourhood mixes
cofactor and polymer atoms. The cofactor atoms contribute zero to the
AA-property features but still count toward the average's divisor - so
the AA signal is diluted in proportion to the cofactor fraction of the
neighbourhood.

> [!WARNING]
> P2Rank's prediction models were trained on protein-only data where every
> surface atom had full AA-property values. The dilution introduces a small
> feature-distribution shift the trained model wasn't directly exposed to.
> For typical sites (a cofactor occupying a minor fraction of the
> neighbourhood), the shift is small. Predictions remain useful, but you
> should treat raw pocket scores from cofactor-enabled runs as not strictly
> comparable to cofactor-disabled runs on the same structure.

If you observe noticeably different pocket rankings with and without
`-cofactors` on a structure where you expect them to be similar, that's
the dilution effect - see the [`prank analyze cofactors`](#discovery--diagnostics)
diagnostic to inspect which atoms were affected.

## Troubleshooting

### "I don't see the startup message"

```
Cofactors to include as protein surface: [...]
```

is logged only when `-cofactors` is non-empty. If it's missing, check:

- You passed `-cofactors` (or set it in the config / dataset column).
- The argument has no spaces around the commas: `FAD,PLP`, not `FAD, PLP`.
- The argument is quoted if it uses square brackets: `'FAD[A_500]'`.

### "I don't see the per-structure 'included' message"

The "included" message is logged at INFO level, so it appears at any log
level (it is not gated behind DEBUG). If you don't see it, the specifier
matched no groups, or P2Rank exited before processing that structure.

When the specifier matched, you will see:

```
Structure protein.pdb: included 1 cofactor type(s) as protein surface (FAD: 53 atoms)
```

If the specifier matched nothing, P2Rank instead emits a DEBUG-level
message listing the available HETATM groups. Enable DEBUG to see it:

```bash
prank predict -f protein.pdb -cofactors FAD -log_level DEBUG
```

```
Structure protein.pdb: cofactor specifier(s) [FAD] matched no groups. Available HETATM groups: [...]
```

### "The cofactor still appears in my predictions CSV"

Run the basic checks:

```bash
# Confirm protein-atom count actually increased
prank predict -f protein.pdb -cofactors FAD            # log: "protein   atoms: 4123"
prank predict -f protein.pdb                            # log: "protein   atoms: 4070"

# Confirm the cofactor is no longer in the predictions CSV
grep -i FAD output/protein.pdb_predictions.csv          # should be empty
```

If the cofactor name still appears after enabling `-cofactors`, check the
spelling against the HETATM listing in the PDB file
(`grep '^HETATM' protein.pdb | awk '{print $4}' | sort -u`).

### "My precise specifier doesn't match"

The fastest diagnostic is dry-run mode of `analyze cofactors`:

```bash
prank analyze cofactors -f protein.pdb -cofactors 'FAD[group_id:A_600]'
# look at cofactor_matches.csv: matched_count, matched_group_ids, unmatched_reason
```

`het_groups.csv` from the same run lists every HETATM group in the file
with its exact `group_id` value to copy back into the specifier.

If P2Rank isn't available or you want to spot-check directly, the raw
PDB recipe still works:

```bash
grep '^HETATM.*FAD' protein.pdb | head -3
# HETATM 4123  N1  FAD A 600       12.345  ...
#                            ^^^^^
# chain A, residue 600 → group_id: A_600
```

Insertion codes are part of the residue number:
`grep` shows `... FAD A 500A ...` → use `A_500A`.

### "I get a `PrankException: Invalid cofactor specifier...`"

A specifier failed to parse. The error message includes the offending
string and the reason (unknown specifier type, non-integer atom_id, etc.).
Check the syntax table above.

## Known Limitations

- **Case-sensitive specifier contents.** The group-name prefix (e.g. `FAD`)
  is normalized to uppercase (case-insensitive), but the contents of `[...]`
  specifiers (`group_id`, `contact_res_ids`) are case-significant. Chain IDs
  and residue codes must match the case used in the structure file.
- **AA-property feature dilution.** Cofactor atoms participate in the
  feature aggregation at each SAS point but contribute zero values for
  AA-property features. This dilutes the polymer signal at SAS points near
  a cofactor - see [Feature Values for Cofactor Atoms](#feature-values-for-cofactor-atoms).
  Effect is typically small; significant only if cofactors dominate the
  local neighbourhood.
- **`load_ligands_from_separate_files`.** A cofactor present in the primary
  structure file appears in the ignored-ligand listing in this mode. Surface
  inclusion is unaffected.
- **Chain-restriction diagnostic.** The "lost during chain reduction" log
  matches on residue name only (not the full precise specifier), so it can
  occasionally report a name that wouldn't have matched the precise
  specifier anyway. Treat it as an advisory.
- **`aa_mapping` overlap.** A custom AA-mapping entry that matches a
  cofactor's group name silently remaps cofactor atoms' features to the
  mapped AA. Detected at startup with a WARN (see `aa_mapping` interaction
  section above).
- **fpocket rescoring.** This feature changes pocket prediction in `prank
  predict`. It does not change the way `prank rescore` invokes fpocket;
  fpocket has its own hard-coded list of "ligand" HETATMs.

## See Also

- [`aa-mapping.md`](aa-mapping.md) - non-canonical residue mapping for
  modified amino acids in the polymer chain. Independent feature, useful
  alongside `-cofactors`.
- [`feature-setup.md`](feature-setup.md) - how feature values are
  calculated. See the [Feature Values for Cofactor Atoms](#feature-values-for-cofactor-atoms)
  section above for cofactor-specific behaviour.
- [`utility-commands.md`](utility-commands.md) - other `prank analyze`
  subcommands. The cofactor-specific `prank analyze cofactors` is
  documented under "Discovery & Diagnostics" above.
- `documentation/dev/cofactors.md` (engineering doc) - implementation status,
  design choices with reasoning, planned improvements. Read this if you are
  changing the feature, not if you are using it.
