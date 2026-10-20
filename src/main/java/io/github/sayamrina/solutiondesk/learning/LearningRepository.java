package io.github.sayamrina.solutiondesk.learning;

import io.github.sayamrina.solutiondesk.learning.Model.Attempt;
import io.github.sayamrina.solutiondesk.learning.Model.Course;
import io.github.sayamrina.solutiondesk.learning.Model.Enrollment;
import io.github.sayamrina.solutiondesk.learning.Model.Learner;
import io.github.sayamrina.solutiondesk.learning.Model.Lesson;
import io.github.sayamrina.solutiondesk.learning.Model.Question;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Plain SQL access to learners, courses, enrollments and quiz attempts. */
@Repository
public class LearningRepository {

    static final String OPTION_SEPARATOR = "\u001F";
    private final JdbcClient db;

    public LearningRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Learner> learners() {
        return db.sql("""
                SELECT l.id, l.customer_id, c.name AS customer_name, l.name, l.email, l.group_name
                FROM learner l JOIN customer c ON c.id = l.customer_id ORDER BY c.name, l.name""")
                .query(LearningRepository::learner).list();
    }

    public Optional<Learner> learner(long id) {
        return db.sql("""
                SELECT l.id, l.customer_id, c.name AS customer_name, l.name, l.email, l.group_name
                FROM learner l JOIN customer c ON c.id = l.customer_id WHERE l.id = ?""")
                .param(id).query(LearningRepository::learner).optional();
    }

    public List<Course> courses() {
        return db.sql("SELECT * FROM course ORDER BY title").query(LearningRepository::course).list();
    }

    public Optional<Course> course(long id) {
        return db.sql("SELECT * FROM course WHERE id = ?").param(id).query(LearningRepository::course).optional();
    }

    public boolean courseCodeExists(String code) {
        return db.sql("SELECT COUNT(*) FROM course WHERE code = ?").param(code).query(Integer.class).single() > 0;
    }

    /** Courses a learner should see: published, for the learner's group, with an enrollment. */
    public List<Course> catalogFor(long learnerId) {
        return db.sql("""
                SELECT c.* FROM course c
                JOIN enrollment e ON e.course_id = c.id
                JOIN learner l ON l.id = e.learner_id
                WHERE l.id = ? AND c.status = 'published' AND c.target_group = l.group_name
                ORDER BY c.title""")
                .param(learnerId).query(LearningRepository::course).list();
    }

    public List<Lesson> lessons(long courseId) {
        return db.sql("SELECT * FROM lesson WHERE course_id = ? ORDER BY position").param(courseId)
                .query((rs, n) -> new Lesson(rs.getLong("id"), rs.getInt("position"), rs.getString("title"),
                        rs.getString("href"), rs.getString("body")))
                .list();
    }

    public List<Question> questions(long courseId) {
        return db.sql("SELECT * FROM question WHERE course_id = ? ORDER BY position").param(courseId)
                .query((rs, n) -> new Question(rs.getLong("id"), rs.getInt("position"), rs.getString("text"),
                        Arrays.asList(rs.getString("options").split(OPTION_SEPARATOR)), rs.getInt("correct")))
                .list();
    }

    @Transactional
    public long insertCourse(CoursePackage p, Instant importedAt) {
        KeyHolder key = new GeneratedKeyHolder();
        db.sql("""
                INSERT INTO course (code, title, description, status, target_group, manifest_version, imported_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""")
                .params(p.code(), p.title(), p.description(), p.status().toLowerCase(), p.targetGroup(), p.version(),
                        Timestamp.from(importedAt))
                .update(key, "id");
        long courseId = key.getKey().longValue();
        int pos = 1;
        for (CoursePackage.Lesson l : p.lessons()) {
            db.sql("INSERT INTO lesson (course_id, position, title, href, body) VALUES (?, ?, ?, ?, ?)")
                    .params(courseId, pos++, l.title(), l.href(), l.body()).update();
        }
        pos = 1;
        for (CoursePackage.Question q : p.questions()) {
            db.sql("INSERT INTO question (course_id, position, text, options, correct) VALUES (?, ?, ?, ?, ?)")
                    .params(courseId, pos++, q.text(), String.join(OPTION_SEPARATOR, q.options()), q.correct()).update();
        }
        return courseId;
    }

    public void enroll(long learnerId, long courseId, LocalDate validUntil) {
        db.sql("INSERT INTO enrollment (learner_id, course_id, valid_until) VALUES (?, ?, ?)")
                .params(learnerId, courseId, validUntil).update();
    }

    public void extendEnrollment(long enrollmentId, LocalDate validUntil) {
        db.sql("UPDATE enrollment SET valid_until = ? WHERE id = ?").params(validUntil, enrollmentId).update();
    }

    public Optional<Enrollment> enrollment(long learnerId, long courseId) {
        return db.sql("SELECT * FROM enrollment WHERE learner_id = ? AND course_id = ?").params(learnerId, courseId)
                .query((rs, n) -> new Enrollment(rs.getLong("id"), rs.getLong("learner_id"), rs.getLong("course_id"),
                        rs.getDate("valid_until").toLocalDate()))
                .optional();
    }

    public long insertAttempt(long enrollmentId, int correct, int total, int score, boolean passed, Instant at) {
        KeyHolder key = new GeneratedKeyHolder();
        db.sql("""
                INSERT INTO quiz_attempt (enrollment_id, correct, total, score_percent, passed, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?)""")
                .params(enrollmentId, correct, total, score, passed, Timestamp.from(at)).update(key, "id");
        return key.getKey().longValue();
    }

    public List<Attempt> attempts(long enrollmentId) {
        return db.sql("SELECT * FROM quiz_attempt WHERE enrollment_id = ? ORDER BY submitted_at DESC").param(enrollmentId)
                .query((rs, n) -> new Attempt(rs.getLong("id"), rs.getLong("enrollment_id"), rs.getInt("correct"),
                        rs.getInt("total"), rs.getInt("score_percent"), rs.getBoolean("passed"),
                        instant(rs, "submitted_at")))
                .list();
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static Learner learner(ResultSet rs, int n) throws SQLException {
        return new Learner(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("customer_name"), rs.getString("name"),
                rs.getString("email"), rs.getString("group_name"));
    }

    private static Course course(ResultSet rs, int n) throws SQLException {
        return new Course(rs.getLong("id"), rs.getString("code"), rs.getString("title"), rs.getString("description"),
                rs.getString("status"), rs.getString("target_group"), rs.getString("manifest_version"),
                instant(rs, "imported_at"));
    }
}
