package pe.dcs.app.features.portal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.person.dto.PersonDtos;
import pe.dcs.app.features.person.service.ConsentService;
import pe.dcs.app.features.portal.dto.PortalDtos.DataRequestResponse;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * M24 [V18] · privacidad del miembro: descargar mis datos y pedir eliminación, 1 por 24h cada una (tabla propia
 * {@code portal_data_request}: SupportService exige rol de organización — {@code requireOrgRole} — que un MEMBER
 * no tiene, así que la "eliminación" abre el caso de soporte con un INSERT directo, no reutiliza SupportService).
 * [D3] sin URL firmada pública: "mis datos" se entrega en el cuerpo de esta respuesta autenticada, no por enlace.
 */
@Service
@RequiredArgsConstructor
public class PrivacyService {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final NamedParameterJdbcTemplate jdbc;
    private final ConsentService consents;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public Map<String, Object> exportData(AuthenticatedActor actor) {
        assertNotRateLimited(actor.ownerId(), "EXPORT");
        record(actor.ownerId(), "EXPORT", null);
        Map<String, Object> profile = jdbc.queryForMap("select first_name, last_name, doc_type, doc_number, email, phone, birth_date, status"
                + " from person where id = :p", new MapSqlParameterSource("p", actor.ownerId()));
        PersonDtos.Consents c = consents.consents(new AccessScope(actor.organizationId(), true, java.util.Set.of(), actor.ownerId(), null, actor.role()),
                actor.ownerId());
        var requests = jdbc.queryForList("select type, status, created_at from approval_request where requested_by = :p order by created_at desc",
                new MapSqlParameterSource("p", actor.ownerId()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", profile);
        out.put("consents", c);
        out.put("requests", requests);
        out.put("exportedAt", Instant.now(clock).toString());
        audit.record(new AuditService.Command("PORTAL", "DATA_EXPORT", "Person", actor.ownerId(), actor.organizationId(), null, Map.of()));
        return out;
    }

    @Transactional
    public DataRequestResponse requestErasure(AuthenticatedActor actor) {
        assertNotRateLimited(actor.ownerId(), "ERASURE");
        Timestamp now = Timestamp.from(clock.instant());
        UUID caseId = UUID.randomUUID();
        String name = jdbc.queryForObject("select first_name || ' ' || last_name from person where id = :p",
                new MapSqlParameterSource("p", actor.ownerId()), String.class);
        jdbc.update("""
                insert into support_case (id, case_number, organization_id, branch_id, opened_by, opened_by_name, category, priority, subject,
                    status, sla_due_at, last_activity_at, created_at)
                values (:id, nextval('support_case_seq'), :o, :b, :p, :name, 'DATA_REQUEST', 'NORMAL', :subj, 'OPEN', :sla, :now, :now)
                """, new MapSqlParameterSource("id", caseId).addValue("o", actor.organizationId()).addValue("b", actor.activeBranchId())
                .addValue("p", actor.ownerId()).addValue("name", name).addValue("subj", "Portal: solicitud de eliminación de datos personales")
                .addValue("sla", Timestamp.from(clock.instant().plus(Duration.ofDays(7)))).addValue("now", now));
        record(actor.ownerId(), "ERASURE", caseId);
        audit.record(new AuditService.Command("PORTAL", "ERASURE_REQUEST", "Person", actor.ownerId(), actor.organizationId(), actor.activeBranchId(),
                Map.of("case", caseId.toString())));
        return new DataRequestResponse("ERASURE", clock.instant(), caseId);
    }

    private void assertNotRateLimited(UUID personId, String type) {
        Instant cut = clock.instant().minus(WINDOW);
        Long n = jdbc.queryForObject("select count(*) from portal_data_request where person_id = :p and type = :t and created_at > :cut",
                new MapSqlParameterSource("p", personId).addValue("t", type).addValue("cut", Timestamp.from(cut)), Long.class);
        if (n != null && n > 0) {
            throw new Exceptions("error.portal.exportLimit", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private void record(UUID personId, String type, UUID caseId) {
        jdbc.update("insert into portal_data_request (id, person_id, type, created_at, support_case_id) values (:id, :p, :t, :now, :c)",
                new MapSqlParameterSource("id", UUID.randomUUID()).addValue("p", personId).addValue("t", type)
                        .addValue("now", Timestamp.from(clock.instant())).addValue("c", caseId));
    }
}
