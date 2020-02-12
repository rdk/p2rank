package cz.siret.prank.utils

import com.google.common.base.Throwables
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.Logger

@Slf4j
@CompileStatic
public class ConsoleWriter {

    public static void write(String msg, Logger log) {
        System.out.println msg
        log.info msg
    }

    public static void write(String msg) {
        System.out.println msg
        log.info msg
    }

    public static void writeln(String msg) {
        write(msg + "\n")
    }

    public static void writeln() {
        write("\n")
    }


    public static void writeError(String msg, Throwable t) {
        String stack = t==null ? "" : "\n" + stackTrace(t)

        System.out.println "ERROR: " + msg + stack
        log.error msg, t
    }

    private static String stackTrace(Throwable t) {
        return Throwables.getStackTraceAsString(t);
    }

}
