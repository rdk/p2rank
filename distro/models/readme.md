
Directory with pre-trained classiers.
Prank looks here for model specified by (-model/-m) parameter.

Model should be always used only in combination with the parameters or config file that was used to train it.
I.e., the feature extraction has to be executed with the same parameters.

## List of models

conservation.model  ... for P2Rank (predictions), trained on bench-fpocket.ds dataset with config conservation.groovy
p2rank_a.model      ... for P2Rank (predictions), trained on bench-fpocket.ds dataset with config default.groovy
prank.model         ... for PRANK  (rescoring),   trained on bench-fpocket.ds dataset with config default-rescore.groovy

