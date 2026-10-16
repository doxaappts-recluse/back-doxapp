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
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * M18 · Plantillas de la organización (N2, {@code document_template}). Cada una versiona su diseño al publicar
 * [V1][V4] (catálogo de variables en {@link TemplateSupport}), y puede marcarse por defecto por (tipo, sede) [V7].
 */
@Service
@RequiredArgsConstructor
public class DocumentTemplateService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final String SELECT = "select t.*, b.name as branch_name from document_template t left join branch b on b.id = t.branch_id ";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DocumentRenderService renderer;
    private final FileStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    /** [V5] imagen suelta para usar en un elemento IMAGE del diseño (se referencia por clave, no por variable). */
    @Transactional
    public TemplateDtos.ImageUploadResult uploadImage(AccessScope scope, String contentType, byte[] data) {
        TemplateSupport.validateImage(contentType, data.length);
        byte[] out = "image/svg+xml".equalsIgnoreCase(contentType) ? TemplateSupport.sanitizeSvg(data) : data;
        String ext = contentType.equals("image/png") ? "png" : contentType.equals("image/webp") ? "webp" : contentType.equals("image/svg+xml") ? "svg" : "jpg";
        String key = "org/" + scope.organizationId() + "/document-templates/" + UUID.randomUUID() + "." + ext;
        storage.put(key, out, contentType);
        return new TemplateDtos.ImageUploadResult(key, contentType, out.length);
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateDtos.TemplateView> search(AccessScope scope, TemplateDtos.TemplateSearch req) {
        TemplateDtos.TemplateSearch.TemplateFilters f = req == null || req.filters() == null ? new TemplateDtos.TemplateSearch.TemplateFilters(null, null, null) : req.filters();
        MapSqlParameterSource ps = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(TemplateSupport.visibleByBranch(scope, ps, "t", true));
        if (f.branchId() != null) {
            w.append(" and t.branch_id = :fb");
            ps.addValue("fb", f.branchId());
        }
        if (TemplateSupport.hasText(f.type())) {
            w.append(" and t.type = :ft");
            ps.addValue("ft", TemplateSupport.type(f.type()));
        }
        if (TemplateSupport.hasText(f.status())) {
            w.append(" and t.status = :fs");
            ps.addValue("fs", f.status().trim().toUpperCase());
        }
        Long total = jdbc.queryForObject("select count(*) from document_template t where " + w, ps, Long.class);
        int size = req == null || req.pagination() == null ? 20 : Math.max(1, Math.min(req.pagination().getSize(), 200));
        int page = req == null || req.pagination() == null ? 0 : Math.max(0, req.pagination().getPage());
        ps.addValue("lim", size).addValue("off", (long) page * size);
        java.util.List<TemplateDtos.TemplateView> rows = jdbc.query(SELECT + "where " + w + " order by t.type, t.name limit :lim offset :off", ps, (rs, i) -> map(rs));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) size), size, page));
    }

    @Transactional(readOnly = true)
    public TemplateDtos.TemplateView get(AccessScope scope, UUID id) {
        MapSqlParameterSource ps = new MapSqlParameterSource("id", id);
        String w = TemplateSupport.visibleByBranch(scope, ps, "t", true);
        return jdbc.query(SELECT + "where t.id = :id and " + w, ps, (rs, i) -> map(rs)).stream().findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Crea en blanco o copiando el diseño de una base [D2]; queda en DRAFT. */
    @Transactional
    public TemplateDtos.TemplateView create(AuthenticatedActor actor, AccessScope scope, TemplateDtos.TemplateRequest req, UUID fromBaseId) {
        String type = TemplateSupport.type(req.type());
        String name = TemplateSupport.trim(req.name(), 120, "nombre");
        if (name == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        Map<String, Object> design = req.design();
        String locale = req.locale() == null || req.locale().isBlank() ? "es" : req.locale().trim().toLowerCase();
        if (fromBaseId != null) {
            Map<String, Object> baseDesign = jdbc.query("select design::text from template_base where id = :id",
                    new MapSqlParameterSource("id", fromBaseId), (rs, i) -> read(rs.getString(1))).stream().findFirst()
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
            design = baseDesign;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into document_template (id, organization_id, branch_id, base_template_id, type, name, design, locale, version, status,"
                        + " is_default, created_at, created_by) values (:id, :org, :b, :base, :t, :n, cast(:d as jsonb), :l, 1, 'DRAFT', false, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()).addValue("b", req.branchId()).addValue("base", fromBaseId)
                        .addValue("t", type).addValue("n", name).addValue("d", writeJson(design)).addValue("l", locale)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "TEMPLATE_CREATE", "DocumentTemplate", id, scope.organizationId(), req.branchId(), Map.of("type", type)));
        return get(scope, id);
    }

    /** Edita el diseño de una plantilla en DRAFT (una publicada solo se reemplaza publicando una nueva versión vía {@link #update}). */
    @Transactional
    public TemplateDtos.TemplateView update(AuthenticatedActor actor, AccessScope scope, UUID id, TemplateDtos.TemplateRequest req) {
        TemplateDtos.TemplateView cur = get(scope, id);
        String name = TemplateSupport.trim(req.name(), 120, "nombre");
        jdbc.update("update document_template set name = coalesce(:n, name), design = cast(:d as jsonb), status = 'DRAFT', updated_at = :at, updated_by = :by,"
                        + " version_lock = version_lock + 1 where id = :id",
                new MapSqlParameterSource("n", name).addValue("d", writeJson(req.design())).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "TEMPLATE_UPDATE", "DocumentTemplate", id, scope.organizationId(), cur.branchId(), Map.of()));
        return get(scope, id);
    }

    /** [V6] la vista previa renderiza el diseño con datos de ejemplo; publicar exige haberla generado sin error primero. */
    @Transactional(readOnly = true)
    public TemplateDtos.PreviewResult preview(AccessScope scope, TemplateDtos.PreviewRequest req, String type) {
        try {
            TemplateSupport.validateDesign(type, req.design());
            Map<String, String> sample = sampleVariables(type, scope);
            byte[] qr = renderer.qrPng("https://doxapp.example/v/SAMPLE0000", 200);
            DocumentRenderService.RenderResult r = renderer.render(req.design(), sample, qr, "SAMPLE");
            return new TemplateDtos.PreviewResult(true, Base64Util.encode(r.pdf()), null);
        } catch (Exceptions e) {
            return new TemplateDtos.PreviewResult(false, null, e.getMessage());
        } catch (Exception e) {
            return new TemplateDtos.PreviewResult(false, null, "error.template.renderFailed");
        }
    }

    /** [V1][V4][V6] valida variables y exige una vista previa exitosa antes de subir a PUBLISHED (versión++). */
    @Transactional
    public TemplateDtos.TemplateView publish(AuthenticatedActor actor, AccessScope scope, UUID id) {
        TemplateDtos.TemplateView cur = get(scope, id);
        TemplateSupport.validateDesign(cur.type(), cur.design());                                                        // [V1][V4]
        try {
            Map<String, String> sample = sampleVariables(cur.type(), scope);
            byte[] qr = renderer.qrPng("https://doxapp.example/v/SAMPLE0000", 200);
            renderer.render(cur.design(), sample, qr, "SAMPLE");                                                         // [V6] debe renderizar sin lanzar
        } catch (Exceptions e) {
            throw e;
        } catch (Exception e) {
            throw new Exceptions("error.template.renderFailed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update document_template set status = 'PUBLISHED', version = version + 1, updated_at = :at, updated_by = :by, version_lock = version_lock + 1 where id = :id",
                new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "TEMPLATE_PUBLISH", "DocumentTemplate", id, scope.organizationId(), cur.branchId(), Map.of()));
        return get(scope, id);
    }

    /** [V7] una plantilla por defecto por (tipo, sede); solo puede ser default una PUBLISHED. */
    @Transactional
    public TemplateDtos.TemplateView setDefault(AuthenticatedActor actor, AccessScope scope, UUID id) {
        TemplateDtos.TemplateView cur = get(scope, id);
        if (!"PUBLISHED".equals(cur.status())) {
            throw new Exceptions("error.template.notPublished", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        jdbc.update("update document_template set is_default = false where organization_id = :org and type = :t and coalesce(branch_id, :zero) = coalesce(:b, :zero)",
                new MapSqlParameterSource("org", scope.organizationId()).addValue("t", cur.type()).addValue("b", cur.branchId())
                        .addValue("zero", new UUID(0, 0)));
        try {
            jdbc.update("update document_template set is_default = true, updated_at = :at, updated_by = :by, version_lock = version_lock + 1 where id = :id",
                    new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        } catch (DuplicateKeyException e) {
            throw new Exceptions("error.template.defaultExists", HttpStatus.UNPROCESSABLE_ENTITY);                                      // [V7]
        }
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "TEMPLATE_DEFAULT", "DocumentTemplate", id, scope.organizationId(), cur.branchId(), Map.of()));
        return get(scope, id);
    }

    /** [V9] archiva en vez de borrar cuando ya tiene documentos emitidos; borra de verdad solo si nunca se usó. */
    @Transactional
    public void archiveOrDelete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        TemplateDtos.TemplateView cur = get(scope, id);
        Integer used = jdbc.queryForObject("select count(*) from issued_document where template_id = :id", new MapSqlParameterSource("id", id), Integer.class);
        if (used != null && used > 0) {
            jdbc.update("update document_template set status = 'ARCHIVED', is_default = false, updated_at = :at, updated_by = :by, version_lock = version_lock + 1 where id = :id",
                    new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
            audit.record(new AuditService.Command(TemplateSupport.MODULE, "TEMPLATE_ARCHIVE", "DocumentTemplate", id, scope.organizationId(), cur.branchId(), Map.of()));
            return;
        }
        jdbc.update("delete from document_template where id = :id", new MapSqlParameterSource("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "TEMPLATE_DELETE", "DocumentTemplate", id, scope.organizationId(), cur.branchId(), Map.of()));
    }

    // ---------------------------------------------------------------- resolución para IssuedDocumentService

    @Transactional(readOnly = true)
    public Map<String, Object> designAndVersion(UUID templateId) {
        return jdbc.query("select design::text, version from document_template where id = :id", new MapSqlParameterSource("id", templateId), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("design", read(rs.getString(1)));
            m.put("version", rs.getInt(2));
            return m;
        }).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** [V10] plantilla PUBLISHED por defecto para (organización, tipo, sede); si no hay una específica de la sede, usa la de toda la organización. */
    @Transactional(readOnly = true)
    public UUID defaultTemplateId(UUID orgId, String type, UUID branchId) {
        java.util.List<UUID> rows = jdbc.queryForList("select id from document_template where organization_id = :o and type = :t and status = 'PUBLISHED' and is_default"
                        + " and branch_id = :b order by updated_at desc limit 1", new MapSqlParameterSource("o", orgId).addValue("t", type).addValue("b", branchId), UUID.class);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        rows = jdbc.queryForList("select id from document_template where organization_id = :o and type = :t and status = 'PUBLISHED' and is_default and branch_id is null"
                + " order by updated_at desc limit 1", new MapSqlParameterSource("o", orgId).addValue("t", type), UUID.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, String> sampleVariables(String type, AccessScope scope) {
        String orgName = jdbc.queryForObject("select name from organization where id = :o", new MapSqlParameterSource("o", scope.organizationId()), String.class);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("org.displayName", orgName);
        m.put("org.name", orgName);
        m.put("branch.name", "Sede de ejemplo");
        m.put("number", "0000001");
        m.put("date", java.time.LocalDate.now(clock).toString());
        for (String v : TemplateSupport.catalogOf(type).specific()) {
            m.put(v, "—");
        }
        String primary = TemplateSupport.catalogOf(type).primary();
        if (primary != null) {
            m.put(primary, "Nombre de ejemplo");
        }
        return m;
    }

    private String writeJson(Map<String, Object> design) {
        try {
            return mapper.writeValueAsString(design == null ? Map.of() : design);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private TemplateDtos.TemplateView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TemplateDtos.TemplateView((UUID) rs.getObject("id"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"),
                (UUID) rs.getObject("base_template_id"), rs.getString("type"), rs.getString("name"), read(rs.getString("design")), rs.getString("locale"),
                rs.getInt("version"), rs.getString("status"), rs.getBoolean("is_default"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant(), rs.getLong("version_lock"));
    }

    /** Envoltorio minúsculo para no acoplar el servicio a una utilería externa de base64. */
    private static final class Base64Util {
        static String encode(byte[] b) {
            return java.util.Base64.getEncoder().encodeToString(b);
        }
    }
}
