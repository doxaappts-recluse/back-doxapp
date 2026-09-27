package pe.dcs.app.features.visitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.catalog.service.CatalogUsageChecker;

import java.util.UUID;

/** M23 [V6]: un ítem de "cómo nos conocieron" o "motivo de archivo" usado por algún caso no se puede borrar. */
@Component
@RequiredArgsConstructor
public class VisitorCatalogUsage implements CatalogUsageChecker {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean inUse(UUID organizationId, String type, String code) {
        String col = switch (type) {
            case "VISITOR_SOURCE" -> "how_arrived";
            case "VISITOR_ARCHIVE_REASON" -> "archive_reason";
            default -> null;
        };
        if (col == null) {
            return false;
        }
        Integer n = jdbc.queryForObject("select count(*) from visitor_case where organization_id = :o and " + col + " = :c",
                new MapSqlParameterSource("o", organizationId).addValue("c", code), Integer.class);
        return n != null && n > 0;
    }
}
