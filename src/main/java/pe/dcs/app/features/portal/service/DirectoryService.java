package pe.dcs.app.features.portal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.person.service.ConsentService;
import pe.dcs.app.features.portal.dto.PortalDtos.DirectoryEntry;
import pe.dcs.app.features.portal.dto.PortalDtos.DirectoryPreferenceRequest;
import pe.dcs.app.features.portal.dto.PortalDtos.DirectoryPreferenceResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * M24 [V17] · directorio opt-in: oculto por defecto, el miembro decide visibilidad y qué campos mostrar. Nunca
 * incluye menores de edad ni datos fuera de lo elegido (teléfono/correo/foto/grupos).
 */
@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ConsentService consents;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DirectoryPreferenceResponse getPreference(java.util.UUID personId) {
        List<DirectoryPreferenceResponse> l = jdbc.query(
                "select visible, cast(fields as text) as fields from directory_preference where person_id = :p",
                new MapSqlParameterSource("p", personId), (rs, i) -> toResponse(rs.getBoolean("visible"), rs.getString("fields")));
        return l.isEmpty() ? new DirectoryPreferenceResponse(false, false, false, false, false) : l.get(0);
    }

    @Transactional
    public DirectoryPreferenceResponse setPreference(java.util.UUID orgId, java.util.UUID personId, DirectoryPreferenceRequest r) {
        Map<String, Boolean> fields = Map.of("phone", r.phone(), "email", r.email(), "photo", r.photo(), "groups", r.groups());
        Timestamp now = Timestamp.from(clock.instant());
        int n = jdbc.update("update directory_preference set visible = :v, fields = cast(:f as jsonb), updated_at = :now where person_id = :p",
                new MapSqlParameterSource("v", r.visible()).addValue("f", json(fields)).addValue("now", now).addValue("p", personId));
        if (n == 0) {
            jdbc.update("insert into directory_preference (person_id, organization_id, visible, fields, updated_at) values (:p, :o, :v, cast(:f as jsonb), :now)",
                    new MapSqlParameterSource("p", personId).addValue("o", orgId).addValue("v", r.visible()).addValue("f", json(fields)).addValue("now", now));
        }
        if (r.visible()) {                                                                                            // [V17] opt-in explícito → consentimiento
            consents.grant(orgId, personId, ConsentService.DIRECTORY, "PORTAL", personId);
        } else {
            consents.revoke(personId, ConsentService.DIRECTORY, personId);                                            // retirar visibilidad es inmediato
        }
        return getPreference(personId);
    }

    @Transactional(readOnly = true)
    public List<DirectoryEntry> search(java.util.UUID orgId, String q) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", orgId);
        StringBuilder w = new StringBuilder("""
                p.organization_id = :o and dp.visible = true and p.status = 'ACTIVE'
                  and (p.birth_date is null or p.birth_date <= (current_date - interval '18 years'))
                """);
        if (q != null && !q.isBlank()) {
            w.append(" and lower(p.first_name || ' ' || p.last_name) like :q");
            ps.addValue("q", "%" + q.trim().toLowerCase() + "%");
        }
        return jdbc.query("""
                select p.first_name, p.last_name, p.phone, p.email, b.name as branch_name, cast(dp.fields as text) as fields
                from directory_preference dp join person p on p.id = dp.person_id left join branch b on b.id = p.primary_branch_id
                where """ + w + " order by lower(p.first_name)", ps, (rs, i) -> {
            Map<String, Object> f = fields(rs.getString("fields"));
            boolean showPhone = Boolean.TRUE.equals(f.get("phone"));
            boolean showEmail = Boolean.TRUE.equals(f.get("email"));
            return new DirectoryEntry(rs.getString("first_name") + " " + rs.getString("last_name"), showPhone ? rs.getString("phone") : null,
                    showEmail ? rs.getString("email") : null, rs.getString("branch_name"));
        });
    }

    private DirectoryPreferenceResponse toResponse(boolean visible, String fieldsJson) {
        Map<String, Object> f = fields(fieldsJson);
        return new DirectoryPreferenceResponse(visible, Boolean.TRUE.equals(f.get("phone")), Boolean.TRUE.equals(f.get("email")),
                Boolean.TRUE.equals(f.get("photo")), Boolean.TRUE.equals(f.get("groups")));
    }

    private Map<String, Object> fields(String s) {
        try {
            return s == null ? Map.of() : mapper.readValue(s, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String json(Map<String, Boolean> m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
