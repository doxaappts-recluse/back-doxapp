package pe.dcs.app.features.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.catalog.service.CatalogUsageChecker;

import java.util.UUID;

/** M23 [V6]: un tipo de culto usado por algún culto no se puede borrar. */
@Component
@RequiredArgsConstructor
public class AttendanceCatalogUsage implements CatalogUsageChecker {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean inUse(UUID organizationId, String type, String code) {
        if (!"SERVICE_TYPE".equals(type)) {
            return false;
        }
        Integer n = jdbc.queryForObject("select count(*) from church_service where organization_id = :o and service_type = :c",
                new MapSqlParameterSource("o", organizationId).addValue("c", code), Integer.class);
        return n != null && n > 0;
    }
}
