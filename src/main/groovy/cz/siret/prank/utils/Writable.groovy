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
        ConsoleWriter.write(msg, log)
    }

    public void write(String msg) {
        ConsoleWriter.write(msg)
    }

    public void writeError(String msg, Throwable t) {
        ConsoleWriter.writeError(msg, t)
    }

    public void writeError(String msg) {
        ConsoleWriter.writeError(msg, null)
    }

}
