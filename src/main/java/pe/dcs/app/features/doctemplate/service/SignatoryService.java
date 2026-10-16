package pe.dcs.app.features.doctemplate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.storage.FileStorageService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M18 · Firmantes ({@code signatory}) [V8]: solo se firma con alguien de esta tabla, referenciando a una persona de {@link PersonLookupService}. */
@Service
@RequiredArgsConstructor
public class SignatoryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonLookupService persons;
    private final FileStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TemplateDtos.SignatoryView> list(AccessScope scope, boolean onlyActive) {
        MapSqlParameterSource ps = new MapSqlParameterSource("org", scope.organizationId());
        String w = onlyActive ? " and s.active" : "";
        return jdbc.query("select s.*, trim(p.first_name || ' ' || p.last_name) as person_name from signatory s join person p on p.id = s.person_id"
                + " where s.organization_id = :org" + w + " order by person_name", ps, (rs, i) -> map(rs));
    }

    @Transactional(readOnly = true)
    public TemplateDtos.SignatoryView get(AccessScope scope, UUID id) {
        return jdbc.query("select s.*, trim(p.first_name || ' ' || p.last_name) as person_name from signatory s join person p on p.id = s.person_id"
                        + " where s.id = :id and s.organization_id = :org", new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()), (rs, i) -> map(rs))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public TemplateDtos.SignatoryView create(AuthenticatedActor actor, AccessScope scope, TemplateDtos.SignatoryRequest req) {
        persons.getVisible(scope, req.personId());
        String title = TemplateSupport.trim(req.title(), 120, "cargo");
        if (title == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "cargo");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into signatory (id, organization_id, person_id, title, active, created_at, created_by) values (:id, :org, :p, :t, true, :at, :by)",
                new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()).addValue("p", req.personId()).addValue("t", title)
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "SIGNATORY_CREATE", "Signatory", id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    @Transactional
    public TemplateDtos.SignatoryView update(AuthenticatedActor actor, AccessScope scope, UUID id, TemplateDtos.SignatoryRequest req) {
        get(scope, id);
        String title = TemplateSupport.trim(req.title(), 120, "cargo");
        jdbc.update("update signatory set title = coalesce(:t, title), updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("t", title).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "SIGNATORY_UPDATE", "Signatory", id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    /** [V5] valida la imagen (png/jpg/svg/webp ≤1MB) y la sanea si es SVG antes de guardarla. */
    @Transactional
    public TemplateDtos.SignatoryView uploadSignature(AuthenticatedActor actor, AccessScope scope, UUID id, String contentType, byte[] data) {
        get(scope, id);
        TemplateSupport.validateImage(contentType, data.length);
        byte[] out = "image/svg+xml".equalsIgnoreCase(contentType) ? TemplateSupport.sanitizeSvg(data) : data;
        String ext = contentType.equals("image/png") ? "png" : contentType.equals("image/webp") ? "webp" : contentType.equals("image/svg+xml") ? "svg" : "jpg";
        String key = "org/" + scope.organizationId() + "/signatories/" + id + "." + ext;
        storage.put(key, out, contentType);
        jdbc.update("update signatory set signature_key = :k, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("k", key).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "SIGNATORY_SIGNATURE", "Signatory", id, scope.organizationId(), null, Map.of()));
        return get(scope, id);
    }

    @Transactional
    public TemplateDtos.SignatoryView setActive(AuthenticatedActor actor, AccessScope scope, UUID id, boolean active) {
        get(scope, id);
        jdbc.update("update signatory set active = :a, updated_at = :at, updated_by = :by, version = version + 1 where id = :id",
                new MapSqlParameterSource("a", active).addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()).addValue("id", id));
        audit.record(new AuditService.Command(TemplateSupport.MODULE, "SIGNATORY_STATUS", "Signatory", id, scope.organizationId(), null, Map.of("active", active)));
        return get(scope, id);
    }

    /** Bytes de la firma para el motor de render (o null si el firmante no tiene una imagen cargada). */
    @Transactional(readOnly = true)
    public byte[] signatureBytes(UUID signatoryId) {
        if (signatoryId == null) {
            return null;
        }
        String key = jdbc.queryForObject("select signature_key from signatory where id = :id", new MapSqlParameterSource("id", signatoryId), String.class);
        if (key == null) {
            return null;
        }
        return storage.get(key).map(FileStorageService.StoredFile::data).orElse(null);
    }

    private TemplateDtos.SignatoryView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TemplateDtos.SignatoryView((UUID) rs.getObject("id"), (UUID) rs.getObject("person_id"), rs.getString("person_name"), rs.getString("title"),
                rs.getString("signature_key") != null, rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }
}
