package cz.siret.prank.program.params.optimizer;

import java.util.Map;

/**
 *
 */
interface HObjectiveFunction {

    double eval(Map<String, Object> variableValues, int stepNumber);

}
