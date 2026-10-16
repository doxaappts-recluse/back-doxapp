package pe.dcs.app.features.support.service;

import pe.dcs.app.features.support.domain.SupportCase;
import pe.dcs.app.features.support.domain.SupportPriority;
import pe.dcs.app.features.support.domain.SupportStatus;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Reglas de tiempo de M22: SLA de primera respuesta por prioridad. URGENT 4 h · HIGH 1 día · NORMAL 3 días · LOW 5 días hábiles. */
public final class SupportRules {

    private SupportRules() {
    }

    public static Instant slaDue(Instant from, SupportPriority priority) {
        return switch (priority) {
            case URGENT -> from.plus(Duration.ofHours(4));
            case HIGH -> from.plus(Duration.ofDays(1));
            case NORMAL -> from.plus(Duration.ofDays(3));
            case LOW -> addBusinessDays(from, 5);
        };
    }

    static Instant addBusinessDays(Instant from, int days) {
        ZonedDateTime z = from.atZone(ZoneOffset.UTC);
        int left = days;
        while (left > 0) {
            z = z.plusDays(1);
            if (z.getDayOfWeek() != DayOfWeek.SATURDAY && z.getDayOfWeek() != DayOfWeek.SUNDAY) {
                left--;
            }
        }
        return z.toInstant();
    }

    /** SLA vencido: sin primera respuesta y ya pasó el plazo (caso abierto), o la primera respuesta llegó tarde. */
    public static boolean breached(SupportCase c, Instant now) {
        if (c.getFirstResponseAt() != null) {
            return c.getFirstResponseAt().isAfter(c.getSlaDueAt());
        }
        boolean waiting = c.getStatus() == SupportStatus.OPEN || c.getStatus() == SupportStatus.WAITING_PLATFORM;
        return waiting && now.isAfter(c.getSlaDueAt());
    }

    public static String code(long caseNumber) {
        return String.format("CS-%06d", caseNumber);
    }
}
