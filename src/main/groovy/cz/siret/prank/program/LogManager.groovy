package cz.siret.prank.program

import com.sun.istack.Nullable
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.config.AppenderRef
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.layout.PatternLayout
import org.slf4j.bridge.SLF4JBridgeHandler

/**
 *
 */
@CompileStatic
class LogManager implements Writable {

    static final String LOGGER_NAME = "cz.siret.prank"
    static final String CONSOLE_APPENDER_NAME = "Console"
    static final String FILE_APPENDER_NAME = "File"
    static final String PATTERN = "[%level] %logger{0} - %msg%n"

    boolean loggingToFile = false
    String logFile

    Appender fileAppender
    Configuration config
    LoggerConfig loggerConfig

    void configureLoggers(String logLevel, boolean logToConsole, boolean logToFile, @Nullable String outdir) {

        String loggerName = LOGGER_NAME
        Level level = Level.getLevel(logLevel)

        LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        config = ctx.getConfiguration();
        loggerConfig = config.getLoggerConfig(loggerName)

        // loggerConfig.getAppenders().each { System.out.println "APPENDER: " + it.value.name }
        //log.debug "logToConsole: $logToConsole"
        //log.debug "logToFile: $logToFile"
        //log.debug "logLevel: ${level.name()}"

        loggerConfig.setLevel(level)

        if (logToFile && outdir!=null) {
            logFile = "$outdir/run.log"
            fileAppender = addFileAppender(config, loggerName, logFile, level)
            loggingToFile = true
        }
        if (!logToConsole) {
//            config.getAppender(CONSOLE_APPENDER_NAME).stop()
//            loggerConfig.removeAppender(CONSOLE_APPENDER_NAME)
//            config.rootLogger.removeAppender(CONSOLE_APPENDER_NAME)
//
//            if (!logToFile) {
//                config.removeLogger(loggerName)
//            }
            loggerConfig.setLevel(Level.ERROR)  // always log at least errors to console
        }

        ctx.updateLoggers();

        // netlib uses java.util.logging - bridge to slf4
        // compare https://github.com/fommil/netlib-java/blob/master/perf/logging.properties
        // note: this seems to disable all netlib logging
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()

        //loggerConfig.getAppenders().each { System.out.println "APPENDER: " + it.value.name }
    }

    private static Appender addFileAppender(Configuration config, String loggerName, String logFile, Level level) {

        String pattern = PATTERN
        int bufferSize = 5000

        Futils.delete(logFile)

        Layout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern(pattern)
                .build()

        Appender appender = FileAppender.createAppender(
                logFile,               // fileName,
                "false",            // append,
                "false",            // locking,
                "File",             // name,
                "false",            // immediateFlush
                "false",            // ignore,
                "true",             // bufferedIo,
                "" + bufferSize,            // bufferSizeStr,
                layout,             // layout
                null,               // filter,
                "false",            // advertise,
                null,               // advertiseUri,
                config
        );
        appender.start();
        config.addAppender(appender);

        AppenderRef ref = AppenderRef.createAppenderRef(FILE_APPENDER_NAME, null, null);
        AppenderRef[] refs =  [ref] as AppenderRef[];

        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName)
        loggerConfig.addAppender(appender, level, null);

        return appender
    }

    void stopFileAppender() {
        if (fileAppender!=null) {
            fileAppender.stop()
        }
        loggerConfig.removeAppender(FILE_APPENDER_NAME)
        config.rootLogger.removeAppender(FILE_APPENDER_NAME)
    }

}
