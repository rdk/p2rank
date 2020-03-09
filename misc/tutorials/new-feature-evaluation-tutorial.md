# Implementing and evaluating new feature

Read this if you want to implement a new feature and evaluate if it contributes to prediction success rates.

## Implementation

New features can be added by implementing `FeatureCalculator` interface and registering the implementation in `FeatureRegistry`.
You can implement the feature by extending one of convenience abstract classes `AtomFeatureCalculator` or `SasFeatureCalculator`.

You need to decide if the new feature will be associated with protein surface (i.e. solvent exposed) atoms or with SAS (Solvent Accessible Surface) points. 
P2Rank works by classifying SAS point feature vectors. 
If you associate the feature with atoms its value will be projected to SAS point feature vectors by P2Rank from neighbouring atoms.

Some features are more easily defined for atoms than SAS points and other way around. See `BfactorFeature` and `ProtrusionFeature` for comparison.


## Evaluation

 1. Prepare the environment (see _Preparing the environment_ in `training-tutorial.md`)

 2. Check `working.groovy` config file. It contains configuration ideal for training new models, but you might need to make changes or override some params on the command line. 
 
 3. Train with the new feature
    * train with the new feature by adding its name to the list of `-extra_features`. i.e.:
        - in the groovy config file: `extra_features = ["protrusion","bfactor","new_feature"]`
        - on the command line: `-extra_features '(protrusion.bfactor.new_feature)'` (dot is used as separator)
    * if the feature has arbitrary parameters, they can be optimized with `prank ploop` or `prank hopt` commands
        - see hyperparameter-optimization-tutorial.md    
    * you can even compare different feature sets running `prank ploop ...`. i.e.:
        - `-extra_features '((protrusion),(new_feature),(protrusion.new_feature))'`
        
        
### Case study        

TODO