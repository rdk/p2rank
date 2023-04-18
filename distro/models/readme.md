
# Directory with pre-trained models

Prank looks here for the model specified by (`-model`/`-m`) parameter.

The model should be always used only in combination with the parameters or config file that was used to train it.
I.e.: the feature extraction has to be executed with the same parameters.

## List of models

### P2Rank (pocket prediction)

* `default.model`      ... trained on `chen11-fpocket.ds` using config `default.groovy`
* `conservation.model`  ... trained on `chen11-fpocket.ds` using config `conservation.groovy`
* `conservation_hmm.model`  ... trained on `chen11-fpocket.ds` using config `conservation_hmm.groovy`
* `alphafold.model`  ... trained on `chen11-fpocket.ds` using config `alphafold.groovy`
* `alphafold_conservation_hmm.model`  ... trained on `chen11-fpocket.ds` using config `alphafold_conservation_hmm.groovy`

### PRANK (pocket rescoring)

* `default_rescore.model` ... trained on chen11-fpocket.ds using config `default_rescore.groovy`

