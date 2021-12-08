
# Training residue and pocket score transformers


As part of prediction output, P2Rank generates a raw scores from interval [0,∞) for all pockets and residues. 
Raw score is a value in `score` column in `*_pockets.csv` and `*_residues.csv`.
Additionaly, those files contain columns `probability` and `zscore` which contain raw score transformed by pre-trained transformers.
     
* `probability`: raw score [0,∞) ->  [0,1]
* `zscore`: raw score [0,∞) -> (-∞,∞)

These transformers are model/parametrization dependent and need to be trained/calibrated for each model on some calibration dataset. 
HOLO4K was used for this purpose for default P2Rank pocket prediction model. 


To train transformers for a new model defined in `newmodel.groovy` execute followiing command:
~~~sh
./prank.sh eval-predict -c new_config.groovy ../p2rank-datasets/holo4k.ds \
    -visualizations 0 \
    -train_score_transformers '(ProbabilityScoreTransformer,ZscoreTpTransformer)' \
    -train_score_transformers_for_residues 1
~~~
Output: Parameters of the trained transfrmers will be in `score` and `residue-score`subdirectories of the output directory.

After transformers are trained, ther should be adequately renamed to reflect association with the new model and configured in `newmodel.groovy`:
~~~groovy
    /**
     * Path to json file that contains parameters of transformation of raw score to "z-score calculated from distribution of true pockets" (pocket.auxInfo.zScoreTP).
     * Use path relative to distro/models/score.
     */
    zscoretp_transformer = "newmodel_zscoretp.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "probability that pocket with given score is true pocket" (pocket.auxInfo.probaTP).
     * Use path relative to distro/models/score.
     */
    probatp_transformer = "newmodel_probatp.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "z-score calculated from distribution of all residue scores".
     * Use path relative to distro/models/score.
     */
    zscoretp_res_transformer = "residue/newmodel_zscore.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "probability that residue with given score is true (binding) residue".
     * Use path relative to distro/models/score.
     */
    probatp_res_transformer = "residue/newmodel_proba.json"
~~~