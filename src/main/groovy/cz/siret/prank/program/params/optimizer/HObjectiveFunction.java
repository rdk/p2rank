package cz.siret.prank.program.params.optimizer;

import groovy.transform.CompileStatic;

import java.util.Map;

/**
 *
 */
@CompileStatic
public interface HObjectiveFunction {

    double eval(Map<String, Object> variableValues, int stepNumber);

}
