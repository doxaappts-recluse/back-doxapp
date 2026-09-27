package pe.dcs.app.features.person.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.person.dto.TagDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.ColorUtils;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Núcleo 01 · TagService. Etiquetas de personas: nombre único por organización sin importar mayúsculas [V14]. */
@Service
@RequiredArgsConstructor
public class TagService {

    static final String MODULE = "PERSON_TAGS";
    static final String ENTITY = "Tag";
    static final String DEFAULT_COLOR = "#1677FF";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TagDtos.Response> list(AccessScope scope) {
        return jdbc.query(base() + " where t.organization_id = :org order by lower(t.name)", new MapSqlParameterSource("org", scope.organizationId()), (rs, i) -> map(rs));
    }

    @Transactional
    public TagDtos.Response create(AuthenticatedActor actor, AccessScope scope, TagDtos.Request r) {
        String name = name(r);
        String color = color(r);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into tag (id, organization_id, name, color, created_at, created_by, version) values (:id, :org, :name, :color, :at, :by, 0)",
                    new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()).addValue("name", name).addValue("color", color)
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.tag.nameTaken", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, id, scope.organizationId(), null, diff("name", name)));
        return get(scope, id);
    }

    @Transactional
    public TagDtos.Response update(AuthenticatedActor actor, AccessScope scope, UUID id, TagDtos.Request r) {
        TagDtos.Response cur = get(scope, id);
        String name = name(r);
        String color = color(r);
        int n;
        try {
            n = jdbc.update("""
                    update tag set name = :name, color = :color, updated_at = :at, updated_by = :by, version = version + 1
                     where id = :id and organization_id = :org and (:v is null or version = :v)""",
                    new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()).addValue("name", name).addValue("color", color)
                            .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.ownerId()).addValue("v", r.version()));
        } catch (DataIntegrityViolationException e) {
            throw new Exceptions("error.tag.nameTaken", HttpStatus.CONFLICT);
        }
        if (n == 0) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, id, scope.organizationId(), null, diff("from", cur.name(), "to", name)));
        return get(scope, id);
    }

    /** Elimina la etiqueta y la quita de las personas que la tenían. */
    @Transactional
    public void delete(AuthenticatedActor actor, AccessScope scope, UUID id) {
        TagDtos.Response cur = get(scope, id);
        jdbc.update("delete from person_tag where tag_id = :id", new MapSqlParameterSource("id", id));
        jdbc.update("delete from tag where id = :id and organization_id = :org", new MapSqlParameterSource("id", id).addValue("org", scope.organizationId()));
        audit.record(new AuditService.Command(MODULE, "DELETE", ENTITY, id, scope.organizationId(), null, diff("name", cur.name(), "persons", cur.usage())));
    }

    private TagDtos.Response get(AccessScope scope, UUID id) {
        return jdbc.query(base() + " where t.organization_id = :org and t.id = :id", new MapSqlParameterSource("org", scope.organizationId()).addValue("id", id),
                (rs, i) -> map(rs)).stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private static String base() {
        return "select t.id, t.name, t.color, t.version, t.created_at, (select count(*) from person_tag pt where pt.tag_id = t.id) as usage from tag t";
    }

    private static TagDtos.Response map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TagDtos.Response((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("color"), rs.getLong("usage"),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant());
    }

    private static String name(TagDtos.Request r) {
        String n = r == null || r.name() == null ? "" : r.name().trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "nombre");
        }
        if (n.length() > 40) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "nombre", 40);
        }
        return n;
    }

    private static String color(TagDtos.Request r) {
        if (r == null || r.color() == null || r.color().isBlank()) {
            return DEFAULT_COLOR;
        }
        if (!ColorUtils.isHex(r.color().trim())) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "color");
        }
        return ColorUtils.normalize(r.color().trim());
    }

    private static Map<String, Object> diff(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
