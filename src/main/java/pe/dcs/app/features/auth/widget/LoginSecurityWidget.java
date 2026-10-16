package pe.dcs.app.features.auth.widget;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.dashboard.widget.DashboardWidgetProvider;
import pe.dcs.app.features.dashboard.widget.WidgetContext;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/** M01 N1 · seguridad de ingreso: intentos fallidos (24 h y 7 días), bloqueos por límite y cuentas bloqueadas ahora. Solo conteos. */
@Component
@RequiredArgsConstructor
public class LoginSecurityWidget implements DashboardWidgetProvider {

    private final JdbcTemplate jdbc;

    @Override public String code() { return "LOGIN_SECURITY"; }
    @Override public String moduleCode() { return "DASHBOARD"; }
    @Override public String level() { return "N1"; }
    @Override public String titleKey() { return "dashboard.widgets.loginSecurity"; }
    @Override public String size() { return "S"; }
    @Override public String kind() { return "STATS"; }
    @Override public int order() { return 60; }

    @Override
    public Object data(WidgetContext ctx) {
        Timestamp now = Timestamp.from(ctx.now());
        Map<String, Object> out = new LinkedHashMap<>();
        jdbc.query("""
                select count(*) filter (where at >= ?::timestamptz - interval '1 day'),
                       count(*) filter (where at >= ?::timestamptz - interval '7 days'),
                       count(*) filter (where at >= ?::timestamptz - interval '1 day' and result = 'RATE_LIMITED')
                  from login_event
                 where result in ('BAD_PASSWORD', 'UNKNOWN_USER', 'LOCKED', 'RATE_LIMITED', 'MFA_FAILED')
                   and at >= ?::timestamptz - interval '7 days'
                """, rs -> {
            out.put("failed24h", rs.getLong(1));
            out.put("failed7d", rs.getLong(2));
            out.put("rateLimited24h", rs.getLong(3));
        }, now, now, now, now);
        Long locked = jdbc.queryForObject("select count(*) from credential where status = 'LOCKED' or locked_until > ?", Long.class, now);
        out.put("lockedNow", locked == null ? 0L : locked);
        return out;
    }
}
