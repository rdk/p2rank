# Training residue and pocket score transformers

As part of prediction output, P2Rank generates raw scores from the interval [0,∞) for all pockets and residues.
The raw score is a value in `score` column in `*_pockets.csv` and `*_residues.csv`.
Additionally, those files contain columns `probability` and `zscore` which contain raw scores transformed by pre-trained transformers.

* `probability`: raw score [0,∞) ->  [0,1]
* `zscore`: raw score [0,∞) -> (-∞,∞)

These transformers are model/parametrization dependent and need to be trained/calibrated for each model on some calibration dataset.
HOLO4K was used for this purpose for the default P2Rank pocket prediction model.


To train transformers for a new model defined in `newmodel.groovy` execute the following command:
~~~sh
./prank.sh eval-predict -c newmodel.groovy ../p2rank-datasets/holo4k.ds \
    -visualizations 0 \
    -train_score_transformers '(ProbabilityScoreTransformer,ZscoreTpTransformer)' \
    -train_score_transformers_for_residues 1
~~~
Output: Parameters of the trained transformers will be stored in `score` and `residue-score` subdirectories of the output directory.

After transformers are trained, they should be adequately renamed to reflect an association with the new model and configured in `newmodel.groovy`:
~~~groovy
/**
 * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of true pockets" (pocket.auxInfo.zScoreTP).
 * Use path relative to distro/models/score.
 */
zscoretp_transformer = "newmodel_zscoretp.json"

/**
 * Path to a JSON file that contains parameters of a transformer from raw score to "probability that pocket with a given score is true pocket" (pocket.auxInfo.probaTP).
 * Use path relative to distro/models/score.
 */
probatp_transformer = "newmodel_probatp.json"

/**
 * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of all residue scores".
 * Use path relative to distro/models/score.
 */
zscoretp_res_transformer = "residue/newmodel_zscore.json"

/**
 * Path to a JSON file that contains parameters of a transformer from raw score to "probability that residue with a given score is true (binding) residue".
 * Use path relative to distro/models/score.
 */
probatp_res_transformer = "residue/newmodel_proba.json"
~~~
