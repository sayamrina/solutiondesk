package io.github.sayamrina.solutiondesk.logging;

import java.time.Instant;
import java.util.Map;

/** One structured log line as kept in memory for the log analysis page. */
public record LogEvent(Instant timestamp, String level, String logger, String message,
                       Map<String, String> fields, String exception, String stackTop) {

    public String field(String name) {
        return fields.getOrDefault(name, "");
    }
}
