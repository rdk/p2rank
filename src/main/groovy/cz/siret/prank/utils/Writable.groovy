package cz.siret.prank.utils

import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.Logger

/**
 * console&log writer
 */
@Slf4j
@CompileStatic
trait Writable {

    void write(String msg, Logger log) {
        if (Params.inst.writeToStdOut()) {
            Console.write(msg, log)
        } else {
            log.info msg
        }
    }

    void write(String msg) {
        if (Params.inst.writeToStdOut()) {
            Console.write(msg, log)
        } else {
            log.info msg
        }
    }

    void writeError(String msg, Throwable t) {
        Console.writeError(msg, t)
    }

    void writeError(String msg) {
        Console.writeError(msg, null)
    }

}
