package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.learning.LearningRepository;
import io.github.sayamrina.solutiondesk.learning.LearningService;
import io.github.sayamrina.solutiondesk.learning.Model.Course;
import io.github.sayamrina.solutiondesk.learning.Model.Learner;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The learner's side: catalog, course and final quiz. The learner is picked from a list (sample data, no login). */
@Controller
public class LearnerController {

    /** A course as shown in the catalog: the teaser is prepared here, not in the template. */
    public record CourseCard(Course course, String teaser) { }

    private final LearningRepository repository;
    private final LearningService service;

    public LearnerController(LearningRepository repository, LearningService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping("/learn")
    public String catalog(@RequestParam(defaultValue = "1") long learner, Model model) {
        Learner l = service.learner(learner);
        model.addAttribute("learners", repository.learners());
        model.addAttribute("learner", l);
        model.addAttribute("catalog", repository.catalogFor(learner).stream().map(c -> new CourseCard(c, c.teaser())).toList());
        model.addAttribute("allCourses", repository.courses());
        return "learn/catalog";
    }

    @GetMapping("/learn/{learnerId}/courses/{courseId}")
    public String course(@PathVariable long learnerId, @PathVariable long courseId, Model model) {
        Course course = service.openCourse(learnerId, courseId);
        model.addAttribute("learner", service.learner(learnerId));
        model.addAttribute("course", course);
        model.addAttribute("lessons", repository.lessons(courseId));
        model.addAttribute("questions", repository.questions(courseId));
        model.addAttribute("enrollment", repository.enrollment(learnerId, courseId).orElseThrow());
        model.addAttribute("attempts", repository.enrollment(learnerId, courseId).map(e -> repository.attempts(e.id())).orElseThrow());
        return "learn/course";
    }

    @PostMapping("/learn/{learnerId}/courses/{courseId}/quiz")
    public String submit(@PathVariable long learnerId, @PathVariable long courseId, @RequestParam Map<String, String> form,
                         RedirectAttributes redirect) {
        Map<Long, Integer> answers = new HashMap<>();
        form.forEach((k, v) -> {
            if (k.startsWith("q")) answers.put(Long.parseLong(k.substring(1)), Integer.parseInt(v));
        });
        var attempt = service.submitQuiz(learnerId, courseId, answers);
        redirect.addFlashAttribute("result", attempt);
        return "redirect:/learn/" + learnerId + "/courses/" + courseId + "#quiz";
    }
}
