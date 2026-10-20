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

    @Override
    protected void append(ILoggingEvent event) {
        Map<String, String> fields = new TreeMap<>(event.getMDCPropertyMap());
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
        LogBuffer.add(new LogEvent(Instant.ofEpochMilli(event.getTimeStamp()), event.getLevel().toString(),
                event.getLoggerName(), event.getFormattedMessage(), fields, exception, stackTop));
    }
}
