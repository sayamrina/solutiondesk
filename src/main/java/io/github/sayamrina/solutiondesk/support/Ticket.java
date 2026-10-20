package io.github.sayamrina.solutiondesk.support;

import java.time.Instant;

public record Ticket(long id, String key, long customerId, String customerName, String summary, String description,
                     int impact, int urgency, String priority, Status status, String team, String errorCode,
                     String requestId, String kbSlug, String rootCause, String workaround, String resolution,
                     Instant createdAt, Instant updatedAt) {

    public enum Status {
        NEW("New"), IN_ANALYSIS("In analysis"), WAITING_FOR_CUSTOMER("Waiting for customer"), ESCALATED("Escalated"),
        RESOLVED("Resolved"), CLOSED("Closed");

        private final String label;

        Status(String label) { this.label = label; }

        public String label() { return label; }
        public boolean open() { return this != RESOLVED && this != CLOSED; }
    }

    public record Event(long id, Instant at, String actor, String kind, String text) { }
}
