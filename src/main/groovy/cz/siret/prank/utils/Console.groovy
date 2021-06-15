package cz.siret.prank.utils

import com.google.common.base.Throwables
import cz.siret.prank.program.Main
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.Logger

/**
 * Console Writer: methods to write to stdout & log
 */
@Slf4j
@CompileStatic
public class Console {

    public static void write(String msg, Logger log) {
        System.out.println applyTimestamp(msg)
        log.info msg
    }

    public static void write(String msg) {
        System.out.println applyTimestamp(msg)
        log.info msg
    }

    public static void writeError(String msg, Throwable t) {
        String stack = t==null ? "" : "\n" + stackTrace(t)

        System.out.println applyTimestamp("ERROR: " + msg) + stack
        log.error msg, t
    }

    private static String stackTrace(Throwable t) {
        return Throwables.getStackTraceAsString(t)
    }

    private static String applyTimestamp(String msg) {
        if (!Main._do_stdout_timestamp) {
            return msg
        } else {
            String timestamp = Main._timestamp_format.format(new Date())
            return Sutils.prefixLines(timestamp + " ", msg)
        }
    }

}
