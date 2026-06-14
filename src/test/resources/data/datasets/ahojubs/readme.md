# AhojUBS Dataset Files

Test data for `AhojUbsSiteParser` — explicit binding site definitions from the AhojUBS pipeline.

Both files are standard CSV (RFC 4180). They share the columns that the parser requires
but differ in total column count, column order, and the presence of quoted fields.

## ahojubs_reduced.csv

Minimal 9-column format containing only the fields needed for site definitions.

| Column         | Description                                          | Example                              |
|----------------|------------------------------------------------------|--------------------------------------|
| site_uniprots  | UniProt accession(s)                                 | A0A009I821                           |
| site_uid       | Unique site identifier                               | A0A009I821:ST_LJ100:1               |
| site_recipe    | Recipe/classification label                          | A0A009I821:1                         |
| threshold      | Score threshold used to define the site              | ST_LJ                                |
| afdb_filename  | AlphaFold DB model filename (lookup key)             | AF-A0A009I821-F1-model_v6.cif.gz    |
| chain_resi     | Space-separated residue IDs (`chain_seqNum`)         | A_85 A_90 A_91 A_92 A_93 A_94 A_95  |
| center_x       | Site centroid X coordinate                           | 10.83                                |
| center_y       | Site centroid Y coordinate                           | -2.461                               |
| center_z       | Site centroid Z coordinate                           | -29.63                               |

21,608 sites across 6,196 unique proteins.

## ahojubs_full.csv.gz

Extended 59-column format with additional pocket statistics, PDB chain metadata,
overlap metrics, pLDDT scores, and more. Some columns contain quoted values with
embedded commas (e.g. `union_residues`, `intersection_residues`).

Gzipped (57 MB to 7.6 MB); `AhojUbsSiteParser` reads it transparently via
`Futils.inputStream` (extension-based decompression).

The columns used by the parser (`site_uid`, `afdb_filename`, `chain_resi`,
`center_x`, `center_y`, `center_z`) are present with the same names but at
different positions than in the reduced format.

81,685 sites across 9,908 unique proteins.

### All columns

