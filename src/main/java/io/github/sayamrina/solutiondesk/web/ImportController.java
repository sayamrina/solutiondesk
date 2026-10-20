package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.learning.CourseImportService;
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
    public String importSample(@RequestParam String name, RedirectAttributes redirect) throws IOException {
        SamplePackage p = samples.get(name);
        importer.importPackage(p.name(), p.bytes());
        redirect.addFlashAttribute("imported", p.name());
        return "redirect:/admin/import";
    }

    @PostMapping("/admin/import/upload")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) throws IOException {
        importer.importPackage(file.getOriginalFilename(), file.getBytes());
        redirect.addFlashAttribute("imported", file.getOriginalFilename());
        return "redirect:/admin/import";
    }
}
