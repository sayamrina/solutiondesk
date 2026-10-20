package io.github.sayamrina.solutiondesk.support;

import java.time.Duration;

/**
 * Impact x urgency matrix (ITIL style). Impact: 1 = many customers, 2 = one customer or a whole group,
 * 3 = a single user. Urgency: 1 = work is blocked, 2 = work is harder, 3 = cosmetic or a question.
 */
public final class Priority {

    private static final String[][] MATRIX = {
            {"P1", "P2", "P3"},
            {"P2", "P3", "P4"},
            {"P3", "P4", "P4"},
    };

    private Priority() { }

    public static String of(int impact, int urgency) {
        if (impact < 1 || impact > 3 || urgency < 1 || urgency > 3) {
            throw new IllegalArgumentException("impact and urgency must be between 1 and 3");
        }
        return MATRIX[impact - 1][urgency - 1];
    }

    /** Target time for the first response to the customer. */
    public static Duration firstResponse(String priority) {
        return switch (priority) {
            case "P1" -> Duration.ofHours(1);
            case "P2" -> Duration.ofHours(4);
            case "P3" -> Duration.ofHours(8);
            default -> Duration.ofHours(24);
        };
    }
}
