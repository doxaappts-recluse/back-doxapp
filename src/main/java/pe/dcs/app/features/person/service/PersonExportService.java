package pe.dcs.app.features.person.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M06 · Privacidad: descarga de todos los datos de una persona (JSON) para atender su solicitud de acceso a la información.
 * Requiere la acción X y que la persona esté en el alcance. Las notas privadas y alergias solo viajan con la acción H [T-C12].
 * No incluye datos de terceros (los demás integrantes del hogar) ni nombres de quienes operaron el sistema.
 */
@Service
@RequiredArgsConstructor
public class PersonExportService {

    private final PersonService persons;
    private final ConsentService consents;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public record Export(byte[] content, String filename) {
    }

    @Transactional
    public Export exportPerson(AuthenticatedActor actor, AccessScope scope, UUID id) {
        PersonDtos.Response p = persons.get(actor, scope, id);
        PersonDtos.History history = persons.history(scope, id);
        PersonDtos.Consents c = consents.view(id);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportedAt", clock.instant().toString());
        root.put("person", p);
        root.put("branchHistory", history.branches());
        root.put("changeHistory", history.timeline().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", t.at());
            m.put("action", t.action());
            m.put("fields", t.fields());
            return m;
        }).toList());
        root.put("consents", c);
        root.put("visitorCases", visitorCases(id));

        byte[] json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new Exceptions("error.common.server", HttpStatus.INTERNAL_SERVER_ERROR, "export");
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("format", "JSON");
        d.put("sensitiveIncluded", !p.sensitiveMasked());
        audit.record(new AuditService.Command("PERSON", "EXPORT_PERSON", "Person", id, scope.organizationId(), null, d));
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        return new Export(json, "datos-persona-" + id.toString().substring(0, 8) + "-" + stamp + ".json");
    }

    private List<Map<String, Object>> visitorCases(UUID personId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("""
                select c.id, c.stage, c.first_visit_date, c.how_arrived, c.source, c.consent_status, c.notes, c.archive_reason,
                       c.created_at, b.name as branch_name
                  from visitor_case c join branch b on b.id = c.branch_id where c.person_id = :p order by c.created_at""",
                new MapSqlParameterSource("p", personId), rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    UUID caseId = (UUID) rs.getObject("id");
                    m.put("branch", rs.getString("branch_name"));
                    m.put("stage", rs.getString("stage"));
                    m.put("firstVisitDate", String.valueOf(rs.getDate("first_visit_date")));
                    m.put("howArrived", rs.getString("how_arrived"));
                    m.put("source", rs.getString("source"));
                    m.put("consent", rs.getString("consent_status"));
                    m.put("archiveReason", rs.getString("archive_reason"));
                    m.put("notes", rs.getString("notes"));
                    m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    m.put("contacts", jdbc.query("""
                            select at, method, result, notes, next_action_date from follow_up_contact
                             where subject_type = 'VISITOR_CASE' and subject_id = :c order by at""", new MapSqlParameterSource("c", caseId), (r2, i) -> {
                        Map<String, Object> k = new LinkedHashMap<>();
                        k.put("at", r2.getTimestamp("at").toInstant().toString());
                        k.put("method", r2.getString("method"));
                        k.put("result", r2.getString("result"));
                        k.put("notes", r2.getString("notes"));
                        k.put("nextActionDate", r2.getDate("next_action_date") == null ? null : r2.getDate("next_action_date").toString());
                        return k;
                    }));
                    out.add(m);
                });
        return out;
    }
}
