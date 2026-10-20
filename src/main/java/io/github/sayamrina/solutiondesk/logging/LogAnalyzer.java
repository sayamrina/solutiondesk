package io.github.sayamrina.solutiondesk.logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** Turns raw log events into the views support needs: grouped errors, search and one request's trail. */
@Service
public class LogAnalyzer {

    /** All events with the same error code (and exception for unexpected errors) form one group. */
    public record ErrorGroup(String errorCode, String team, String level, String sample, int count,
                             Instant firstSeen, Instant lastSeen, List<String> paths, List<String> requestIds,
                             String exception, String stackTop) { }

    private final Supplier<List<LogEvent>> source;

    public LogAnalyzer() {
        this(() -> LogBuffer.snapshot().stream().sorted(Comparator.comparing(LogEvent::timestamp)).toList());
    }

    LogAnalyzer(Supplier<List<LogEvent>> source) {
        this.source = source;
    }

    public List<ErrorGroup> groups() {
        Map<String, List<LogEvent>> byKey = new LinkedHashMap<>();
        for (LogEvent e : source.get()) {
            String code = e.field("errorCode");
            if (code.isEmpty()) continue;
            byKey.computeIfAbsent(code + "|" + e.exception(), k -> new ArrayList<>()).add(e);
        }
        List<ErrorGroup> groups = new ArrayList<>();
        for (List<LogEvent> events : byKey.values()) {
            LogEvent first = events.get(0);
            LogEvent last = events.get(events.size() - 1);
            List<String> paths = events.stream().map(e -> e.field("path")).filter(p -> !p.isEmpty()).distinct().limit(5).toList();
            List<String> ids = events.stream().map(e -> e.field("requestId")).filter(p -> !p.isEmpty()).distinct()
                    .sorted(Comparator.reverseOrder()).limit(5).toList();
            groups.add(new ErrorGroup(first.field("errorCode"), first.field("team"), first.level(), last.message(),
                    events.size(), first.timestamp(), last.timestamp(), paths, ids, first.exception(), first.stackTop()));
        }
        groups.sort(Comparator.comparing((ErrorGroup g) -> !g.level().equals("ERROR"))
                .thenComparing(ErrorGroup::count, Comparator.reverseOrder()));
        return groups;
    }

    /** Free-text search over message, error code, request ID and path; newest first. */
    public List<LogEvent> search(String query, String level, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return source.get().reversed().stream()
                .filter(e -> level == null || level.isBlank() || e.level().equals(level))
                .filter(e -> q.isEmpty() || (e.message() + " " + e.fields() + " " + e.exception()).toLowerCase().contains(q))
                .limit(limit)
                .toList();
    }

    /** Everything one request wrote, oldest first: the usual starting point for an incident. */
    public List<LogEvent> trail(String requestId) {
        return source.get().stream().filter(e -> e.field("requestId").equals(requestId)).toList();
    }
}
