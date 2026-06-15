
## Breaking changes

### Introduction

This file collects backwards incompatible changes that have potential to break code that uses P2Rank.

These include:

* changes in the command line interface 
* changes in the input/output format
* changes in default behaviour

All changes of that type should be rare and should be all listed here.

## List of changes

### 2.6

###### Evaluation

* Ligand detection was fixed (`e7fc457f`) to include nucleotide ligands (GDP, GTP, ATP — classified by BioJava as `NUCLEOTIDE`)
  and amino-acid-derivative ligands (SHR-like — classified as `AMINOACID`) that were previously skipped because only
  `GroupType.HETATM` qualified. Any non-water group in a NONPOLYMER chain now qualifies regardless of GroupType.
  This changes the relevant-ligand set on datasets containing such ligands, which moves DCA/DCC numerator and denominator
  and thus the reported success rates.
* For additional internal evaluation-criterion fixes during the 2.6 dev cycle see
  [`documentation/dev/evaluation-metric-fixes-2.6.md`](documentation/dev/evaluation-metric-fixes-2.6.md).

###### Model / config compatibility check

* Prediction and rescoring now validate that the loaded model's stored feature header (`features.txt`) matches the
  feature header produced by the current configuration. On mismatch the run fails fast with an actionable error
  (expected vs actual features) instead of silently producing incorrect predictions. This is controlled by the new
  `fail_on_model_feature_mismatch` parameter (default `true`); set `-fail_on_model_feature_mismatch 0` to downgrade
  the failure to a warning. Legacy models without a stored header (v1/v2 files, or v3 directories lacking
  `features.txt`) are not affected. All bundled models match their shipped configs, so default usage is unaffected.

###### Command / config purpose check

* Prediction and rescoring commands now validate that the chosen config's declared purpose
  (`config_purpose`, set to `prediction` or `rescoring` in the shipped configs) matches the command.
  A mismatch (e.g. `prank rescore -c alphafold`, or a rescoring config used with `prank predict`)
  fails fast with an actionable error. This is controlled by the new `fail_on_wrong_config`
  parameter (default `true`); set `-fail_on_wrong_config 0` to downgrade to a warning. Configs
  without `config_purpose` (custom configs, dev/training configs) are unrestricted, so they are unaffected.

###### Pocket-descriptors export (opt-in feature)

* Per-pocket descriptors `-export_pocket_descriptors` underwent a multi-column interface migration. The built-in default
  list now contains **ten** descriptors (previously six), adds `principal_moments` (a 3-column descriptor emitting
  `principal_moments.lambda1/lambda2/lambda3`) and three electrostatic descriptors
  (`pocket_net_charge`, `pocket_charge_polarity` with 3 sub-columns positive/negative/ratio, and
  `pocket_dipole_magnitude`), and reorders the existing six so `num_*` come first.
  Scripts parsing the descriptors CSV/Arrow/Parquet output by **column name** are unaffected;
  scripts parsing by **column index** need updating. See [`documentation/export-pocket-descriptors.md`](documentation/export-pocket-descriptors.md).

###### Pocket-grid per-point descriptors (opt-in feature)

* The `-pocket_grid_point_descriptors` default was previously empty (no per-point columns appended to the grid
  CSV). It now contains **all three** registered per-grid-point descriptors:
  `volsite` (6 cols), `volsite_smooth` (6 cols), `electrostatics` (5 cols) — 17 extra columns per (point, pocket) row
  when `-export_pocket_grid 1`. To restore the prior bare x/y/z/pocket schema, pass
  `-pocket_grid_point_descriptors ''`. See [`documentation/export-pocket-grid.md`](documentation/export-pocket-grid.md).
* New opt-in `-vis_pocket_grid` (renamed from `-export_pocket_grid_pml`) emits both PyMOL `.pml` and ChimeraX `.cxc`
  overlay scripts. The two viz-tuning knobs were renamed for namespace consistency:
  `pocket_grid_vis_volume_radius` → `vis_pocket_grid_volume_radius` and
  `pocket_grid_vis_gaussian_iso` → `vis_pocket_grid_gaussian_iso`. Old names hard-fail at startup with no aliases.

###### Feature value changes (retraining required)

