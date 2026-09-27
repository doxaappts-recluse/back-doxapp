package pe.dcs.app.features.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** M20 · Vistas guardadas: un reporte + sus filtros con un nombre, propias de cada usuario (no visibles a otros [V9-saved]). */
@Service
@RequiredArgsConstructor
public class SavedReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ReportDtos.SavedReportView> list(AccessScope scope) {
        return jdbc.query("select * from saved_report where organization_id = :o and person_id = :p order by name",
                new MapSqlParameterSource("o", scope.organizationId()).addValue("p", scope.personId()), (rs, i) -> map(rs));
    }

    @Transactional
    public ReportDtos.SavedReportView create(AccessScope scope, ReportDtos.SavedReportRequest req) {
        String name = ReportSupport.hasText(req.name()) ? req.name().trim() : null;
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into saved_report (id, organization_id, person_id, code, name, filters, created_at) values (:id, :o, :p, :c, :n, cast(:f as jsonb), :at)",
                    new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("p", scope.personId()).addValue("c", req.code())
                            .addValue("n", name).addValue("f", writeJson(req.filters())).addValue("at", Timestamp.from(clock.instant())));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.report.savedNameTaken", HttpStatus.CONFLICT);                                                    // [V9-saved]
        }
        return jdbc.query("select * from saved_report where id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs)).get(0);
    }

    @Transactional
    public void delete(AccessScope scope, UUID id) {
        jdbc.update("delete from saved_report where id = :id and organization_id = :o and person_id = :p",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("p", scope.personId()));
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReportDtos.SavedReportView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            ReportDtos.RunFilters f = mapper.readValue(rs.getString("filters"), ReportDtos.RunFilters.class);
            return new ReportDtos.SavedReportView((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name"), f, rs.getTimestamp("created_at").toInstant());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
