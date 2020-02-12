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

    static void write(String msg, Logger log) {
        ConsoleWriter.write(msg, log)
    }

    static void write(String msg) {
        ConsoleWriter.write(msg)
    }

    static void writeln(String msg) {
        ConsoleWriter.writeln(msg)
    }

    static void writeln() {
        ConsoleWriter.writeln()
    }

    static void writeError(String msg, Throwable t) {
        ConsoleWriter.writeError(msg, t)
    }

    static void writeError(String msg) {
        ConsoleWriter.writeError(msg, null)
    }

}