Several feature calculators changed their numeric output (or width) during the
2.6 dev cycle. The model/feature-header check above catches a width change at
load time, but value-only changes are silent: a model trained on 2.5.1 feature
vectors sees shifted inputs at prediction time. Retrain models that use these
features (the listed research configs).

* `duplets` (sequence duplet propensity) widened from a single `product` column
  to three (`avg`, `max`, `product`). Models using `duplets_sas`/`duplets_atomic`
  (e.g. config/ions, config/pept) must be retrained.
* `cres` (contact-residue) contact-atom distance changed from 3.3 to 3.5 A
  (`ContactResiduesRF.CONTACT_ATOM_DIST`), shifting `cres` values for any model
  using it (config/dna, config/ions, config/pept).
* `cr1pos.CAmCB` now reflects the true `dca - dcb` for every residue. Previously
  a value-ordering bug subtracted `dcb` before it was assigned (so `dcb` was 0),
  making `CAmCB` equal `dca` (the CA distance) in the common case. Models trained
  on the old `cr1pos` must be retrained.
* `asa` (solvent-accessible surface) now includes cofactor HETATM atoms when
  `-cofactors` is enabled (switched from `getAllNonHAtomArray(hetAtoms=false)` to
  the protein heavy-atom set). Only affects runs using cofactor-as-surface.

###### Alternate-conformation chain reduction (default on)

* Structures that deposit microheterogeneity as separate superimposed whole chains, each tagged with a single
  non-blank altLoc letter (e.g. PDB `6een`, chains A/B/C/D being the same polymer ~0.002 A apart), now have the
  redundant alternate chains dropped at load time, keeping only the primary conformation. Controlled by the new
  `reduce_alternate_conformation_chains` parameter (default `true`); set `-reduce_alternate_conformation_chains 0`
  for legacy behaviour (all alternate chains loaded). This changes the surface, features, and predictions for such
  structures (the previous output was computed on ~Nx-overlapping atoms and was inflated). Ordinary within-residue
  altLocs are already collapsed by the parser and are unaffected. The pattern is rare (~3/10000 structures on a
  PDB-wide sample, 1 of them materially), so aggregate benchmark metrics are unchanged within noise. See
  `cz.siret.prank.geom.AlternateChainReducer`.

### 2.5.1

none

### 2.5

none

### 2.4.2

none

### 2.4.1

###### Prediction

* Scripts that execute P2Rank (shell script `distro/prank` and `distro/prank.bat`) no longer redirect log (***stderr*** stream) to the file `distro/log/prank.log`. 
  Instead, they write ***stderr*** to the console. This was done to avoid P2Rank writing to the installation directory by default, which may be forbidden on some systems.
  See issue #59.

###### Training new models

* Type of parameter `-ignore_het_groups` changed from `Set<String>` to `List<String>`
     


### 2.4

###### Prediction

none

###### Training new models

* Removed deprecated parameters `-conservation_origin` and `-load_conservation_paths` 

### 2.3

###### Prediction

none

###### Training new models

* parameter `-extra_features` was renamed to `-features` 
* command line format of parameters values with type `List<String>` and `List<List<String>>` has changed
    * now only comas `,` are delimeters and inner parentheses are respected 
    * before `.` was used as an alternative delimeter and delimeter for inner lists, now it is part of element value
    * Examples: 
        * `'(a.b.c)'` was interpreted as list of 3 elements, now it defines list of 1 element: `a.b.c`
        * list of lists value `'((a.b.c),(d.e))'` should be changed to `'((a,b,c),(d,e))'`
* Changes in `csv_file_feature`
    * renamed to `csv`
    * introduced parameter `-feat_csv_columns` (type: `List<String>`). 
        Names of enabled value columns from csv files must be listed here. 
        Columns not listed are ignored.
        * Example: if you were working with one directory of csv files with one value column named `pdbekb_conservation`, 
        you must now run the program with `-feat_csv_columns '(pdbekb_conservation)'` 
    * introduced parameter `-feat_csv_ignore_missing` (type: `boolean`, default: `false`). If true, then feature ignores:
        * missing csv files for proteins
        * missing value columns
        * missing rows for atoms and residues
      
    

### 2.2

* parameter `-conservation_dir` (type: `String`) was renamed to `-conservation_dirs` (type: `List<String>`)
* column `probability` was added to `*_predictions.csv` output file
