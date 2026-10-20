package io.github.sayamrina.solutiondesk.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/** Logback appender that copies application events into {@link LogBuffer}. */
public class BufferAppender extends AppenderBase<ILoggingEvent> {

    public static final String SIMULATED_AT = "simulatedAt";

    @Override
    protected void append(ILoggingEvent event) {
        Map<String, String> fields = new TreeMap<>(event.getMDCPropertyMap());
        // the demo's sample traffic sets the time at which a simulated request happened
        String simulatedAt = fields.remove(SIMULATED_AT);
        Instant at = simulatedAt == null ? Instant.ofEpochMilli(event.getTimeStamp()) : Instant.ofEpochMilli(Long.parseLong(simulatedAt));
        String exception = "";
        String stackTop = "";
        IThrowableProxy t = event.getThrowableProxy();
        if (t != null) {
            exception = t.getClassName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            StackTraceElementProxy[] frames = t.getStackTraceElementProxyArray();
            for (StackTraceElementProxy frame : frames) {
                if (frame.getStackTraceElement().getClassName().startsWith("io.github.sayamrina")) {
                    stackTop = frame.getSTEAsString();
                    break;
                }
            }
        }
        LogBuffer.add(new LogEvent(at, event.getLevel().toString(),
                event.getLoggerName(), event.getFormattedMessage(), fields, exception, stackTop));
    }
}
