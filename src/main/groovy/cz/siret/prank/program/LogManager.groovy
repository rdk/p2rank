package cz.siret.prank.program

import cz.siret.prank.utils.Writable
import cz.siret.prank.utils.futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.config.AppenderRef
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.layout.PatternLayout

/**
 *
 */
@CompileStatic
class LogManager implements Writable {

    static final String LOGGER_NAME = "cz.siret.prank"
    static final String CONSOLE_APPENDER_NAME = "Console"
    static final String FILE_APPENDER_NAME = "File"

    boolean loggingToFile = false
    String logFile


    Appender fileAppender
    Configuration config
    LoggerConfig loggerConfig

    void configureLoggers(String logLevel, boolean logToConsole, boolean logToFile, String outdir) {

        String loggerName = LOGGER_NAME
        Level level = Level.getLevel(logLevel)

        LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        config = ctx.getConfiguration();
        loggerConfig = config.getLoggerConfig(loggerName)

        loggerConfig.getAppenders().each { System.out.println "APPENDER: " + it.value.name }
        write "logToConsole: $logToConsole"
        write "logToFile: $logToFile"

        loggerConfig.setLevel(level)

        if (logToFile) {
            logFile = "$outdir/run.log"
            fileAppender = addFileAppender(config, loggerName, logFile, level)
            loggingToFile = true
        }
        if (!logToConsole) {
            config.getAppender(CONSOLE_APPENDER_NAME).stop()
            loggerConfig.removeAppender(CONSOLE_APPENDER_NAME)
            config.rootLogger.removeAppender(CONSOLE_APPENDER_NAME)

            if (!logToFile) {
                config.removeLogger(loggerName)
            }
        }

        ctx.updateLoggers();

        loggerConfig.getAppenders().each { System.out.println "APPENDER: " + it.value.name }
    }

    private static Appender addFileAppender(Configuration config, String loggerName, String logFile, Level level) {

        String pattern = "[%level] %logger{0} - %msg%n"
        int bufferSize = 5000

        futils.delete(logFile)

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
        AppenderRef[] refs = (AppenderRef[]) [ref].toArray();

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
