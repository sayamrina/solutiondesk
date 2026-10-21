package io.github.sayamrina.solutiondesk;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.common.SolutionDeskException;
import io.github.sayamrina.solutiondesk.learning.CourseImportService;
import io.github.sayamrina.solutiondesk.learning.LearningRepository;
import io.github.sayamrina.solutiondesk.learning.LearningService;
import io.github.sayamrina.solutiondesk.learning.Model.Course;
import io.github.sayamrina.solutiondesk.learning.Model.Question;
import io.github.sayamrina.solutiondesk.logging.BufferAppender;
import io.github.sayamrina.solutiondesk.logging.IncidentLogger;
import io.github.sayamrina.solutiondesk.logging.RequestIdFilter;
import io.github.sayamrina.solutiondesk.support.Priority;
import io.github.sayamrina.solutiondesk.support.Ticket;
import io.github.sayamrina.solutiondesk.support.Ticket.Status;
import io.github.sayamrina.solutiondesk.support.TicketRepository;
import io.github.sayamrina.solutiondesk.web.SamplePackages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fills the demo: imports the course packages, enrolls learners and then plays realistic traffic through
 * the real services. The errors in the log and the request IDs in the open tickets come from these runs.
 */
@Component
@ConditionalOnProperty(name = "solutiondesk.sample-traffic", havingValue = "true", matchIfMissing = true)
public class SampleData implements ApplicationRunner {

    private static final Logger access = LoggerFactory.getLogger("solutiondesk.access");

    private final SamplePackages samples;
    private final CourseImportService importer;
    private final LearningRepository learning;
    private final LearningService service;
    private final TicketRepository tickets;
    private final Clock clock;
    private final Map<String, Long> courseIds = new HashMap<>();

    public SampleData(SamplePackages samples, CourseImportService importer, LearningRepository learning,
                      LearningService service, TicketRepository tickets, Clock clock) {
        this.samples = samples;
        this.importer = importer;
        this.learning = learning;
        this.service = service;
        this.tickets = tickets;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (var p : samples.all()) {
            if (!p.broken()) importer.importPackage(p.name(), p.bytes());
        }
        learning.courses().forEach(c -> courseIds.put(c.code(), c.id()));
        LocalDate today = LocalDate.now(clock);
        learning.enroll(1, id("VLG-LS-101"), today.plusDays(60));
        learning.enroll(2, id("VLG-LS-101"), today.minusDays(4));
        learning.enroll(3, id("KWM-HY-201"), today.plusDays(90));
        learning.enroll(4, id("KWM-HY-201"), today.plusDays(90));
        learning.enroll(4, id("KWM-DP-202"), today.plusDays(90));
        learning.enroll(5, id("TMB-MS-301"), today.plusDays(30));
        learning.enroll(6, id("TMB-MS-301"), today.plusDays(30));
        historicAttemptsFromRelease100();

        // normal use
        openAndPass(Duration.ofMinutes(150), 1, "VLG-LS-101");
        openAndPass(Duration.ofMinutes(135), 3, "KWM-HY-201");
        openAndPass(Duration.ofMinutes(120), 5, "TMB-MS-301");
        request(Duration.ofMinutes(95), "POST", "/learn/6/courses/" + id("TMB-MS-301") + "/quiz",
                () -> service.submitQuiz(6, id("TMB-MS-301"), Map.of(firstQuestion("TMB-MS-301"), 1)));
        importSample(Duration.ofMinutes(88), "supplier-doctype.xml");
        importSample(Duration.ofMinutes(80), "night-shift-missing-title.xml");

        // what customers will call about (each a few minutes before the ticket was opened)
        String tooLarge = request(Duration.ofMinutes(62), "POST", "/admin/import/upload",
                () -> importer.importPackage("machine-safety-video-course.xml", new byte[600 * 1024]));
        String quizExpired = null;
        for (int minutes : new int[] {58, 52, 47}) {
            quizExpired = request(Duration.ofMinutes(minutes), "POST", "/learn/2/courses/" + id("VLG-LS-101") + "/quiz",
                    () -> service.submitQuiz(2, id("VLG-LS-101"), allCorrect("VLG-LS-101")));
        }
        String notVisible = null;
        for (int minutes : new int[] {34, 29}) {
            notVisible = request(Duration.ofMinutes(minutes), "GET", "/learn/4/courses/" + id("KWM-HY-201"),
                    () -> service.openCourse(4, id("KWM-HY-201")));
        }
        String notWellFormed = null;
        for (int minutes : new int[] {19, 14}) {
            notWellFormed = importSample(Duration.ofMinutes(minutes), "fire-safety-not-well-formed.xml");
        }

        seedTickets(quizExpired, notVisible, notWellFormed, tooLarge);
    }

