package pe.dcs.app.features.ministry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M11 · Tarea periódica: las verificaciones CLEARED cuyo vencimiento llegó (hoy en la zona de su organización) pasan a EXPIRED, y se avisa a quien lidera y a la administración de
 * cada sede donde la persona sigue asignada a un ministerio que la exige. El aviso se emite una sola vez por verificación y ministerio (clave de deduplicación).
 */
@Service
@RequiredArgsConstructor
public class MinistryMaintenanceService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ScreeningService screenings;

    @Transactional
    public Map<String, Integer> run() {
        Map<String, Integer> out = new LinkedHashMap<>();
        int expired = jdbc.update("update person_screening s set status = 'EXPIRED', updated_at = now(), version = s.version + 1 from organization o where o.id = s.organization_id"
                + " and s.status = 'CLEARED' and s.expires_at is not null and s.expires_at <= (now() at time zone coalesce(o.timezone, 'America/Lima'))::date", new MapSqlParameterSource());
        out.put("expired", expired);
        List<UUID> atRisk = jdbc.queryForList("select s.id from person_screening s where s.status = 'EXPIRED' and exists (select 1 from ministry_assignment a join branch_ministry b on b.id = a.branch_ministry_id"
                + " join ministry m on m.id = b.ministry_id where a.person_id = s.person_id and a.status = 'ACTIVE' and m.requires_screening and (m.screening_type is null or m.screening_type = s.type))",
                new MapSqlParameterSource(), UUID.class);
        int notified = 0;
        for (UUID id : atRisk) {
            notified += screenings.notifyRisk(id);
        }
        out.put("notified", notified);
        return out;
    }
}
