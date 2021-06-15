package cz.siret.prank.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.Logger

/**
 * console&log writer
 */
@Slf4j
@CompileStatic
trait Writable {

    public void write(String msg, Logger log) {
        Console.write(msg, log)
    }

    public void write(String msg) {
        Console.write(msg)
    }

    public void writeError(String msg, Throwable t) {
        Console.writeError(msg, t)
    }

    public void writeError(String msg) {
        Console.writeError(msg, null)
    }

}
