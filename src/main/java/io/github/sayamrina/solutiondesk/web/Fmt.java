package io.github.sayamrina.solutiondesk.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Date helpers for the templates, used as ${@fmt.dt(...)}. */
@Component("fmt")
public class Fmt {

    private final Clock clock;
    private final DateTimeFormatter dateTime;
    private final DateTimeFormatter time;

    public Fmt(Clock clock) {
        this.clock = clock;
        this.dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(clock.getZone());
        this.time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(clock.getZone());
    }

    public String dt(Instant t) {
        return t == null ? "" : dateTime.format(t);
    }

    public String time(Instant t) {
        return t == null ? "" : time.format(t);
    }

    public String ago(Instant t) {
        if (t == null) return "";
        Duration d = Duration.between(t, clock.instant());
        if (d.isNegative()) return "in " + human(d.negated());
        return human(d) + " ago";
    }

    public boolean overdue(Instant due) {
        return due != null && clock.instant().isAfter(due);
    }

    private static String human(Duration d) {
        if (d.toMinutes() < 1) return d.toSeconds() + " s";
        if (d.toHours() < 1) return d.toMinutes() + " min";
        if (d.toDays() < 1) return d.toHours() + " h";
        return d.toDays() + " d";
    }
}
