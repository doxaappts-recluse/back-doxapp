package pe.dcs.app.features.doctemplate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M18 · Plantillas base (N1, {@code template_base}): catálogo de plantillas por defecto de la plataforma que las
 * organizaciones pueden copiar como punto de partida (traza vía {@code document_template.base_template_id}).
 * Gestión exclusiva de SYSTEM_ADMIN vía el módulo CATALOGS (mismo patrón que {@code PlatformCatalogController}).
 */
@Service
@RequiredArgsConstructor
public class TemplateBaseService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TemplateDtos.BaseTemplateView> list(String type) {
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder("where 1 = 1");
        if (type != null && !type.isBlank()) {
            ps.addValue("t", TemplateSupport.type(type));
            w.append(" and type = :t");
        }
        return jdbc.query("select * from template_base " + w + " order by type, locale, version desc", ps, (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public TemplateDtos.BaseTemplateView get(UUID id) {
        return jdbc.query("select * from template_base where id = :id", new MapSqlParameterSource("id", id), (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public TemplateDtos.BaseTemplateView create(AuthenticatedActor actor, TemplateDtos.BaseTemplateRequest req) {
        String type = TemplateSupport.type(req.type());
        String name = TemplateSupport.trim(req.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        String locale = req.locale() == null || req.locale().isBlank() ? "es" : req.locale().trim().toLowerCase();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into template_base (id, type, name, design, locale, version, status, created_at, created_by)"
                        + " values (:id, :t, :n, cast(:d as jsonb), :l, 1, 'DRAFT', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("t", type).addValue("n", name).addValue("d", writeJson(req.design())).addValue("l", locale)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()));
        audit.record(new AuditService.Command("CATALOGS", "TEMPLATE_BASE_CREATE", "TemplateBase", id, null, null, Map.of("type", type)));
        return get(id);
    }

    @Transactional
    public TemplateDtos.BaseTemplateView update(AuthenticatedActor actor, UUID id, TemplateDtos.BaseTemplateRequest req) {
        TemplateDtos.BaseTemplateView cur = get(id);
        if (!"DRAFT".equals(cur.status())) {
            throw new Exceptions("error.template.baseNotDraft", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String name = TemplateSupport.trim(req.name(), 120, "nombre");
        jdbc.update("update template_base set name = coalesce(:n, name), design = cast(:d as jsonb), updated_at = :at, updated_by = :by where id = :id",
                new MapSqlParameterSource("id", id).addValue("n", name).addValue("d", writeJson(req.design())).addValue("at", Timestamp.from(clock.instant()))
                        .addValue("by", actor.contextId()));
        audit.record(new AuditService.Command("CATALOGS", "TEMPLATE_BASE_UPDATE", "TemplateBase", id, null, null, Map.of()));
        return get(id);
    }

    /** [V2] cambia el estado del ciclo DRAFT→PUBLISHED→ARCHIVED; publicar exige que no exista ya otra PUBLISHED del mismo (tipo, idioma). */
    @Transactional
    public TemplateDtos.BaseTemplateView setStatus(AuthenticatedActor actor, UUID id, String statusRaw) {
        TemplateDtos.BaseTemplateView cur = get(id);
        String status = statusRaw == null ? null : statusRaw.trim().toUpperCase();
        if (!Set.of("PUBLISHED", "ARCHIVED", "DRAFT").contains(status)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "estado");
        }
        try {
            jdbc.update("update template_base set status = :s, updated_at = :at, updated_by = :by where id = :id",
                    new MapSqlParameterSource("s", status).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.template.baseAlreadyPublished", HttpStatus.UNPROCESSABLE_ENTITY);                                // [V2]
        }
        audit.record(new AuditService.Command("CATALOGS", "TEMPLATE_BASE_STATUS", "TemplateBase", id, null, null, Map.of("status", status)));
        return get(id);
    }

    private String writeJson(Map<String, Object> design) {
        try {
            return mapper.writeValueAsString(design == null ? Map.of() : design);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private TemplateDtos.BaseTemplateView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TemplateDtos.BaseTemplateView((UUID) rs.getObject("id"), rs.getString("type"), rs.getString("name"), read(rs.getString("design")),
                rs.getString("locale"), rs.getInt("version"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
