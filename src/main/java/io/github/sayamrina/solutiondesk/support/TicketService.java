package io.github.sayamrina.solutiondesk.support;

import io.github.sayamrina.solutiondesk.support.Ticket.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ticket workflow: allowed status changes, escalation rules and the activity log. */
@Service
public class TicketService {

    public static final String SUPPORT = "Support";
    public static final List<String> TEAMS = List.of(SUPPORT, "Hosting", "Development");

    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.NEW, EnumSet.of(Status.IN_ANALYSIS),
            Status.IN_ANALYSIS, EnumSet.of(Status.WAITING_FOR_CUSTOMER, Status.ESCALATED, Status.RESOLVED),
            Status.WAITING_FOR_CUSTOMER, EnumSet.of(Status.IN_ANALYSIS, Status.RESOLVED),
            Status.ESCALATED, EnumSet.of(Status.IN_ANALYSIS, Status.RESOLVED),
            Status.RESOLVED, EnumSet.of(Status.CLOSED, Status.IN_ANALYSIS),
            Status.CLOSED, EnumSet.noneOf(Status.class));

    private final TicketRepository repository;
    private final Clock clock;

    public TicketService(TicketRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public static Set<Status> nextStatuses(Status from) {
        return TRANSITIONS.get(from);
    }

    public Ticket get(String key) {
        return repository.byKey(key).orElseThrow(() -> new IllegalArgumentException("unknown ticket " + key));
    }

    @Transactional
    public Ticket create(long customerId, String summary, String description, int impact, int urgency, String errorCode,
                         String requestId, String kbSlug, String actor) {
        Instant now = clock.instant();
        String key = "SD-" + (101 + repository.count());
        Ticket t = new Ticket(0, key, customerId, null, summary, description, impact, urgency, Priority.of(impact, urgency),
                Status.NEW, SUPPORT, blankToNull(errorCode), blankToNull(requestId), blankToNull(kbSlug), null, null, null,
                now, now);
        long id = repository.insert(t);
        repository.addEvent(id, now, actor, "created", "Ticket created with priority " + t.priority());
        return get(key);
    }

    @Transactional
    public Ticket comment(String key, String actor, String text) {
        Ticket t = get(key);
        require(!text.isBlank(), "comment must not be empty");
        repository.addEvent(t.id(), clock.instant(), actor, "comment", text.strip());
        return touch(t);
    }

    @Transactional
    public Ticket reprioritise(String key, String actor, int impact, int urgency) {
        Ticket t = get(key);
        String priority = Priority.of(impact, urgency);
        Ticket updated = copy(t, impact, urgency, priority, t.status(), t.team(), t.rootCause(), t.workaround(), t.resolution());
        repository.update(updated);
        repository.addEvent(t.id(), clock.instant(), actor, "priority",
                "Priority " + t.priority() + " → " + priority + " (impact " + impact + ", urgency " + urgency + ")");
        return get(key);
    }

    /**
     * Moves a ticket to the next status. Escalation needs a target team and a note with the analysis so far;
     * resolving needs a root cause and a resolution, so every closed ticket teaches something.
     */
    @Transactional
    public Ticket transition(String key, String actor, Status to, String team, String note, String rootCause,
                             String workaround, String resolution) {
        Ticket t = get(key);
        require(nextStatuses(t.status()).contains(to), "a ticket cannot go from " + t.status().label() + " to " + to.label());
        String newTeam = t.team();
        if (to == Status.ESCALATED) {
            require(team != null && !team.equals(SUPPORT) && TEAMS.contains(team), "escalation needs Hosting or Development");
            require(note != null && !note.isBlank(), "escalation needs a note with the analysis so far");
            newTeam = team;
        } else if (to == Status.IN_ANALYSIS) {
            newTeam = SUPPORT;
        }
        String rc = pick(rootCause, t.rootCause());
        String wa = pick(workaround, t.workaround());
        String res = pick(resolution, t.resolution());
        if (to == Status.RESOLVED) {
            require(rc != null, "resolving needs a root cause");
            require(res != null, "resolving needs a resolution");
        }
        Ticket updated = copy(t, t.impact(), t.urgency(), t.priority(), to, newTeam, rc, wa, res);
        repository.update(updated);
        String text = t.status().label() + " → " + to.label() + (to == Status.ESCALATED ? " (" + newTeam + ")" : "");
        if (note != null && !note.isBlank()) text += ": " + note.strip();
        repository.addEvent(t.id(), clock.instant(), actor, "status", text);
        return get(key);
    }

    private Ticket touch(Ticket t) {
        Ticket updated = copy(t, t.impact(), t.urgency(), t.priority(), t.status(), t.team(), t.rootCause(), t.workaround(),
                t.resolution());
        repository.update(updated);
        return get(t.key());
    }

    private Ticket copy(Ticket t, int impact, int urgency, String priority, Status status, String team, String rootCause,
                        String workaround, String resolution) {
        return new Ticket(t.id(), t.key(), t.customerId(), t.customerName(), t.summary(), t.description(), impact, urgency,
                priority, status, team, t.errorCode(), t.requestId(), t.kbSlug(), rootCause, workaround, resolution,
                t.createdAt(), clock.instant());
    }

    private static String pick(String value, String current) {
        return value == null || value.isBlank() ? current : value.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
}
