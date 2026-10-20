package io.github.sayamrina.solutiondesk.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Read-only SQL checks that support runs while analysing a ticket. Only these reviewed, parameterised
 * queries can run: there is deliberately no free SQL console in a customer-facing system.
 */
@Service
public class Diagnostics {

    public record Check(String id, String title, String question, List<String> params, String sql) { }

    public record Result(List<String> columns, List<Map<String, Object>> rows) { }

    private static final List<Check> CHECKS = List.of(
            new Check("course-visibility", "Why can't a learner see a course?",
                    "Compares course status and target group with the learner's group and enrollment.",
                    List.of("email", "course_code"), """
                    SELECT l.name AS learner, l.group_name AS learner_group, c.code AS course, c.status,
                           c.target_group, (e.id IS NOT NULL) AS enrolled, e.valid_until,
                           CASE WHEN c.status <> 'published' THEN 'course not published'
                                WHEN c.target_group <> l.group_name THEN 'group mismatch'
                                WHEN e.id IS NULL THEN 'not enrolled'
                                ELSE 'visible' END AS verdict
                    FROM learner l
                    JOIN course c ON c.code = :course_code
                    LEFT JOIN enrollment e ON e.learner_id = l.id AND e.course_id = c.id
                    WHERE l.email = :email"""),
            new Check("enrollment-status", "Enrollments and quiz results of a learner",
                    "Shows every enrollment with its end date and the latest quiz attempt.",
                    List.of("email"), """
                    SELECT c.code AS course, e.valid_until,
                           CASE WHEN e.valid_until < CURRENT_DATE THEN 'expired' ELSE 'active' END AS enrollment,
                           a.score_percent AS last_score, a.correct, a.total, a.passed, a.submitted_at
                    FROM learner l
                    JOIN enrollment e ON e.learner_id = l.id
                    JOIN course c ON c.id = e.course_id
                    LEFT JOIN quiz_attempt a ON a.id = (SELECT MAX(id) FROM quiz_attempt x WHERE x.enrollment_id = e.id)
                    WHERE l.email = :email ORDER BY c.code"""),
            new Check("expired-enrollments", "Enrollments that expired in the last 30 days",
                    "Learners who may report that their result was not saved.",
                    List.of(), """
                    SELECT cu.name AS customer, l.name AS learner, l.email, c.code AS course, e.valid_until
                    FROM enrollment e
                    JOIN learner l ON l.id = e.learner_id
                    JOIN customer cu ON cu.id = l.customer_id
                    JOIN course c ON c.id = e.course_id
                    WHERE e.valid_until < CURRENT_DATE AND e.valid_until >= CURRENT_DATE - 30
                    ORDER BY e.valid_until DESC"""),
            new Check("score-mismatch", "Quiz results that do not match the answers",
                    "Finds attempts whose stored percentage differs from correct / total. Used to find the scope of SD-105.",
                    List.of(), """
                    SELECT a.id AS attempt, l.name AS learner, c.code AS course, a.correct, a.total, a.score_percent AS stored,
                           CAST(a.correct * 100 / a.total AS INT) AS expected, a.passed, a.submitted_at
                    FROM quiz_attempt a
                    JOIN enrollment e ON e.id = a.enrollment_id
                    JOIN learner l ON l.id = e.learner_id
                    JOIN course c ON c.id = e.course_id
                    WHERE a.total > 0 AND a.score_percent <> CAST(a.correct * 100 / a.total AS INT)
                    ORDER BY a.submitted_at DESC"""),
            new Check("course-contents", "Course contents per course",
                    "Status, target group, number of lessons and questions, and missing descriptions.",
                    List.of(), """
                    SELECT c.code, c.title, c.status, c.target_group, c.manifest_version,
                           (SELECT COUNT(*) FROM lesson x WHERE x.course_id = c.id) AS lessons,
                           (SELECT COUNT(*) FROM question q WHERE q.course_id = c.id) AS questions,
                           (c.description IS NULL OR TRIM(c.description) = '') AS missing_description
                    FROM course c ORDER BY c.code"""),
            new Check("tickets-by-code", "Tickets per error code",
                    "Which errors cause the most tickets: candidates for a fix or a better knowledge base article.",
                    List.of(), """
                    SELECT COALESCE(error_code, '(none)') AS error_code, COUNT(*) AS tickets,
                           SUM(CASE WHEN status IN ('RESOLVED', 'CLOSED') THEN 0 ELSE 1 END) AS open
                    FROM ticket GROUP BY COALESCE(error_code, '(none)') ORDER BY tickets DESC"""));

    private final JdbcClient db;

    public Diagnostics(JdbcClient db) {
        this.db = db;
    }

    public List<Check> checks() { return CHECKS; }

    public Optional<Check> check(String id) {
        return CHECKS.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public Result run(Check check, Map<String, String> params) {
        var spec = db.sql(check.sql());
        for (String p : check.params()) {
            spec = spec.param(p, params.getOrDefault(p, "").strip());
        }
        List<Map<String, Object>> rows = spec.query().listOfRows().stream()
                .map(r -> {
                    Map<String, Object> lower = new LinkedHashMap<>();
                    r.forEach((k, v) -> lower.put(k.toLowerCase(), v));
                    return lower;
                })
                .toList();
        List<String> columns = rows.isEmpty() ? List.of() : List.copyOf(rows.get(0).keySet());
        return new Result(columns, rows);
    }
}
