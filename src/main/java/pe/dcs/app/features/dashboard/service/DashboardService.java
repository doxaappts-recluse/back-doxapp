package pe.dcs.app.features.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.dashboard.dto.DashboardPref;
import pe.dcs.app.features.dashboard.dto.DashboardResponse;
import pe.dcs.app.features.dashboard.dto.WidgetResult;
import pe.dcs.app.features.dashboard.widget.DashboardWidgetProvider;
import pe.dcs.app.features.dashboard.widget.WidgetContext;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M01 · orquestador del panel: reúne los widgets de los proveedores registrados para el nivel del actor, muestra solo los
 * que el actor puede ver (módulo dueño con V), los cachea 5 minutos y aplica su orden/ocultamiento personal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int MAX_MONTHS = 24;

    private final List<DashboardWidgetProvider> providers;
    private final AuthorizationService authz;
    private final DashboardCache cache;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    // ---------------------------------------------------------------- panel de plataforma (N1)
    public DashboardResponse platform(AuthenticatedActor actor, LocalDate from, LocalDate to, boolean refresh) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusMonths(11).withDayOfMonth(1);
        validateRange(start, end);

        List<DashboardWidgetProvider> visible = providersFor("N1").stream()
                .filter(p -> authz.effectiveActions(actor, p.moduleCode()).contains("V") || "DASHBOARD".equals(p.moduleCode()))
                .toList();

        String key = "N1|" + start + "|" + end + "|" + visible.stream().map(DashboardWidgetProvider::code).collect(Collectors.joining(","));
        List<WidgetResult> base = refresh ? null : cache.get(key);
        Instant now = clock.instant();
        if (base == null) {
            WidgetContext ctx = new WidgetContext(actor, start, end, null, now);
            base = new ArrayList<>();
            for (DashboardWidgetProvider p : visible) {
                base.add(compute(p, ctx));
            }
            cache.put(key, List.copyOf(base));
        }
        return new DashboardResponse("N1", start, end, now, personalize(actor, base));
    }

    private WidgetResult compute(DashboardWidgetProvider p, WidgetContext ctx) {
        try {
            return new WidgetResult(p.code(), p.moduleCode(), p.titleKey(), p.size(), p.kind(), p.route(), p.order(), false, "OK", p.data(ctx));
        } catch (RuntimeException e) {
            log.warn("Widget {} no disponible: {}", p.code(), e.toString());
            return new WidgetResult(p.code(), p.moduleCode(), p.titleKey(), p.size(), p.kind(), p.route(), p.order(), false, "UNAVAILABLE", null);
        }
    }

    static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new Exceptions("error.dashboard.rangeInvalid", HttpStatus.BAD_REQUEST);
        }
        if (from.plusMonths(MAX_MONTHS).isBefore(to)) {
            throw new Exceptions("error.dashboard.rangeTooLong", HttpStatus.BAD_REQUEST);
        }
    }

    // ---------------------------------------------------------------- personalización
    private List<WidgetResult> personalize(AuthenticatedActor actor, List<WidgetResult> base) {
        Map<String, DashboardPref> prefs = prefs(actor).stream().collect(Collectors.toMap(DashboardPref::widgetCode, p -> p, (a, b) -> a));
        List<WidgetResult> out = new ArrayList<>();
        for (WidgetResult w : base) {
            DashboardPref p = prefs.get(w.code());
            out.add(p == null ? w : w.with(p.position(), p.hidden()));
        }
        out.sort(Comparator.comparingInt(WidgetResult::position).thenComparing(WidgetResult::code));
        return out;
    }

    public List<DashboardPref> prefs(AuthenticatedActor actor) {
        return jdbc.query("select widget_code, position, hidden from user_dashboard_pref where owner_id = ? order by position",
                (rs, i) -> new DashboardPref(rs.getString(1), rs.getInt(2), rs.getBoolean(3)), actor.ownerId());
    }

    /** Reemplaza la personalización. Cada código debe ser un widget del nivel del actor; el orden es el de la lista. */
    @Transactional
    public List<DashboardPref> savePrefs(AuthenticatedActor actor, List<DashboardPref> requested) {
        List<DashboardPref> list = requested == null ? List.of() : requested;
        Set<String> valid = providersFor(actor.role().levelCode()).stream().map(DashboardWidgetProvider::code).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (DashboardPref p : list) {
            if (p == null || p.widgetCode() == null || !valid.contains(p.widgetCode()) || !seen.add(p.widgetCode())) {
                throw new Exceptions("error.dashboard.widgetUnavailable", HttpStatus.BAD_REQUEST);
            }
        }
        jdbc.update("delete from user_dashboard_pref where owner_id = ?", actor.ownerId());
        Timestamp now = Timestamp.from(clock.instant());
        int pos = 1;
        for (DashboardPref p : list) {
            jdbc.update("insert into user_dashboard_pref (owner_id, widget_code, position, hidden, updated_at) values (?,?,?,?,?)",
                    actor.ownerId(), p.widgetCode(), pos++, p.hidden(), now);
        }
        return prefs(actor);
    }

    private List<DashboardWidgetProvider> providersFor(String level) {
        return providers.stream().filter(p -> level.equals(p.level())).sorted(Comparator.comparingInt(DashboardWidgetProvider::order)).toList();
    }
}