    // ---------------------------------------------------------------- traffic helpers

    private long id(String code) {
        return courseIds.get(code);
    }

    private long firstQuestion(String code) {
        return learning.questions(id(code)).get(0).id();
    }

    private Map<Long, Integer> allCorrect(String code) {
        Map<Long, Integer> answers = new HashMap<>();
        for (Question q : learning.questions(id(code))) answers.put(q.id(), q.correct());
        return answers;
    }

    private void openAndPass(Duration ago, long learnerId, String code) {
        request(ago, "GET", "/learn/" + learnerId + "/courses/" + id(code), () -> service.openCourse(learnerId, id(code)));
        request(ago.minusMinutes(6), "POST", "/learn/" + learnerId + "/courses/" + id(code) + "/quiz",
                () -> service.submitQuiz(learnerId, id(code), allCorrect(code)));
    }

    private String importSample(Duration ago, String name) {
        var p = samples.get(name);
        return request(ago, "POST", "/admin/import/sample", () -> importer.importPackage(p.name(), p.bytes()));
    }

    /** Runs an action the way the web layer does: request ID in the log context, errors logged, access line. */
    private String request(Duration ago, String method, String path, Runnable action) {
        String requestId = RequestIdFilter.newRequestId();
        MDC.put(BufferAppender.SIMULATED_AT, String.valueOf(clock.instant().minus(ago).toEpochMilli()));
        MDC.put("requestId", requestId);
        MDC.put("method", method);
        MDC.put("path", path);
        int status = 200;
        try {
            action.run();
        } catch (SolutionDeskException e) {
            IncidentLogger.known(e, Map.of());
            status = e.errorCode().status().value();
        } finally {
            MDC.put("status", String.valueOf(status));
            MDC.put("durationMs", String.valueOf(12 + (requestId.hashCode() & 0x3f)));
            access.info("request completed");
            MDC.clear();
        }
        return requestId;
    }

    /** Two attempts stored by release 1.0.0 with the percentage bug (see ticket SD-105). */
    private void historicAttemptsFromRelease100() {
        Instant tenDaysAgo = clock.instant().minus(Duration.ofDays(10));
        long lea = learning.enrollment(6, id("TMB-MS-301")).orElseThrow().id();
        long alex = learning.enrollment(5, id("TMB-MS-301")).orElseThrow().id();
        learning.insertAttempt(lea, 2, 3, 0, false, tenDaysAgo);
        learning.insertAttempt(alex, 2, 3, 0, false, tenDaysAgo.plus(Duration.ofMinutes(40)));
    }

    // ---------------------------------------------------------------- tickets

    private record Step(Duration after, String actor, String kind, String text) { }

