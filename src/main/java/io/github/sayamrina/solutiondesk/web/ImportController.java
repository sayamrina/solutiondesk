package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.learning.CourseImportService;
import io.github.sayamrina.solutiondesk.learning.CoursePackage;
import io.github.sayamrina.solutiondesk.learning.LearningRepository;
import io.github.sayamrina.solutiondesk.web.SamplePackages.SamplePackage;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ImportController {

    private final CourseImportService importer;
    private final LearningRepository repository;
    private final SamplePackages samples;

    public ImportController(CourseImportService importer, LearningRepository repository, SamplePackages samples) {
        this.importer = importer;
        this.repository = repository;
        this.samples = samples;
    }

    @GetMapping("/admin/import")
    public String page(Model model) {
        model.addAttribute("courses", repository.courses());
        model.addAttribute("samples", samples.all());
        return "admin/import";
    }

    @PostMapping("/admin/import/sample")
    public String importSample(@RequestParam String name, @RequestParam(defaultValue = "import") String mode,
                               RedirectAttributes redirect) {
        SamplePackage p = samples.get(name);
        return run(p.name(), p.bytes(), mode, redirect);
    }

    @PostMapping("/admin/import/upload")
    public String upload(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "import") String mode,
                         RedirectAttributes redirect) throws IOException {
        return run(file.getOriginalFilename(), file.getBytes(), mode, redirect);
    }

    private String run(String name, byte[] bytes, String mode, RedirectAttributes redirect) {
        if (mode.equals("validate")) {
            CoursePackage p = importer.validate(name, bytes);
            redirect.addFlashAttribute("validated", name + ": valid, " + p.code() + " \"" + p.title() + "\" with "
                    + p.lessons().size() + " lessons and " + p.questions().size() + " questions. Nothing was imported.");
        } else {
            importer.importPackage(name, bytes);
            redirect.addFlashAttribute("imported", name);
        }
        return "redirect:/admin/import";
    }
}
