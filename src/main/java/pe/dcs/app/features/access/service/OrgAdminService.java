package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.dto.AccessStatusRequest;
import pe.dcs.app.features.access.dto.OrgAdminRequest;
import pe.dcs.app.features.access.dto.OrgAdminResponse;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.features.person.domain.Person;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M05 · Administradores de organización desde la plataforma (N1, módulo ORG_ADMINS). Alta = persona mínima + acceso
 * ORG_ADMIN (sin sede) + invitación. Después del alta la plataforma NO lee datos de personas (regla D3): solo esta
 * lista (nombre, correo de invitación, estado y último ingreso; SYSTEM_SUPPORT ve nombre, estado y último ingreso).
 * [V6] correo para invitar · [V7]/[V11] licencia · [V8] la organización conserva al menos un ORG_ADMIN.
 */
@Service
@RequiredArgsConstructor
public class OrgAdminService {

    static final String MODULE = "ORG_ADMINS";
    static final String ENTITY = "UserAccess";

    private final UserAccessRepository accesses;
    private final PersonRepository persons;
    private final CredentialRepository credentials;
    private final OrganizationRepository organizations;
    private final AccessProvisioner provisioner;
    private final AccessLifecycle lifecycle;
    private final LicenseGuard licenseGuard;
    private final AccessService accessService;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<OrgAdminResponse> list(UUID orgId, AuthenticatedActor actor) {
        organization(orgId);
        return toResponses(accesses.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId), cb.equal(root.get("role"), RoleType.ORG_ADMIN))), actor);
    }

    @Transactional
    public OrgAdminResponse create(UUID orgId, OrgAdminRequest req, AuthenticatedActor actor) {
        Organization org = lockOpen(orgId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(org.getTimezone()));
        AccessProvisioner.PersonInput in = new AccessProvisioner.PersonInput(null, req.docType(), req.docNumber(),
                req.firstName(), req.lastName(), req.email(), req.phone());
        Person person = provisioner.resolvePerson(org.getId(), in);
        licenseGuard.assertAvailable(org.getId(), null);                                              // [V7]
        AccessProvisioner.Provisioned pr = provisioner.provision(org,
                new AccessProvisioner.PersonInput(person.getId(), null, null, null, null, null, null), RoleType.ORG_ADMIN, null, today, null);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("personId", person.getId().toString());
        diff.put("role", RoleType.ORG_ADMIN.name());
        diff.put("status", pr.access().getStatus().name());
        diff.put("invited", pr.invited());
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, pr.access().getId(), org.getId(), null, diff));
        return toResponses(List.of(pr.access()), actor).get(0);
    }

    /** Corrige el correo de una invitación aún no aceptada y la reenvía. */
    @Transactional
    public OrgAdminResponse updateEmail(UUID orgId, UUID accessId, String email, AuthenticatedActor actor) {
        Organization org = lockOpen(orgId);
        UserAccess a = admin(orgId, accessId);
        Map<String, Object> diff = new LinkedHashMap<>();
        if (accessService.changeEmail(org, a, email, diff)) {
            Person p = persons.findByIdAndOrganizationId(a.getPersonId(), orgId).orElseThrow();
            provisioner.sendInvite(org, p, credentials.findByPersonId(p.getId()).orElseThrow());
            audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, a.getId(), org.getId(), null, diff));
        }
        return toResponses(List.of(a), actor).get(0);
    }

    @Transactional
    public OrgAdminResponse changeStatus(UUID orgId, UUID accessId, AccessStatusRequest req, AuthenticatedActor actor) {
        Organization org = lockOpen(orgId);
        UserAccess a = admin(orgId, accessId);
        lifecycle.changeStatus(a, req.status(), req.reason(), org, MODULE);
        return toResponses(List.of(a), actor).get(0);
    }

    @Transactional
    public void resendInvite(UUID orgId, UUID accessId) {
        Organization org = lockOpen(orgId);
        UserAccess a = admin(orgId, accessId);
        Credential c = credentials.findByPersonId(a.getPersonId()).orElse(null);
        if (a.getStatus() != AccessStatus.INVITED || c == null || c.getPasswordHash() != null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.getStatus());
        }
        Person p = persons.findByIdAndOrganizationId(a.getPersonId(), orgId).orElseThrow();
        provisioner.sendInvite(org, p, c);
        audit.record(new AuditService.Command(MODULE, "INVITE_RESENT", ENTITY, a.getId(), org.getId(), null, Map.of("role", "ORG_ADMIN")));
    }

    // ---------------------------------------------------------------- utilidades

    private UserAccess admin(UUID orgId, UUID accessId) {
        return accesses.findByIdAndOrganizationId(accessId, orgId).filter(a -> a.getRole() == RoleType.ORG_ADMIN)
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Organization organization(UUID orgId) {
        return organizations.findById(orgId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Organization lockOpen(UUID orgId) {
        Organization o = organizations.lockById(orgId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (o.getStatus() == OrganizationStatus.CLOSED) {
            throw new Exceptions("error.access.orgClosed", HttpStatus.CONFLICT);
        }
        return o;
    }

    private List<OrgAdminResponse> toResponses(List<UserAccess> list, AuthenticatedActor actor) {
        boolean full = authorization.effectiveActions(actor, MODULE).contains("E");                    // SYSTEM_ADMIN
        Map<UUID, Person> people = persons.findAllById(list.stream().map(UserAccess::getPersonId).toList()).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        return list.stream().sorted(Comparator.comparing(UserAccess::getCreatedAt)).map(a -> {
            Person p = people.get(a.getPersonId());
            Credential c = credentials.findByPersonId(a.getPersonId()).orElse(null);
            return new OrgAdminResponse(a.getId(), p == null ? null : p.fullName(), full && p != null ? p.getEmail() : null,
                    a.getStatus(), full ? a.getStatusReason() : null, c != null && c.getPasswordHash() != null,
                    c == null ? null : c.getLastLoginAt(), a.getCreatedAt());
        }).toList();
    }
}
