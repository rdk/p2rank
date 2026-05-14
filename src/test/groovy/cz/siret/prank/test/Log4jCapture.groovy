package cz.siret.prank.test

import groovy.transform.CompileStatic
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

/**
 * Test-only helper that captures log4j2 events from a target class's logger.
 *
 * Usage:
 * <pre>
 *   def capture = Log4jCapture.attach(MyClass)
 *   try {
 *       // act
 *       assertTrue capture.warns().any { it.contains("expected text") }
 *   } finally {
 *       capture.detach()
 *   }
 * </pre>
 */
@CompileStatic
class Log4jCapture {

    private final Logger logger
    private final CapturingAppender appender
    private final Level previousLevel

    private Log4jCapture(Logger logger, CapturingAppender appender, Level previousLevel) {
        this.logger = logger
        this.appender = appender
        this.previousLevel = previousLevel
    }

    static Log4jCapture attach(Class<?> targetClass) {
        Logger lg = (Logger) LogManager.getLogger(targetClass)
        Level prev = lg.level
        if (prev == null || prev.intLevel() < Level.WARN.intLevel()) {
            // intLevel() is smaller for more verbose levels - we want WARN or finer to be captured.
        }
        CapturingAppender appender = new CapturingAppender("Capture-" + targetClass.simpleName)
        appender.start()
        lg.addAppender(appender)
        // Ensure WARN+ propagates regardless of root config.
        lg.setLevel(Level.DEBUG)
        return new Log4jCapture(lg, appender, prev)
    }

    void detach() {
        logger.removeAppender(appender)
        logger.setLevel(previousLevel)
        appender.stop()
    }

    List<String> messages() {
        return appender.events.collect { it.message.formattedMessage }
    }

    List<String> warns() {
        return appender.events.findAll { it.level == Level.WARN }
                .collect { it.message.formattedMessage }
    }

    List<String> infos() {
        return appender.events.findAll { it.level == Level.INFO }
                .collect { it.message.formattedMessage }
    }

    @CompileStatic
    private static class CapturingAppender extends AbstractAppender {
        final List<LogEvent> events = Collections.synchronizedList(new ArrayList<LogEvent>())

        CapturingAppender(String name) {
            super(name, null, null, false, Property.EMPTY_ARRAY)
        }

        @Override
        void append(LogEvent event) {
            events.add(event.toImmutable())
        }
    }
}
