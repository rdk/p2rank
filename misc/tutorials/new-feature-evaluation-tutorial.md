# Implementing and evaluating new feature

Read this if you want to implement a new feature and evaluate if it contributes to prediction success rates.

## Implementation

New features can be added by implementing `FeatureCalculator` interface and registering the implementation in `FeatureRegistry`.
You can implement the feature by extending one of the convenience abstract classes `AtomFeatureCalculator` or `SasFeatureCalculator`.

You need to decide if the new feature will be associated with protein surface (i.e. solvent exposed) atoms or with SAS (Solvent Accessible Surface) points. 
P2Rank works by classifying SAS point feature vectors. 
If you associate the feature with atoms its value will be projected to SAS point feature vectors by P2Rank from neighbouring atoms.

Some features are more naturally defined for atoms rather than for SAS points and other way around. See `BfactorFeature` and `ProtrusionFeature` for comparison.


## General evaluation

 1. Prepare the environment (see _Preparing the environment_ in `training-tutorial.md`)

 2. Check `config/train-new-default.groovy` config file. It contains configuration ideal for training new models, but you might need to make changes or override some params on the command line. 
 
 3. Train with the new feature
    * train with the new feature by adding its name to the list of `-features`. e.g.:
        - in the groovy config file: `features = ["protrusion","bfactor","new_feature"]`
        - on the command line: `-features '(protrusion,bfactor,new_feature)'` 
    * if the feature has arbitrary parameters, they can be optimized with `prank ploop` or `prank hopt` commands
        - see the [hyperparameter optimization tutorial](hyperparameter-optimization-tutorial.md) 
    * you can even compare different feature sets running `prank ploop ...`. e.g.:
        - `-features '((protrusion),(new_feature),(protrusion,new_feature))'`
          

Note: due to the variance in trained classifiers it is important to consider average results of several runs (by using e.g `-loop 10`) when comparing features.


## Feature importances

See 'Feature importances' section in [training tutorial](training-tutorial.md).  


## Evaluating new feature 

_(this section is work in progress)_

How to evaluate wheather the new feature is useful?
Is it helping to predict more binding sites, and/or more precisely predict their shape? 
                                                   
This question is more complicated that it may seem. 
                                   
* Short answer: If it improves `point_AUPRC`, it is discriminative, and it has a potential to help P2Rank to make better predictions.
* Slightly longer answer: Better way to compare models is using DCA metrics (`DCA_4_0`,`DCA_4_2`...) in combination with some metric 
  that takes into account pocket shapes, like `LIGAND_COVERAGE` metric. 
* More complete answer: Even if `point_AUPRC` is improved, DCA and other pocket matrics may stay roughly the same, or even get worse. 
  The reason is that adding new feature can substantially change the distribution of predicted SAS point scores.
  DCA and other pocket metrics then depend on some parameters that were optimized on a diffrent score distribution.
  To get a meaningful comparison of DCA metrics, it is necessary to perform oprimization of at least some basic parameters (`pred_point_threshold`, `point_score_pow`).
             
      
Details and a case study follows.           

### Introduction to metrics

* pocket prediction metrics
    - `DCA_4_0`
    - `DCA_4_2`
    - `LIGAND_COVERAGE` - what % of ligands (in terms of volume) is covered by positively predicted SAS points. 
* point metrics 
    - `point_AUPRC`
    - `point_AUC`
    - `point_MCC`  
* resdue metrics
    - `residue_AUPRC`
    - `residue_AUC`
    - `residue_MCC`


There are 3 related but distinct problems that should be distinguished:
1. Problem of predicting ligand biding sites.
2. Binary classification problem of predicting ligandablity of SAS points.
3. Binary classification problem of predicting binding residues.

We are mainly focused on 1., while considering 3. just a poor way of looking at and evaluating binding site prediction methods.
(It was used mainly for algorithms that predict just from sequence because it is the most natural -- or rather, the easiest.)
Considering 2. can give a useful hints while training and optimizing new model to be better at 1.

Note: residue metrics are available only when P2Rank is executed in residue mode (`-predict_residues 1`). 
Default P2Rank models are not optimized for residue metrics.

Why are residue metrics inadequate? See the discussion on 'Residue-centric versus pocket-centric perspective' 
in the [paper](https://doi.org/10.1186/s13321-018-0285-8).

### Case study - conservation feature


~~~bash
./prank.sh traineval -c config/train-default -out_subdir CASE -label DEFA \
    -t chen11-fpocket.ds -e joined.ds \
    -loop 10 

./prank.sh traineval -c config/train-conservation -out_subdir CASE -label CONS \
    -t chen11-fpocket.ds -e joined.ds \
    -conservation_dirs '(chen11/conservation/e5i1/scores,joined/conservation/e5i1/scores)' \
    -loop 10 
~~~

