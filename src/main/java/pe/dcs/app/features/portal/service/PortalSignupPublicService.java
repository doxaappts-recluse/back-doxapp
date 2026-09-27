package pe.dcs.app.features.portal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.features.person.service.ConsentService;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.features.person.service.PersonService;
import pe.dcs.app.features.portal.dto.PortalDtos.PublicBranchOption;
import pe.dcs.app.features.portal.dto.PortalDtos.SignupPublicConfig;
import pe.dcs.app.features.portal.dto.PortalDtos.SignupPublicRequest;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.shared.vo.DocumentValidator;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M24 [D2] · autorregistro público (/public/orgs/{slug}/portal/signup). Sin verificación de correo/celular por
 * código (no hay SMTP ni SMS en el proyecto — ver cabecera de V31): el freno al abuso es el límite por IP [V5] más
 * la unicidad de documento revalidada al aprobar. Responde siempre el mismo agradecimiento (señuelo, ya en trámite,
 * documento ya vinculado a un acceso [V6]): nunca revela si una persona existe.
 */
@Service
@RequiredArgsConstructor
public class PortalSignupPublicService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContractGate contracts;
    private final PortalSettingsService settings;
    private final PersonLookupService lookup;
    private final PersonService persons;
    private final ConsentService consents;
    private final ApprovalEngine engine;
    private final PortalPublicRateLimiter limiter;
    private final AuditService audit;
    private final Clock clock;

    private static final int MIN_AGE = 14;

    private record Org(UUID id, String name) {
    }

    @Transactional(readOnly = true)
    public SignupPublicConfig config(String slug) {
        Org org = org(slug);
        var s = settings.effectiveForMember(org.id(), null);
        if (!"OPEN_WITH_APPROVAL".equals(s.signupMode())) {                                                           // [V8]
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        List<PublicBranchOption> branches = jdbc.query("select code, name from branch where organization_id = :o and status = 'ACTIVE' order by lower(name)",
                new MapSqlParameterSource("o", org.id()), (rs, i) -> new PublicBranchOption(rs.getString(1), rs.getString(2)));
        return new SignupPublicConfig(org.name(), true, branches, s.welcomeTextEs(), s.legalTextVersion());
    }

    @Transactional
    public void submit(String slug, String ip, SignupPublicRequest r) {
        if (!limiter.tryAcquire((ip == null ? "unknown" : ip))) {                                                     // [V5]
            throw new Exceptions("error.portal.rateLimited", HttpStatus.TOO_MANY_REQUESTS);
        }
        Org org = org(slug);
        var settingsRow = settings.effectiveForMember(org.id(), null);
        if (!"OPEN_WITH_APPROVAL".equals(settingsRow.signupMode())) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        if (r == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "datos");
        }
        if (r.website() != null && !r.website().isBlank()) {
            return;                                                                                                   // señuelo: se descarta en silencio
        }
        if (!Boolean.TRUE.equals(r.consent())) {
            throw new Exceptions("error.portal.consentRequired", HttpStatus.BAD_REQUEST);
        }
        if (r.birthDate() == null || Period.between(r.birthDate(), LocalDate.now(clock)).getYears() < MIN_AGE) {       // [V5]
            throw new Exceptions("error.portal.underAge", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID branchId = branch(org.id(), r.branchCode());
        DocumentType type;
        try {
            type = DocumentType.valueOf((r.docType() == null ? "" : r.docType()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "docType");
        }
        String doc = DocumentValidator.normalize(r.docNumber());
        if (type == DocumentType.RUC || !DocumentValidator.isValid(type, doc)) {
            throw new Exceptions("error.common.docInvalid", HttpStatus.BAD_REQUEST, doc, type);
        }
        String email = r.email() == null || r.email().isBlank() ? null : ContactValidator.normalizeEmail(r.email());
        if (email != null && !ContactValidator.isValidEmail(email)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        if (email == null && (r.phone() == null || r.phone().isBlank())) {
            throw new Exceptions("error.portal.contactRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        PersonLookupService.PersonMin existing = lookup.find(org.id(), type, doc).orElse(null);
        UUID personId;
        if (existing != null) {
            if (hasAnyAccess(existing.id())) {                                                                        // [V6] no revela existencia
                return;
            }
            if (hasOpenSignup(org.id(), existing.id())) {                                                              // idempotente: no duplica
                return;
            }
            personId = existing.id();
        } else {
            personId = persons.registerBasic(org.id(), branchId, r.firstName(), r.lastName(), r.phone(), email, type, doc, null, "PORTAL_SIGNUP");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("branchId", branchId.toString());
        payload.put("docType", type.name());
        payload.put("docNumber", doc);
        UUID reqId = engine.open(new ApprovalEngine.NewRequest(org.id(), branchId, null, "PORTAL_SIGNUP", "PERSON", personId, personId, null, payload));
        consents.grantDefaults(org.id(), personId, "PORTAL", null);
        audit.record(new AuditService.Command("PORTAL_SIGNUP", "CREATE", "ApprovalRequest", reqId, org.id(), branchId, Map.of("via", "PUBLIC_FORM")));
    }

    private boolean hasAnyAccess(UUID personId) {
        Long n = jdbc.queryForObject("select count(*) from user_access where person_id = :p", new MapSqlParameterSource("p", personId), Long.class);
        return n != null && n > 0;
    }

    private boolean hasOpenSignup(UUID orgId, UUID personId) {
        Long n = jdbc.queryForObject("select count(*) from approval_request where organization_id = :o and type = 'PORTAL_SIGNUP' and subject_id = :p and status = 'PENDING'",
                new MapSqlParameterSource("o", orgId).addValue("p", personId), Long.class);
        return n != null && n > 0;
    }

    private Org org(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase();
        List<Org> l = jdbc.query("select id, name from organization where slug = :s and status = 'ACTIVE'", new MapSqlParameterSource("s", slug),
                (rs, i) -> new Org((UUID) rs.getObject(1), rs.getString(2)));
        if (l.isEmpty() || !contracts.enabled(l.get(0).id(), "PORTAL_SIGNUP")) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return l.get(0);
    }

    private UUID branch(UUID orgId, String code) {
        if (code == null || code.isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        List<UUID> l = jdbc.query("select id from branch where organization_id = :o and status = 'ACTIVE' and upper(code) = :c",
                new MapSqlParameterSource("o", orgId).addValue("c", code.trim().toUpperCase()), (rs, i) -> (UUID) rs.getObject(1));
        if (l.isEmpty()) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "sede");
        }
        return l.get(0);
    }
}
