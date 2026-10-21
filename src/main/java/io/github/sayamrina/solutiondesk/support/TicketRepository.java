package io.github.sayamrina.solutiondesk.support;

import io.github.sayamrina.solutiondesk.support.Ticket.Event;
import io.github.sayamrina.solutiondesk.support.Ticket.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TicketRepository {

    private static final String SELECT = """
            SELECT t.*, c.name AS customer_name FROM ticket t JOIN customer c ON c.id = t.customer_id""";
    private final JdbcClient db;

    public TicketRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Ticket> all() {
        return db.sql(SELECT + " ORDER BY CASE WHEN t.status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END, t.priority, t.created_at DESC")
                .query(TicketRepository::ticket).list();
    }

    public Optional<Ticket> byKey(String key) {
        return db.sql(SELECT + " WHERE t.ticket_key = ?").param(key).query(TicketRepository::ticket).optional();
    }

    public int count() {
        return db.sql("SELECT COUNT(*) FROM ticket").query(Integer.class).single();
    }

    public long insert(Ticket t) {
        KeyHolder key = new GeneratedKeyHolder();
        db.sql("""
                INSERT INTO ticket (ticket_key, customer_id, summary, description, impact, urgency, priority, status, team,
                                    error_code, request_id, kb_slug, root_cause, workaround, resolution, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(t.key(), t.customerId(), t.summary(), t.description(), t.impact(), t.urgency(), t.priority(),
                        t.status().name(), t.team(), t.errorCode(), t.requestId(), t.kbSlug(), t.rootCause(), t.workaround(),
                        t.resolution(), Timestamp.from(t.createdAt()), Timestamp.from(t.updatedAt()))
                .update(key, "id");
        return key.getKey().longValue();
    }

    public void update(Ticket t) {
        db.sql("""
                UPDATE ticket SET impact = ?, urgency = ?, priority = ?, status = ?, team = ?, error_code = ?, kb_slug = ?,
                       root_cause = ?, workaround = ?, resolution = ?, updated_at = ? WHERE id = ?""")
                .params(t.impact(), t.urgency(), t.priority(), t.status().name(), t.team(), t.errorCode(), t.kbSlug(),
                        t.rootCause(), t.workaround(), t.resolution(), Timestamp.from(t.updatedAt()), t.id())
                .update();
    }

    public void addEvent(long ticketId, Instant at, String actor, String kind, String text) {
        db.sql("INSERT INTO ticket_event (ticket_id, at, actor, kind, text) VALUES (?, ?, ?, ?, ?)")
                .params(ticketId, Timestamp.from(at), actor, kind, text).update();
    }

    public List<Event> events(long ticketId) {
        return db.sql("SELECT * FROM ticket_event WHERE ticket_id = ? ORDER BY at, id").param(ticketId)
                .query((rs, n) -> new Event(rs.getLong("id"), instant(rs, "at"), rs.getString("actor"), rs.getString("kind"),
                        rs.getString("text")))
                .list();
    }

    /** First reply or status change by support, or null if the customer has not had an answer yet. */
    public Instant firstResponseAt(long ticketId) {
        return db.sql("""
                SELECT MIN(at) FROM ticket_event
                WHERE ticket_id = ? AND kind IN ('reply', 'status') AND actor NOT LIKE 'Customer%'""")
                .param(ticketId).query(OffsetDateTime.class).optional().map(OffsetDateTime::toInstant).orElse(null);
    }

    public record Activity(String ticketKey, String summary, Instant at, String actor, String kind, String text) { }

    /** Latest events across all tickets, newest first. */
    public List<Activity> recentActivity(int limit) {
        return db.sql("""
                SELECT t.ticket_key, t.summary, e.at, e.actor, e.kind, e.text
                FROM ticket_event e JOIN ticket t ON t.id = e.ticket_id
                ORDER BY e.at DESC, e.id DESC LIMIT ?""")
                .param(limit)
                .query((rs, n) -> new Activity(rs.getString("ticket_key"), rs.getString("summary"), instant(rs, "at"),
                        rs.getString("actor"), rs.getString("kind"), rs.getString("text")))
                .list();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static Ticket ticket(ResultSet rs, int n) throws SQLException {
        return new Ticket(rs.getLong("id"), rs.getString("ticket_key"), rs.getLong("customer_id"), rs.getString("customer_name"),
                rs.getString("summary"), rs.getString("description"), rs.getInt("impact"), rs.getInt("urgency"),
                rs.getString("priority"), Status.valueOf(rs.getString("status")), rs.getString("team"),
                rs.getString("error_code"), rs.getString("request_id"), rs.getString("kb_slug"), rs.getString("root_cause"),
                rs.getString("workaround"), rs.getString("resolution"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }
}
