package io.github.sayamrina.solutiondesk.learning;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.common.SolutionDeskException;
import io.github.sayamrina.solutiondesk.learning.Model.Attempt;
import io.github.sayamrina.solutiondesk.learning.Model.Course;
import io.github.sayamrina.solutiondesk.learning.Model.Enrollment;
import io.github.sayamrina.solutiondesk.learning.Model.Learner;
import io.github.sayamrina.solutiondesk.learning.Model.Question;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** What a learner does: open a course and submit the final quiz. */
@Service
public class LearningService {

    private static final Logger log = LoggerFactory.getLogger("solutiondesk.learning");

    private final LearningRepository repository;
    private final Clock clock;

    public LearningService(LearningRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Learner learner(long learnerId) {
        return repository.learner(learnerId).orElseThrow(() -> new IllegalArgumentException("unknown learner " + learnerId));
    }

    /** Opens a course for a learner, or explains in the log why it is hidden. */
    public Course openCourse(long learnerId, long courseId) {
        Learner learner = learner(learnerId);
        Course course = repository.course(courseId).orElseThrow(() -> new SolutionDeskException(ErrorCode.COURSE_NOT_VISIBLE,
                "course " + courseId + " does not exist"));
        String reason = null;
        if (!course.published()) {
            reason = "course " + course.code() + " has status '" + course.status() + "'";
        } else if (!course.targetGroup().equals(learner.groupName())) {
            reason = "course " + course.code() + " is for group '" + course.targetGroup() + "', learner "
                    + learner.id() + " is in group '" + learner.groupName() + "'";
        } else if (repository.enrollment(learnerId, courseId).isEmpty()) {
            reason = "learner " + learner.id() + " is not enrolled in course " + course.code();
        }
        if (reason != null) throw new SolutionDeskException(ErrorCode.COURSE_NOT_VISIBLE, reason);
        return course;
    }

    public Attempt submitQuiz(long learnerId, long courseId, Map<Long, Integer> answers) {
        Course course = repository.course(courseId).orElseThrow();
        Enrollment enrollment = repository.enrollment(learnerId, courseId).orElseThrow(() -> new SolutionDeskException(
                ErrorCode.QUIZ_NOT_ENROLLED, "learner " + learnerId + " is not enrolled in course " + course.code()));
        LocalDate today = LocalDate.now(clock);
        if (enrollment.validUntil().isBefore(today)) {
            throw new SolutionDeskException(ErrorCode.QUIZ_ENROLLMENT_EXPIRED,
                    "enrollment " + enrollment.id() + " (learner " + learnerId + ", course " + course.code()
                            + ") expired on " + enrollment.validUntil() + "; result was not saved", enrollment.validUntil());
        }
        List<Question> questions = repository.questions(courseId);
        long answered = questions.stream().filter(q -> answers.containsKey(q.id())).count();
        if (answered < questions.size()) {
            throw new SolutionDeskException(ErrorCode.QUIZ_INCOMPLETE,
                    answered + " of " + questions.size() + " questions answered", answered, questions.size());
        }
        int correct = (int) questions.stream().filter(q -> answers.get(q.id()) == q.correct()).count();
        int score = ScoreCalculator.percent(correct, questions.size());
        boolean passed = ScoreCalculator.passed(score);
        long id = repository.insertAttempt(enrollment.id(), correct, questions.size(), score, passed, clock.instant());
        log.info("quiz submitted: learner {} course {} correct {}/{} score {}% passed {}", learnerId, course.code(), correct,
                questions.size(), score, passed);
        return repository.attempts(enrollment.id()).stream().filter(a -> a.id() == id).findFirst().orElseThrow();
    }
}