    private void seedTickets(String quizExpired, String notVisible, String notWellFormed, String tooLarge) {
        Instant now = clock.instant();
        String support = "Support · K. Lindner";

        ticket("SD-101", 1, "Course package import fails: \"item points to a resource that does not exist\"",
                "Our training coordinator cannot import the new forklift refresher. The import stops with error SD-IMP-003. "
                        + "The same package works in our authoring tool preview.",
                2, 2, Status.CLOSED, "Support", ErrorCode.IMPORT_BROKEN_REFERENCE, null,
                "In the manifest, item 'Load diagram' references identifierref='f2', but the resource is declared as identifier='F2'. "
                        + "Identifiers are case-sensitive in the package format; the authoring tool preview ignores case.",
                "Change the resource identifier to 'f2' (or the reference to 'F2') and import again.",
                "Customer corrected the identifier and re-imported the package successfully. KB article updated with the case-sensitivity hint.",
                now.minus(Duration.ofDays(9)), List.of(
                        new Step(Duration.ofMinutes(20), support, "status", "New → In analysis"),
                        new Step(Duration.ofMinutes(35), support, "comment", "Reproduced with the customer's file in the import page. "
                                + "Error detail: item 'Load diagram' points to resource 'f2', which does not exist. The file declares 'F2'."),
                        new Step(Duration.ofMinutes(50), support, "reply", "Hello, the package references a lesson resource as 'f2', but the "
                                + "resource is declared as 'F2'. Identifiers are case-sensitive. Please change one of them so they match and import "
                                + "the package again. Step-by-step guide: knowledge base article SD-IMP-003."),
                        new Step(Duration.ofHours(26), support, "status", "In analysis → Resolved"),
                        new Step(Duration.ofDays(3), support, "status", "Resolved → Closed: customer confirmed")));

        ticket("SD-102", 1, "Umlauts in the course title are broken after import",
                "We tried to import the dangerous goods course 'Gefahrgut: Grundlagen für Fahrer'. The import is rejected with SD-IMP-004.",
                2, 2, Status.RESOLVED, "Support", ErrorCode.IMPORT_ENCODING, null,
                "The manifest declares encoding='UTF-8', but the file was saved as Windows-1252 by an older editor. "
                        + "The byte for 'ü' (0xFC) is not valid UTF-8, so the parser stops in line 4.",
                "Open the file in an editor and save it as UTF-8 (without BOM), then import again.",
                "Customer re-saved the file as UTF-8; import succeeded. Suggested to development: show the line number in the learner-facing message too.",
                now.minus(Duration.ofDays(6)), List.of(
                        new Step(Duration.ofMinutes(15), support, "status", "New → In analysis"),
                        new Step(Duration.ofMinutes(30), support, "comment", "Checked the file with a hex view: 0xFC at line 4 (\"für\"). "
                                + "Classic Windows-1252 file declared as UTF-8."),
                        new Step(Duration.ofHours(3), support, "status", "In analysis → Resolved")));

        ticket("SD-103", 1, "Quiz result is not saved for one driver",
                "Mira Kowalski finished the Load Securing quiz three times today, but the result never appears in the report. "
                        + "She gets an error message each time. Other drivers are fine.",
                3, 1, Status.IN_ANALYSIS, "Support", ErrorCode.QUIZ_ENROLLMENT_EXPIRED, quizExpired,
                null, null, null, now.minus(Duration.ofMinutes(40)), List.of(
                        new Step(Duration.ofMinutes(5), support, "status", "New → In analysis"),
                        new Step(Duration.ofMinutes(6), support, "reply", "Hello, thank you for the report. We are checking the result of "
                                + "Mira Kowalski now and will get back to you shortly."),
                        new Step(Duration.ofMinutes(8), support, "comment", "Error code SD-QZ-002 in the request trail. Next: check the enrollment "
                                + "with the diagnostic 'Enrollments and quiz results of a learner'.")));

        ticket("SD-104", 2, "Administration staff cannot see the hand hygiene course",
                "Sam Richter from administration should complete 'Hand Hygiene on the Ward' this month, "
                        + "but gets 'Course not available' (SD-CRS-001). Nurses can open it.",
                3, 2, Status.NEW, "Support", ErrorCode.COURSE_NOT_VISIBLE, notVisible,
                null, null, null, now.minus(Duration.ofMinutes(25)), List.of());

        ticket("SD-105", 3, "Quiz shows 0 % although most answers were correct",
                "Two machine operators answered 2 of 3 questions correctly in 'Safe Work at the Lathe', but the result shows 0 % and 'failed'.",
                1, 2, Status.RESOLVED, "Development", null, null,
                "Release 1.0.0 calculated the percentage as (correct / total) * 100 with integer division. "
                        + "2 / 3 is 0 in integer arithmetic, so every result below 100 % was stored as 0 %. All customers were affected (impact 1).",
                "None for learners; trainers can read the number of correct answers in the report.",
                "Fixed in release 1.0.1 (correct * 100 / total) with a regression test. Diagnostic 'score-mismatch' lists the 2 attempts stored by 1.0.0; "
                        + "data correction is planned with the customer.",
                now.minus(Duration.ofDays(10)).plus(Duration.ofHours(2)), List.of(
                        new Step(Duration.ofMinutes(10), support, "status", "New → In analysis"),
                        new Step(Duration.ofMinutes(30), support, "comment", "Reproduced: 2 of 3 correct → 0 %. 1 of 1 correct → 100 %. "
                                + "Looks like integer division. Not a data problem: every customer is affected. Raised impact to 1 (P2)."),
                        new Step(Duration.ofMinutes(35), support, "status", "In analysis → Escalated (Development): reproduction steps and the suspected "
                                + "line in ScoreCalculator attached"),
                        new Step(Duration.ofHours(5), "Development · R. Jansen", "comment", "Confirmed. Fix + regression test merged, released as 1.0.1."),
                        new Step(Duration.ofHours(6), support, "status", "Escalated → Resolved")));

        ticket("SD-106", 2, "Error page when opening the course catalog",
                "Since this morning our administration staff see 'Something went wrong' (SD-SYS-500) on the course overview. "
                        + "The nurses can use the platform normally.",
                2, 1, Status.RESOLVED, "Development", ErrorCode.UNEXPECTED, null,
                "The course 'Data Protection for Administration Staff' has no description in its manifest (the field is optional). "
                        + "The catalog card called description.strip() without a null check: NullPointerException in Model.Course.teaser(). "
                        + "Only learners who have this course in their catalog were affected.",
                "Temporarily unpublish the course or add a description to the manifest and import it again.",
                "Fixed in release 1.0.1: courses without a description show no teaser. Regression test added.",
                now.minus(Duration.ofDays(4)), List.of(
                        new Step(Duration.ofMinutes(12), support, "status", "New → In analysis"),
                        new Step(Duration.ofMinutes(20), support, "comment", "Log analysis: SD-SYS-500 with java.lang.NullPointerException, top frame "
                                + "Model$Course.teaser. Only learners of group 'Verwaltung' hit it; their catalog contains KWM-DP-202, whose description is NULL "
                                + "(diagnostic 'Course contents per course')."),
                        new Step(Duration.ofMinutes(25), support, "status", "In analysis → Escalated (Development): stack trace, request IDs and the "
                                + "affected course attached; workaround sent to the customer"),
                        new Step(Duration.ofHours(4), "Development · R. Jansen", "comment", "Null-safe teaser + test, released in 1.0.1."),
                        new Step(Duration.ofHours(5), support, "status", "Escalated → Resolved")));

        ticket("SD-107", 3, "Upload of a large course package is rejected",
                "We want to import a machine safety course with embedded video previews (about 600 KB). The upload stops with SD-UPL-001.",
                2, 3, Status.ESCALATED, "Hosting", ErrorCode.UPLOAD_TOO_LARGE, tooLarge,
                null, "Import the course without embedded media and link the videos from the media server instead.", null,
                now.minus(Duration.ofMinutes(55)), List.of(
                        new Step(Duration.ofMinutes(10), support, "status", "New → In analysis"),
                        new Step(Duration.ofMinutes(18), support, "comment", "Not a package error: the server limit is 256 KB per upload "
                                + "(spring.servlet.multipart.max-file-size)."),
                        new Step(Duration.ofMinutes(19), support, "reply", "Hello, the package is larger than the current upload limit of 256 KB "
                                + "because of the embedded videos. Until we have an answer from our hosting team, you can import the course without "
                                + "the videos and link them from your media server."),
                        new Step(Duration.ofMinutes(20), support, "status", "In analysis → Escalated (Hosting): please decide whether the upload limit "
                                + "can be raised to 2 MB for this tenant; request ID attached")));

        ticket("SD-108", 3, "New fire safety course cannot be imported",
                "The import of 'Fire Safety in the Workshop' fails with SD-IMP-001. We tried twice. The training starts next week.",
                3, 2, Status.NEW, "Support", ErrorCode.IMPORT_NOT_WELL_FORMED, notWellFormed,
                null, null, null, now.minus(Duration.ofMinutes(10)), List.of());
    }

    private void ticket(String key, long customerId, String summary, String description, int impact, int urgency,
                        Status status, String team, ErrorCode code, String requestId, String rootCause, String workaround,
                        String resolution, Instant created, List<Step> steps) {
        Instant updated = steps.isEmpty() ? created : created.plus(steps.get(steps.size() - 1).after());
        String customer = switch ((int) customerId) {
            case 1 -> "Customer · Velmar Logistik";
            case 2 -> "Customer · Klinikverbund Wendmoor";
            default -> "Customer · Tessberg Maschinenbau";
        };
        long id = tickets.insert(new Ticket(0, key, customerId, null, summary, description, impact, urgency,
                Priority.of(impact, urgency), status, team, code == null ? null : code.code(), requestId,
                code == null ? null : code.kbSlug(), rootCause, workaround, resolution, created, updated));
        tickets.addEvent(id, created, customer, "created", "Reported via the customer portal. Priority " + Priority.of(impact, urgency) + ".");
        for (Step s : steps) tickets.addEvent(id, created.plus(s.after()), s.actor(), s.kind(), s.text());
    }
}
