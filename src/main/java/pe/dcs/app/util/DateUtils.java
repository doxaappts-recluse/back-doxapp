package pe.dcs.app.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class DateUtils {

    private DateUtils() {
    }

    public static LocalDate utcToday() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    public static Instant utcNow() {
        return Instant.now();
    }
}