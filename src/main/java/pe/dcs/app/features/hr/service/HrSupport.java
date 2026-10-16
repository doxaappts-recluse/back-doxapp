package pe.dcs.app.features.hr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.auth.service.SecretCipher;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** M17 · Utilidades comunes de Personal (HR_STAFF), Vacaciones/permisos (HR_LEAVE) y Planilla (HR_PAYROLL). Mismo patrón que {@code FacilitySupport} (M16). */
@Component
@RequiredArgsConstructor
public class HrSupport {

    public static final String MOD_STAFF = "HR_STAFF";
    public static final String MOD_LEAVE = "HR_LEAVE";
    public static final String MOD_PAYROLL = "HR_PAYROLL";

    /** vacationDaysPerYear (config org, default 30) [spec entidad LeaveBalance]: sin un endpoint de política aparte, el saldo se crea con este valor por defecto y `HR_LEAVE#E` lo puede ajustar por sede/año. */
    public static final BigDecimal DEFAULT_VACATION_DAYS = new BigDecimal("30");

    private static final Map<String, DayOfWeek> DOW = Map.of("MON", DayOfWeek.MONDAY, "TUE", DayOfWeek.TUESDAY, "WED", DayOfWeek.WEDNESDAY,
            "THU", DayOfWeek.THURSDAY, "FRI", DayOfWeek.FRIDAY, "SAT", DayOfWeek.SATURDAY, "SUN", DayOfWeek.SUNDAY);

    private final NamedParameterJdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final Clock clock;

    public static String visibleByBranch(AccessScope scope, MapSqlParameterSource ps, String alias) {
        StringBuilder sb = new StringBuilder(alias + ".organization_id = :org");
        ps.addValue("org", scope.organizationId());
        if (!scope.allBranches()) {
            ps.addValue("scopeBranches", branchIdsOrNone(scope));
            sb.append(" and ").append(alias).append(".branch_id in (:scopeBranches)");
        }
        return sb.toString();
    }

    public static List<UUID> branchIdsOrNone(AccessScope scope) {
        return scope.branchIds() == null || scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds());
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** [V10/V11/M17-T11] días hábiles entre start y end (inclusive) según `organization.working_days` menos `org_holiday`. */
    public int businessDays(UUID orgId, LocalDate start, LocalDate end) {
        Set<DayOfWeek> working = workingDays(orgId);
        Set<LocalDate> holidays = new HashSet<>();
        for (java.sql.Date d : jdbc.query("select holiday_date from org_holiday where organization_id = :o and holiday_date between :s and :e",
                new MapSqlParameterSource("o", orgId).addValue("s", java.sql.Date.valueOf(start)).addValue("e", java.sql.Date.valueOf(end)),
                (rs, i) -> rs.getDate(1))) {
            holidays.add(d.toLocalDate());
        }
        int n = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (working.contains(d.getDayOfWeek()) && !holidays.contains(d)) {
                n++;
            }
        }
        return n;
    }

    private Set<DayOfWeek> workingDays(UUID orgId) {
        List<String> r = jdbc.queryForList("select working_days from organization where id = :o", new MapSqlParameterSource("o", orgId), String.class);
        String csv = r.isEmpty() || r.get(0) == null ? "MON,TUE,WED,THU,FRI" : r.get(0);
        Set<DayOfWeek> out = new HashSet<>();
        for (String s : csv.split(",")) {
            DayOfWeek d = DOW.get(s.trim().toUpperCase());
            if (d != null) {
                out.add(d);
            }
        }
        return out.isEmpty() ? Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) : out;
    }

    // ---------------------------------------------------------------- cifrado del dato bancario (mismo patrón que PersonService)

    public String encryptBank(String plain) {
        return hasText(plain) ? cipher.encrypt(plain.trim()) : null;
    }

    public String decryptBank(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return cipher.decrypt(stored);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- comunes

    public static String trim(String s, int max, String label) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > max) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, label, max);
        }
        return t;
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public static BigDecimal requiredAmount(String s, String label) {
        if (s == null || s.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, label);
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, label);
        }
    }

    public static BigDecimal optionalAmount(String s, String label) {
        return hasText(s) ? requiredAmount(s, label) : null;
    }

    public static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
