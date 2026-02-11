# Non-Canonical Amino Acid Residue Mapping

PDB structures often contain modified amino acid residues (e.g., selenomethionine MSE,
phosphoserine SEP). P2Rank maps these to standard amino acids for feature calculation.
The `aa_mapping` parameter controls which mappings are used.

## Modes

| Mode | Description | Mappings |
|------|-------------|----------|
| `minimal` | Default, backward-compatible (MSE→MET, MEN→ASN only) | 2 |
| `pdbfixer` | Extended set from [OpenMM pdbfixer](https://github.com/openmm/pdbfixer) | 87 |
| `/path/to/file.csv` | Custom user-provided mapping file | User-defined |

Residue codes not in the active mapping pass through unchanged.

**`minimal`** preserves original P2Rank behavior. Sufficient for most X-ray/cryo-EM structures.

**`pdbfixer`** covers phosphorylated, methylated, and acetylated residues, selenocysteine,
histidine protonation variants, D-amino acids, and other modifications. Recommended when
structures contain many modified residues.

## Usage

```bash
# Default (minimal)
prank predict -f protein.pdb

# Extended pdbfixer mappings
prank predict -f protein.pdb -aa_mapping pdbfixer

# Custom mapping file
prank predict -f protein.pdb -aa_mapping /path/to/my-mappings.csv
```

## Custom Mapping Files

Any value other than `minimal` or `pdbfixer` is treated as a file path.
To load a file literally named "minimal" or "pdbfixer", use a path: `./minimal`.

A custom file **replaces** all built-in mappings (it does not extend them).
To add entries on top of the pdbfixer set, copy the bundled `aa-mapping-pdbfixer.csv`
and append your entries.

Format — two-column CSV:

```csv
# Custom mappings
MSE,MET
LLP,LYS
SEC,CYS
```

- Lines starting with `#` and empty lines are ignored
- Codes are case-insensitive, 1-4 alphanumeric characters
- Whitespace around codes and commas is trimmed
- Duplicate source codes: warned, first mapping wins
- Self-mappings (e.g., `ALA,ALA`): skipped
- Invalid lines: warned and skipped
- File not found or not readable: P2Rank exits with an error
