package cz.siret.prank.program.params

/**
 * Marks parameters of the prediction model (including feature extraction params) i.e. algorithm params.
 * The notion of "model" here is seen wholesomely as a pocket prediction model = the whole algorithm, which includes feature extraction, classification and aggregation to binding sites.
 *
 * These are the parameters that must be the same in training and prediction phase.
 *
 * Currently annotation serves only documentation purposes.
 */
@interface ModelParam {

}