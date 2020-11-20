
Directory with pre-trained models.

Prank looks here for the model specified by (`-model`/`-m`) parameter.

The model should be always used only in combination with the parameters or config file that was used to train it.
I.e.: the feature extraction has to be executed with the same parameters.

## List of models

* `p2rank_a.model`      ... for P2Rank (predictions), trained on bench-fpocket.ds dataset using config file `default.groovy`
* `conservation.model`  ... for P2Rank (predictions), trained on bench-fpocket.ds dataset using config file `conservation.groovy`
* `prank.model`         ... for PRANK  (rescoring),   trained on bench-fpocket.ds dataset using config file `default-rescore.groovy`

