package io.github.sayamrina.solutiondesk.support;

import java.time.Duration;
import java.time.Instant;

/**
 * First-response status of a ticket. A response is the first reply to the customer or the first status
 * change by support after the ticket was opened.
 */
public record Sla(Instant due, Instant respondedAt, State state) {

    public enum State {
        MET("Responded in time"), LATE("Responded late"), DUE("Response due"), OVERDUE("Response overdue");

        private final String label;

        State(String label) { this.label = label; }

        public String label() { return label; }
    }

    public static Sla of(Ticket t, Instant respondedAt, Instant now) {
        Instant due = t.createdAt().plus(Priority.firstResponse(t.priority()));
        State state;
        if (respondedAt != null) {
            state = respondedAt.isAfter(due) ? State.LATE : State.MET;
        } else {
            state = now.isAfter(due) ? State.OVERDUE : State.DUE;
        }
        return new Sla(due, respondedAt, state);
    }

    /** Time left until the response is due (negative when overdue). */
    public Duration remaining(Instant now) {
        return Duration.between(now, due);
    }
}
