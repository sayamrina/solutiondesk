package io.github.sayamrina.solutiondesk.web;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.learning.LearningRepository;
import io.github.sayamrina.solutiondesk.logging.LogAnalyzer;
import io.github.sayamrina.solutiondesk.support.Diagnostics;
import io.github.sayamrina.solutiondesk.support.JiraExport;
import io.github.sayamrina.solutiondesk.support.KnowledgeBase;
import io.github.sayamrina.solutiondesk.support.Sla;
import io.github.sayamrina.solutiondesk.support.Ticket;
import io.github.sayamrina.solutiondesk.support.TicketRepository;
import io.github.sayamrina.solutiondesk.support.TicketService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The support desk: tickets, log analysis, SQL diagnostics and the knowledge base. */
@Controller
public class SupportController {

    private static final String ACTOR = "Support · You";

    private final TicketRepository tickets;
    private final TicketService ticketService;
    private final LogAnalyzer logs;
    private final Diagnostics diagnostics;
    private final KnowledgeBase kb;
    private final JiraExport jira;
    private final JdbcClient db;

    public SupportController(TicketRepository tickets, TicketService ticketService, LogAnalyzer logs, Diagnostics diagnostics,
                             KnowledgeBase kb, JiraExport jira, JdbcClient db, LearningRepository learning) {
        this.tickets = tickets;
        this.ticketService = ticketService;
        this.logs = logs;
        this.diagnostics = diagnostics;
        this.kb = kb;
        this.jira = jira;
        this.db = db;
    }

    // ---- tickets ----

    public record Row(Ticket ticket, Sla sla) { }

    @GetMapping("/support/tickets")
    public String tickets(@RequestParam(defaultValue = "open") String view, @RequestParam(required = false) String q,
                          @RequestParam(required = false) String priority, @RequestParam(required = false) Long customer,
                          Model model) {
        String query = q == null ? "" : q.strip().toLowerCase();
        List<Row> rows = tickets.all().stream()
                .filter(t -> switch (view) {
                    case "all" -> true;
                    case "closed" -> !t.status().open();
                    default -> t.status().open();
                })
                .filter(t -> priority == null || priority.isBlank() || t.priority().equals(priority))
                .filter(t -> customer == null || t.customerId() == customer)
                .filter(t -> query.isEmpty() || (t.key() + " " + t.summary() + " " + t.description() + " "
                        + (t.errorCode() == null ? "" : t.errorCode())).toLowerCase().contains(query))
                .map(t -> new Row(t, ticketService.sla(t)))
                .toList();
        model.addAttribute("rows", rows);
        model.addAttribute("view", view);
        model.addAttribute("q", q);
        model.addAttribute("priority", priority);
        model.addAttribute("customer", customer);
        model.addAttribute("customers", db.sql("SELECT id, name FROM customer ORDER BY name").query().listOfRows());
        return "support/tickets";
    }

    @GetMapping("/support/tickets/{key}")
    public String ticket(@PathVariable String key, Model model) {
        Ticket t = ticketService.get(key);
        model.addAttribute("t", t);
        model.addAttribute("events", tickets.events(t.id()));
        model.addAttribute("next", TicketService.nextStatuses(t.status()));
        model.addAttribute("teams", TicketService.TEAMS);
        model.addAttribute("trail", t.requestId() == null ? List.of() : logs.trail(t.requestId()));
        model.addAttribute("article", t.kbSlug() == null ? null : kb.bySlug(t.kbSlug()).orElse(null));
        model.addAttribute("sla", ticketService.sla(t));
        return "support/ticket";
    }

    @GetMapping("/support/tickets/new")
    public String newTicket(@RequestParam(required = false) String code, @RequestParam(required = false) String requestId,
                            Model model) {
        model.addAttribute("customers", db.sql("SELECT id, name FROM customer ORDER BY name").query().listOfRows());
        model.addAttribute("codes", Arrays.stream(ErrorCode.values()).map(ErrorCode::code).toList());
        model.addAttribute("code", code);
        model.addAttribute("requestId", requestId);
        return "support/new-ticket";
    }

