package cz.siret.prank.program.params.optimizer

import groovy.transform.CompileStatic

/**
 * Definition of variable and constraints to be optimized.
 */
@CompileStatic
class HVariable {

    static enum Type { FLOAT, INT }

    String name
    Type type
    Number min
    Number max

    HVariable(String name, Type type, Number min, Number max) {
        this.name = name
        this.type = type
        this.min = min
        this.max = max
    }

    @Override
    String toString() {
        return "HVariable{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", min=" + min +
                ", max=" + max +
                '}'
    }

}
