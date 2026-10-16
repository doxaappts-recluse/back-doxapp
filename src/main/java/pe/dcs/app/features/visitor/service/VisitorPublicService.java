package pe.dcs.app.features.visitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.features.person.service.PersonService;
import pe.dcs.app.features.visitor.dto.VisitorDtos;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M07 · Formulario público de bienvenida (/public/orgs/{slug}/visit). Sin sesión: exige que la organización esté activa y tenga
 * el módulo contratado (si no, 404), consentimiento marcado [V12], señuelo (honeypot) vacío y cupo de envíos por IP [V11].
 * Quien ya tiene un caso abierto en la sede no recibe error ni se duplica el caso (no se revela quién está registrado).
 */
@Service
@RequiredArgsConstructor
public class VisitorPublicService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContractGate contracts;
    private final VisitorService visitors;
    private final PersonLookupService lookup;
    private final PersonService persons;
    private final pe.dcs.app.features.person.service.ConsentService consents;
    private final NotificationService notifications;
    private final PublicRateLimiter limiter;
    private final AuditService audit;
    private final Clock clock;

    private record Org(UUID id, String name) {
    }

    @Transactional(readOnly = true)
    public VisitorDtos.PublicConfig config(String slug) {
        Org org = org(slug);
        List<VisitorDtos.PublicBranch> branches = jdbc.query("select code, name from branch where organization_id = :o and status = 'ACTIVE' order by lower(name)",
                new MapSqlParameterSource("o", org.id()), (rs, i) -> new VisitorDtos.PublicBranch(rs.getString(1), rs.getString(2)));
        List<VisitorDtos.PublicOption> sources = jdbc.query("select code, name_es, name_en from catalog_item where type = 'VISITOR_SOURCE' and active"
                + " and (organization_id is null or organization_id = :o) order by sort_order, name_es", new MapSqlParameterSource("o", org.id()),
                (rs, i) -> new VisitorDtos.PublicOption(rs.getString(1), rs.getString(2), rs.getString(3)));
        return new VisitorDtos.PublicConfig(org.name(), branches, sources);
    }

    /** Devuelve siempre el mismo agradecimiento, haya creado un caso o no (señuelo, caso ya abierto). */
    @Transactional
    public void submit(String slug, String ip, VisitorDtos.PublicRequest r) {
        if (!limiter.tryAcquire(ip == null ? "unknown" : ip)) {                                                       // [V11]
            throw new Exceptions("error.visitor.rateLimited", HttpStatus.TOO_MANY_REQUESTS);
        }
        Org org = org(slug);
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if (r.website() != null && !r.website().isBlank()) {
            return;                                                                                                   // honeypot: se descarta en silencio
        }
        if (!Boolean.TRUE.equals(r.consent())) {                                                                      // [V12]
            throw new Exceptions("error.visitor.consentRequired", HttpStatus.BAD_REQUEST);
        }
        UUID[] branch = branch(org.id(), r.branchCode());
        UUID branchId = branch[0];
        String branchName = jdbc.queryForObject("select name from branch where id = :id", new MapSqlParameterSource("id", branchId), String.class);
        String how = visitors.catalogCode(org.id(), "VISITOR_SOURCE", r.howArrived(), "cómo llegó");
        String phone = PersonService.normalizePhone(r.phone());
        if (phone == null && (r.email() == null || r.email().isBlank())) {                                            // [V2]
            throw new Exceptions("error.visitor.contactRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        AccessScope scope = new AccessScope(org.id(), false, Set.of(branchId), null, null, null);
        UUID personId = lookup.findMatches(scope, null, null, phone, r.email()).people().stream()
                .filter(p -> "ACTIVE".equals(p.status())).map(PersonLookupService.PersonMin::id).findFirst().orElse(null);
        if (personId == null) {
            personId = persons.registerBasic(org.id(), branchId, r.firstName(), r.lastName(), r.phone(), r.email(), null, null, null, "PUBLIC_FORM");
        }
        UUID caseId;
        try {
            caseId = visitors.insertCase(org.id(), branchId, personId, LocalDate.now(clock), how, null, null, VisitorService.clean(r.notes(), 500),
                    "PUBLIC_FORM", true, null);
        } catch (Exceptions e) {
            if ("error.visitor.openCaseExists".equals(e.getCode())) {
                return;                                                                                               // ya está en seguimiento: no se avisa ni se duplica
            }
            throw e;
        }
        consents.grantDefaults(org.id(), personId, "PUBLIC_FORM", null);                                              // M06: consentimiento vigente
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("branch", branchName);
        d.put("source", "PUBLIC_FORM");
        audit.record(new AuditService.Command(VisitorService.MODULE, "CREATE", "VisitorCase", caseId, org.id(), branchId, d));
        notifications.toPersons(NotificationType.VISITOR_NEW, org.id(), notifications.branchAdmins(org.id(), branchId),
                Map.of("name", visitors.personName(personId), "branch", branchName), "/app/visitors/" + caseId, null);
    }

    private Org org(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase();
        List<Org> l = jdbc.query("select id, name from organization where slug = :s and status = 'ACTIVE'", new MapSqlParameterSource("s", slug),
                (rs, i) -> new Org((UUID) rs.getObject(1), rs.getString(2)));
        if (l.isEmpty() || !contracts.enabled(l.get(0).id(), "VISITOR")) {                                            // sin módulo contratado: 404
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return l.get(0);
    }

    private UUID[] branch(UUID orgId, String code) {
        List<UUID> l;
        if (code == null || code.isBlank()) {
            l = jdbc.query("select id from branch where organization_id = :o and status = 'ACTIVE'", new MapSqlParameterSource("o", orgId), (rs, i) -> (UUID) rs.getObject(1));
            if (l.size() != 1) {
                throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
            }
        } else {
            l = jdbc.query("select id from branch where organization_id = :o and status = 'ACTIVE' and upper(code) = :c",
                    new MapSqlParameterSource("o", orgId).addValue("c", code.trim().toUpperCase()), (rs, i) -> (UUID) rs.getObject(1));
            if (l.isEmpty()) {
                throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "sede");
            }
        }
        return new UUID[]{l.get(0)};
    }
}