    @PostMapping("/support/tickets")
    public String create(@RequestParam long customerId, @RequestParam String summary, @RequestParam String description,
                         @RequestParam int impact, @RequestParam int urgency, @RequestParam(required = false) String errorCode,
                         @RequestParam(required = false) String requestId) {
        if (summary.isBlank() || description.isBlank()) throw new IllegalStateException("summary and description are required");
        String slug = errorCode == null || errorCode.isBlank() ? null : ErrorCode.fromCode(errorCode).kbSlug();
        Ticket t = ticketService.create(customerId, summary.strip(), description.strip(), impact, urgency, errorCode, requestId,
                slug, ACTOR);
        return "redirect:/support/tickets/" + t.key();
    }

    @PostMapping("/support/tickets/{key}/comment")
    public String comment(@PathVariable String key, @RequestParam String text,
                          @RequestParam(defaultValue = "internal") String visibility) {
        if (visibility.equals("customer")) {
            ticketService.reply(key, ACTOR, text);
        } else {
            ticketService.comment(key, ACTOR, text);
        }
        return "redirect:/support/tickets/" + key + "#activity";
    }

    @PostMapping("/support/tickets/{key}/priority")
    public String priority(@PathVariable String key, @RequestParam int impact, @RequestParam int urgency) {
        ticketService.reprioritise(key, ACTOR, impact, urgency);
        return "redirect:/support/tickets/" + key;
    }

    @PostMapping("/support/tickets/{key}/status")
    public String status(@PathVariable String key, @RequestParam Ticket.Status to, @RequestParam(required = false) String team,
                         @RequestParam(required = false) String note, @RequestParam(required = false) String rootCause,
                         @RequestParam(required = false) String workaround, @RequestParam(required = false) String resolution) {
        ticketService.transition(key, ACTOR, to, team, note, rootCause, workaround, resolution);
        return "redirect:/support/tickets/" + key + "#activity";
    }

    @GetMapping("/support/tickets/export.csv")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"solutiondesk-jira-import.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(jira.csv(tickets.all()));
    }

    // ---- logs ----

    @GetMapping("/support/logs")
    public String logs(@RequestParam(required = false) String q, @RequestParam(required = false) String level, Model model) {
        model.addAttribute("groups", logs.groups());
        model.addAttribute("events", logs.search(q, level, 150));
        model.addAttribute("q", q);
        model.addAttribute("level", level);
        return "support/logs";
    }

    @GetMapping("/support/logs/request/{requestId}")
    public String trail(@PathVariable String requestId, Model model) {
        model.addAttribute("requestId", requestId);
        model.addAttribute("events", logs.trail(requestId));
        model.addAttribute("tickets", tickets.all().stream().filter(t -> requestId.equals(t.requestId())).toList());
        return "support/trail";
    }

    // ---- diagnostics ----

    @GetMapping("/support/diagnostics")
    public String diagnostics(@RequestParam(required = false) String check, @RequestParam Map<String, String> params, Model model) {
        model.addAttribute("checks", diagnostics.checks());
        model.addAttribute("learners", db.sql("SELECT email, name FROM learner ORDER BY name").query().listOfRows());
        model.addAttribute("courseCodes", db.sql("SELECT code FROM course ORDER BY code").query(String.class).list());
        model.addAttribute("params", params);
        if (check != null) {
            var c = diagnostics.check(check).orElseThrow(() -> new IllegalArgumentException("unknown check " + check));
            model.addAttribute("active", c);
            boolean ready = c.params().stream().allMatch(p -> params.containsKey(p) && !params.get(p).isBlank());
            if (ready) model.addAttribute("result", diagnostics.run(c, params));
        }
        return "support/diagnostics";
    }

    // ---- knowledge base ----

    @GetMapping("/support/kb")
    public String kb(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("articles", kb.search(q));
        model.addAttribute("q", q);
        return "support/kb";
    }

    @GetMapping("/support/kb/{slug}")
    public String article(@PathVariable String slug, Model model) {
        var article = kb.bySlug(slug).orElseThrow(() -> new IllegalArgumentException("unknown article " + slug));
        model.addAttribute("article", article);
        model.addAttribute("tickets", tickets.all().stream().filter(t -> slug.equals(t.kbSlug())).toList());
        return "support/article";
    }
}
