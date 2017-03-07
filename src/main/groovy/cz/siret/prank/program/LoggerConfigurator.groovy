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
class LoggerConfigurator implements Writable {

    static void configureLoggers(Main main, String logLevel, boolean logToConsole, boolean logToFile, String outdir) {

        String loggerName = "cz.siret.prank"
        Level level = Level.getLevel(logLevel)

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName)

        loggerConfig.getAppenders().each { System.out.println "APPENDER: " + it.value.name }
        write "logToConsole: $logToConsole"
        write "logToFile: $logToFile"

        loggerConfig.setLevel(level)

        if (logToFile) {
            main.logFile = addFileAppender(config, loggerName, outdir, level)
            main.loggingToFile = true
        }
        if (!logToConsole) {
            config.getAppender("Console").stop()
            loggerConfig.removeAppender("Console")
            config.rootLogger.removeAppender("Console")

            if (!logToFile) {
                config.removeLogger(loggerName)
            }
        }

        ctx.updateLoggers();

        loggerConfig.getAppenders().each { System.out.println "APPENDER: " + it.value.name }
    }

    private static String addFileAppender(Configuration config, String loggerName, String outdir, Level level) {
        String file = "$outdir/run.log"
        String pattern = "[%level] %logger{0} - %msg%n"
        int bufferSize = 5000

        futils.delete(file)

        Layout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern(pattern)
                .build()

        Appender appender = FileAppender.createAppender(
                file,               // fileName,
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

        AppenderRef ref = AppenderRef.createAppenderRef("File", null, null);
        AppenderRef[] refs = (AppenderRef[]) [ref].toArray();

        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName)
        loggerConfig.addAppender(appender, level, null);

        return file
    }


}