| #  | Column                         | Example                                    | Description                                              |
|----|--------------------------------|--------------------------------------------|----------------------------------------------------------|
| 1  | threshold                      | ST_100                                     | Score threshold category                                 |
| 2  | threshold_detail               | ST_100                                     | Detailed threshold variant                               |
| 3  | site_uniprot                   | A0A009I821                                 | UniProt accession                                        |
| 4  | site_uid                       | A0A009I821:ST_100:1                        | Unique site identifier                                   |
| 5  | site_recipe                    | A0A009I821:1                               | Recipe/classification label                              |
| 6  | n_unp_pockets                  | 1                                          | Number of UniProt pockets                                |
| 7  | n_unp_pockets_multichain       | 1                                          | Number of UniProt pockets (multichain)                   |
| 8  | n_unp_pockets_ST_030           | 1                                          | Pocket count at ST_030 threshold                         |
| 9  | n_unp_pockets_multichain_ST_030| 1                                          | Pocket count at ST_030 (multichain)                      |
| 10 | n_unp_pockets_ST_050           | 1                                          | Pocket count at ST_050 threshold                         |
| 11 | n_unp_pockets_multichain_ST_050| 1                                          | Pocket count at ST_050 (multichain)                      |
| 12 | n_unp_pockets_ST_070           | 1                                          | Pocket count at ST_070 threshold                         |
| 13 | n_unp_pockets_multichain_ST_070| 1                                          | Pocket count at ST_070 (multichain)                      |
| 14 | n_unp_pockets_ST_100           | 1                                          | Pocket count at ST_100 threshold                         |
| 15 | n_unp_pockets_multichain_ST_100| 1                                          | Pocket count at ST_100 (multichain)                      |
| 16 | n_unp_pockets_ST_LJ            | 1                                          | Pocket count at ST_LJ threshold                          |
| 17 | n_unp_pockets_multichain_ST_LJ | 1                                          | Pocket count at ST_LJ (multichain)                       |
| 18 | members                        | 3                                          | Number of member observations                            |
| 19 | members_total                  | 3                                          | Total member count                                       |
| 20 | n_entries                      | 3                                          | Number of PDB entries                                    |
| 21 | n_entries_total                | 3                                          | Total PDB entry count                                    |
| 22 | n_uniprots                     | 1                                          | Number of UniProt accessions                             |
| 23 | n_chains                       | 1                                          | Number of chains                                         |
| 24 | max_n_chains3                  | 1                                          | Maximum chain count (variant 3)                          |
| 25 | multimeric_status_all_unique   | 1                                          | Whether all multimeric states are unique                 |
| 26 | rep_job                        | 7uw1-A-OIY-3007                            | Representative PDB job (entry-chain-ligand-serial)       |
| 27 | rep_job_overlap                | 7.0                                        | Representative job overlap score                         |
| 28 | rep_job_resis3                 | 7.0                                        | Representative job residue count (variant 3)             |
| 29 | job_names                      | 7uvy-A-OIY-3001;7uvz-A-OIY-3003;...       | Semicolon-separated list of all PDB jobs                 |
| 30 | resis3_unit                    | 7.0                                        | Residue count per unit (variant 3)                       |
| 31 | site_size_unit                 | 7.0                                        | Site size per unit                                       |
| 32 | site_size_intersection         | 7.0                                        | Site size at intersection of observations                |
| 33 | n_union                        | 7                                          | Number of residues in union across observations          |
| 34 | n_intersection                 | 7                                          | Number of residues in intersection across observations   |
| 35 | union_residues                 | ['A0A009I821_85', 'A0A009I821_90', ...]    | Quoted list of all residue IDs across observations       |
| 36 | intersection_residues          | ['A0A009I821_85', 'A0A009I821_90', ...]    | Quoted list of residue IDs common to all observations    |
| 37 | pocket_class                   | apo                                        | Pocket classification (apo/holo)                         |
| 38 | pocket_density_combined        | 0.46                                       | Combined pocket density score                            |
| 39 | pocket_density_pair            | 0.47                                       | Pairwise pocket density score                            |
| 40 | pocket_density_strongest       | 0.49                                       | Strongest pocket density score                           |
| 41 | pocket_overlap_mode            | 0.72                                       | Mode of pocket overlap                                   |
| 42 | pocket_overlap_overall         | 0.61                                       | Overall pocket overlap                                   |
| 43 | pocket_overlap_pair            | 0.66                                       | Pairwise pocket overlap                                  |
| 44 | pocket_separation_pair         | 0.34                                       | Pairwise pocket separation                               |
| 45 | pocket_p_apo                   | 0.821                                      | Probability of apo state                                 |
| 46 | pocket_p_holo                  | 0.179                                      | Probability of holo state                                |
| 47 | pocket_score                   |                                             | Pocket score (may be empty)                              |
| 48 | model_pocket_plddt             | 97.54                                      | AlphaFold pLDDT score at the pocket                      |
| 49 | n_apo_avg                      | 10.0                                       | Average number of apo observations                       |
| 50 | n_holo_avg                     | 2.0                                        | Average number of holo observations                      |
| 51 | af_model_len                   | 109.0                                      | AlphaFold model sequence length                          |
| 52 | afdb_filename                  | AF-A0A009I821-F1-model_v6.cif.gz           | AlphaFold DB model filename (lookup key)                 |
| 53 | chain_resi                     | A_85 A_90 A_91 A_92 A_93 A_94 A_95        | Space-separated residue IDs                              |
| 54 | center_x                       | 10.83                                      | Site centroid X coordinate                               |
| 55 | center_y                       | -2.461                                     | Site centroid Y coordinate                               |
| 56 | center_z                       | -29.63                                     | Site centroid Z coordinate                               |
| 57 | rg                             | 7.354                                      | Radius of gyration of the site                           |
| 58 | n_atoms                        | 56.0                                       | Number of atoms in the site                              |
| 59 | n_residues_found               | 7.0                                        | Number of residues resolved in the model                 |

## Residue ID format

Residue IDs follow the pattern `chain_seqNum[insCode]`, e.g.:
- `A_85` — chain A, residue 85
- `A_D160A` — chain A, amino acid D, residue 160, insertion code A

Parsed by `ExtendedResidueId.parse()`.
