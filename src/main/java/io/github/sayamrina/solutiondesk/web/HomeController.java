package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.learning.LearningRepository;
import io.github.sayamrina.solutiondesk.logging.LogAnalyzer;
import io.github.sayamrina.solutiondesk.support.Ticket;
import io.github.sayamrina.solutiondesk.support.TicketRepository;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final TicketRepository tickets;
    private final LearningRepository learning;
    private final LogAnalyzer logs;

    public HomeController(TicketRepository tickets, LearningRepository learning, LogAnalyzer logs) {
        this.tickets = tickets;
        this.learning = learning;
        this.logs = logs;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Ticket> all = tickets.all();
        model.addAttribute("openTickets", all.stream().filter(t -> t.status().open()).count());
        model.addAttribute("urgentTickets", all.stream().filter(t -> t.status().open())
                .filter(t -> t.priority().equals("P1") || t.priority().equals("P2")).count());
        model.addAttribute("errorGroups", logs.groups().size());
        model.addAttribute("errorEvents", logs.groups().stream().mapToInt(LogAnalyzer.ErrorGroup::count).sum());
        model.addAttribute("courses", learning.courses().size());
        model.addAttribute("learners", learning.learners().size());
        model.addAttribute("recent", all.stream().filter(t -> t.status().open()).limit(4).toList());
        return "home";
    }
}
