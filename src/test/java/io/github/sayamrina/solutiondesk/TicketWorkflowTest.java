package io.github.sayamrina.solutiondesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sayamrina.solutiondesk.support.Priority;
import io.github.sayamrina.solutiondesk.support.Ticket;
import io.github.sayamrina.solutiondesk.support.Ticket.Status;
import io.github.sayamrina.solutiondesk.support.TicketService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
class TicketWorkflowTest {

    @Autowired TicketService tickets;

    @Test
    void priorityMatrix() {
        assertThat(Priority.of(1, 1)).isEqualTo("P1");
        assertThat(Priority.of(2, 2)).isEqualTo("P3");
        assertThat(Priority.of(3, 1)).isEqualTo("P3");
        assertThat(Priority.of(3, 3)).isEqualTo("P4");
        assertThat(Priority.firstResponse("P1")).isEqualTo(Duration.ofHours(1));
        assertThatThrownBy(() -> Priority.of(0, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullLifecycleWithEscalation() {
        Ticket t = tickets.create(1, "Test", "Something broke", 2, 1, "SD-IMP-001", null, "kb-imp-001", "test");
        assertThat(t.status()).isEqualTo(Status.NEW);
        assertThat(t.priority()).isEqualTo("P2");

        assertThatThrownBy(() -> tickets.transition(t.key(), "test", Status.RESOLVED, null, null, "rc", null, "res"))
                .hasMessageContaining("cannot go from New");
        tickets.transition(t.key(), "test", Status.IN_ANALYSIS, null, null, null, null, null);
        assertThatThrownBy(() -> tickets.transition(t.key(), "test", Status.ESCALATED, "Development", "", null, null, null))
                .hasMessageContaining("needs a note");
        Ticket escalated = tickets.transition(t.key(), "test", Status.ESCALATED, "Development", "stack trace attached", null, null, null);
        assertThat(escalated.team()).isEqualTo("Development");

        assertThatThrownBy(() -> tickets.transition(t.key(), "test", Status.RESOLVED, null, null, null, null, "fixed"))
                .hasMessageContaining("root cause");
        Ticket resolved = tickets.transition(t.key(), "test", Status.RESOLVED, null, null, "null check missing", null, "fixed in 1.0.1");
        assertThat(resolved.status()).isEqualTo(Status.RESOLVED);
        assertThat(resolved.rootCause()).isEqualTo("null check missing");
    }

    @Test
    void reprioritisingRecomputesPriority() {
        Ticket t = tickets.create(2, "Prio", "x", 3, 3, null, null, null, "test");
        assertThat(t.priority()).isEqualTo("P4");
        assertThat(tickets.reprioritise(t.key(), "test", 1, 1).priority()).isEqualTo("P1");
    }
}
