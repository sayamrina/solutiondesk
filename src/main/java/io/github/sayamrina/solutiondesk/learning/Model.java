package io.github.sayamrina.solutiondesk.learning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Rows of the learning side of the platform. */
public final class Model {

    private Model() { }

    public record Learner(long id, long customerId, String customerName, String name, String email, String groupName) { }

    public record Course(long id, String code, String title, String description, String status, String targetGroup,
                         String manifestVersion, Instant importedAt) {

        public boolean published() {
            return "published".equalsIgnoreCase(status);
        }

        /** Short text for the course card. */
        public String teaser() {
            String text = description.strip();
            return text.length() > 140 ? text.substring(0, 137) + "..." : text;
        }
    }

    public record Lesson(long id, int position, String title, String href, String body) { }

    public record Question(long id, int position, String text, List<String> options, int correct) { }

    public record Enrollment(long id, long learnerId, long courseId, LocalDate validUntil) { }

    public record Attempt(long id, long enrollmentId, int correct, int total, int scorePercent, boolean passed,
                          Instant submittedAt) { }
}
