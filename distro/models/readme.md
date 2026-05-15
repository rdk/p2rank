
# Directory with pre-trained models

Prank looks here for the model specified by the `-model` / `-m` parameter.

The model should always be used only in combination with the parameters or config file that was used to train it.
I.e.: the feature extraction has to be executed with the same parameters.

## List of models

### P2Rank (pocket prediction)

* `default`                     ... config `default.groovy`
* `conservation_hmm`            ... config `conservation_hmm.groovy` (uses HMMER-based sequence conservation)
* `alphafold`                   ... config `alphafold.groovy` (tuned for AlphaFold / NMR / cryo-EM structures, no b-factor)
* `alphafold_conservation_hmm`  ... config `alphafold_conservation_hmm.groovy`

### PRANK (pocket rescoring)

* `default_rescore`             ... config `default_rescore.groovy`
* `rescore_2024`                ... config `rescore_2024.groovy` (newer model, recommended for AlphaFold / NMR / cryo-EM)
* `rescore_conservation`        ... config `rescore_conservation.groovy` (rescoring with conservation features)

## Auxiliary

* `_score_transform/` — score-transformer JSON files (probability calibration, z-score) loaded by the models above.
