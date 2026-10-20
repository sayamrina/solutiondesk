package io.github.sayamrina.solutiondesk.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the latest log events in memory. Logback creates the appender itself, so the buffer is a
 * static holder that both the appender and the Spring beans can reach.
 */
public final class LogBuffer {

    public static final int CAPACITY = 5000;
    private static final Deque<LogEvent> EVENTS = new ArrayDeque<>();

    private LogBuffer() { }

    static synchronized void add(LogEvent event) {
        if (EVENTS.size() == CAPACITY) EVENTS.removeFirst();
        EVENTS.addLast(event);
    }

    public static synchronized List<LogEvent> snapshot() {
        return new ArrayList<>(EVENTS);
    }

    public static synchronized void clear() {
        EVENTS.clear();
    }
}
