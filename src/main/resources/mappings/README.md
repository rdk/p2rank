# Amino Acid Mapping Files

This directory contains CSV files that map non-canonical (modified) amino acid
residue codes to standard 20 amino acid codes. These mappings are used by P2Rank
to normalize residue codes for feature extraction and prediction.

## Files

### aa-mapping-pdbfixer.csv

Extended mapping file with 87 mappings derived from OpenMM's pdbfixer tool.

## Data Provenance

### Source

The mappings in `aa-mapping-pdbfixer.csv` are derived from:

- **Project**: OpenMM pdbfixer
- **Repository**: https://github.com/openmm/pdbfixer
- **File**: `pdbfixer/pdbfixer.py`
- **Data extracted**: February 2026
- **License**: MIT (pdbfixer is open source)

The pdbfixer tool is widely used in molecular dynamics workflows to prepare
PDB files for simulation, including substituting non-standard residues with
their standard equivalents.

### Original P2Rank Mappings

The following mappings existed in P2Rank before this feature (hardcoded in
`PdbUtils.correctResidueCode()`):

| Modified | Standard | Description |
|----------|----------|-------------|
| MSE | MET | Selenomethionine (common in X-ray crystallography) |
| MEN | ASN | N-Methyl Asparagine |

These are preserved in both "minimal" mode and the pdbfixer file.

## Modifications from Original pdbfixer

The following changes were made to the original pdbfixer mappings:

### 1. Removed: ACE -> ALA

**Original pdbfixer mapping**: `ACE,ALA`

**Reason for removal**: ACE (acetyl) is an N-terminal capping group, not a
modified amino acid residue. In PDB files, ACE typically appears as:
- A separate HETATM record representing the acetyl moiety
- Part of post-translational modifications
- A crystallographic artifact

Mapping ACE to ALA would incorrectly:
- Count capping groups as alanine residues in the protein chain
- Potentially affect binding site predictions near N-termini
- Introduce spurious residues that aren't part of the actual sequence

Similar capping groups (NME for C-terminus) are also not mapped.

### 2. Removed: Duplicate DVA Entry

The original extraction had DVA (D-Valine) listed twice. The duplicate was
removed to avoid warnings during loading.

### 3. Preserved: MEN -> ASN

This mapping was present in P2Rank's original hardcoded behavior (N-Methyl
Asparagine). It is included in both modes to maintain backward compatibility.

## File Format

CSV format with two columns:
```
MODIFIED_CODE,STANDARD_CODE
```

- Lines starting with `#` are comments
- Empty lines are ignored
- Codes are case-insensitive (converted to uppercase during loading)
- Codes must be 1-4 alphanumeric characters
- Whitespace around codes and commas is trimmed (e.g., ` LLP , LYS ` is valid)

## Usage Modes

The `aa_mapping` parameter controls which mappings are used:

| Mode | Description | Mappings |
|------|-------------|----------|
| `minimal` | Original P2Rank behavior (default) | 2 (MSE, MEN) |
| `pdbfixer` | Extended mappings from pdbfixer | 87 |
| `./file.csv` or `/path/to/file.csv` | Custom user-provided file | User-defined |

**Note**: The strings `minimal` and `pdbfixer` are reserved mode names. To use a
file with these names, specify a path: `./minimal` or `./pdbfixer`.

Examples:
```bash
# Use extended pdbfixer mappings
prank predict -f protein.pdb -aa_mapping pdbfixer

# Use custom mapping file
prank predict -f protein.pdb -aa_mapping /path/to/custom.csv
```

**Note**: Residue codes not in the mapping (including all 20 standard amino
acids and unknown codes) pass through unchanged.

## Categories of Mappings

The pdbfixer file includes mappings for:

1. **Selenocysteine/Selenomethionine** (SEC, CSE, MSE) - Selenium-containing
   amino acids commonly used in X-ray crystallography for phasing

2. **Post-translational modifications**:
   - Phosphorylated residues (SEP, TPO, PTR)
   - Methylated residues (MLY, M3L, MME, etc.)
   - Acetylated lysines (ALY)
   - Hydroxylated prolines (HYP, 4HY)

3. **Protonation state variants** (HID, HIE, HIP, HSD, HSE, HSP) - Different
   tautomers/protonation states of histidine used in MD simulations

4. **D-amino acids** (DAL, DAR, DVA, etc.) - Mirror-image forms of standard
   L-amino acids. Includes 18 of 20 possible; glycine is achiral, D-Met not
   in pdbfixer source.

5. **Other modified residues** - Various biochemically modified forms

## Validation

During loading, the mapper validates:
- Two-column format (warns and skips malformed lines)
- Alphanumeric codes 1-4 characters (warns and skips invalid)
- Target is a standard amino acid (warns but accepts)
- No duplicate source codes (warns and keeps first)
- No self-mappings like ALA->ALA (skips silently)

## Adding Custom Mappings

To use custom mappings:

1. Create a CSV file following the format above
2. Use the parameter: `-aa_mapping /path/to/your/mappings.csv`

**Important**: Custom files REPLACE all built-in mappings entirely. They do not
extend or add to the "minimal" or "pdbfixer" mappings. If you want pdbfixer
mappings plus your own additions, copy `aa-mapping-pdbfixer.csv` and add your
entries to it.

Example custom file:
```csv
# My custom mappings
XYZ,ALA
ABC,GLY
```

## Error Handling

For custom mapping files:
- File path does not exist: P2Rank exits with an error
- File is not readable: P2Rank exits with an error
- File contains no valid mappings: Warning logged, mapper uses empty set
  (all codes pass through unchanged)

## References

- PDBFixer: https://github.com/openmm/pdbfixer
- wwPDB Chemical Component Dictionary: https://www.wwpdb.org/data/ccd
- RCSB PDB modified residues: https://www.rcsb.org/docs/general-help/ligand-structure-quality
