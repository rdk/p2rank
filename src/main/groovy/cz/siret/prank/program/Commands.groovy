package cz.siret.prank.program

import groovy.transform.CompileStatic

/**
 * not used yet
 */
@CompileStatic
class Commands {

    List<Command> commands
    Map<String, Command> index

    static class Command {
        String name
        Closure function
        String shortDescription

        Command(String name, Closure function, String shortDescription) {
            this.name = name
            this.function = function
            this.shortDescription = shortDescription
        }
    }

}
