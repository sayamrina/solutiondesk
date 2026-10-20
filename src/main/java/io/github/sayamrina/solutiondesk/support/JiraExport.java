package io.github.sayamrina.solutiondesk.support;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Exports tickets as CSV for the Jira CSV importer (Summary, Description, Priority, Labels, ...),
 * so incidents can move to a Jira project without retyping.
 */
@Component
public class JiraExport {

    private static final DateTimeFormatter JIRA_DATE = DateTimeFormatter.ofPattern("dd/MMM/yy h:mm a").withZone(ZoneId.of("Europe/Berlin"));

    public String csv(List<Ticket> tickets) {
        StringBuilder sb = new StringBuilder("Issue Type,Summary,Description,Priority,Status,Labels,Component,Created,External ID\n");
        for (Ticket t : tickets) {
            String description = t.description()
                    + (t.rootCause() == null ? "" : "\n\nRoot cause: " + t.rootCause())
                    + (t.workaround() == null ? "" : "\n\nWorkaround: " + t.workaround())
                    + (t.resolution() == null ? "" : "\n\nResolution: " + t.resolution());
            sb.append(String.join(",", "Incident", q(t.summary()), q(description), jiraPriority(t.priority()),
                    q(t.status().label()), q(t.errorCode() == null ? "" : t.errorCode()), q(t.team()),
                    q(JIRA_DATE.format(t.createdAt())), q(t.key()))).append('\n');
        }
        return sb.toString();
    }

    static String jiraPriority(String p) {
        return switch (p) {
            case "P1" -> "Highest";
            case "P2" -> "High";
            case "P3" -> "Medium";
            default -> "Low";
        };
    }

    private static String q(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
