package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.learning.LearningRepository;
import io.github.sayamrina.solutiondesk.logging.LogAnalyzer;
import io.github.sayamrina.solutiondesk.support.Sla;
import io.github.sayamrina.solutiondesk.support.Ticket;
import io.github.sayamrina.solutiondesk.support.TicketRepository;
import io.github.sayamrina.solutiondesk.support.TicketService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The support dashboard: what needs attention now. */
@Controller
public class HomeController {

    public record Row(Ticket ticket, Sla sla) { }

    private final TicketRepository tickets;
    private final TicketService ticketService;
    private final LearningRepository learning;
    private final LogAnalyzer logs;
    private final Clock clock;

    public HomeController(TicketRepository tickets, TicketService ticketService, LearningRepository learning, LogAnalyzer logs,
                          Clock clock) {
        this.tickets = tickets;
        this.ticketService = ticketService;
        this.learning = learning;
        this.logs = logs;
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(Model model) {
        Instant now = clock.instant();
        List<Row> open = tickets.all().stream().filter(t -> t.status().open())
                .map(t -> new Row(t, ticketService.sla(t)))
                .sorted(Comparator.comparing((Row r) -> r.sla().respondedAt() != null).thenComparing(r -> r.ticket().priority())
                        .thenComparing(r -> r.sla().due()))
                .toList();
        var errors24h = logs.groups().stream().filter(g -> g.lastSeen().isAfter(now.minus(Duration.ofHours(24)))).toList();
        model.addAttribute("open", open);
        model.addAttribute("openCount", open.size());
        model.addAttribute("urgentCount", open.stream().filter(r -> r.ticket().priority().compareTo("P2") <= 0).count());
        model.addAttribute("awaitingResponse", open.stream().filter(r -> r.sla().respondedAt() == null).count());
        model.addAttribute("overdue", open.stream().filter(r -> r.sla().state() == Sla.State.OVERDUE).count());
        model.addAttribute("escalated", open.stream().filter(r -> r.ticket().status() == Ticket.Status.ESCALATED).count());
        model.addAttribute("errors", errors24h.stream().limit(6).toList());
        model.addAttribute("errorEvents", errors24h.stream().mapToInt(LogAnalyzer.ErrorGroup::count).sum());
        model.addAttribute("activity", tickets.recentActivity(8));
        model.addAttribute("courses", learning.courses().size());
        model.addAttribute("learners", learning.learners().size());
        return "home";
    }
}
