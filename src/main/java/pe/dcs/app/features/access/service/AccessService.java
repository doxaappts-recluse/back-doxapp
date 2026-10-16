package pe.dcs.app.features.access.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.PermissionProfile;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.domain.UserAccessPermission;
import pe.dcs.app.features.access.dto.AccessCreateRequest;
import pe.dcs.app.features.access.dto.AccessResponse;
import pe.dcs.app.features.access.dto.AccessSearchRequest;
import pe.dcs.app.features.access.dto.AccessStatusRequest;
import pe.dcs.app.features.access.dto.AccessUpdateRequest;
import pe.dcs.app.features.access.dto.DelegableModule;
import pe.dcs.app.features.access.dto.PermissionItemDto;
import pe.dcs.app.features.access.dto.PersonCandidate;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.UserAccessPermissionRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.branch.domain.Branch;
import pe.dcs.app.features.branch.domain.BranchRepository;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.features.person.domain.Person;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AccessStateCache;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.vo.ContactValidator;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M05 · Equipo y accesos de la organización (N2 y N3). ORG_ADMIN gestiona ORG_ADMIN adicionales, ORG_BRANCH_ADMIN y
 * ORG_USER; ORG_BRANCH_ADMIN solo ORG_USER de su sede. Reglas: [V6] contacto · [V9] duplicado · [V10] sede según rol ·
 * [V11] licencias · [V12] no elevación · [V13] módulos delegables · [V14] no tocar el propio acceso · [V15] vigencia.
 * Todo cambio surte efecto de inmediato: la caché de acceso se invalida y, al inactivar, se cierran las sesiones.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    static final String MODULE = "SUPPORT_TEAM";
    static final String ENTITY = "UserAccess";
    private static final Set<RoleType> TEAM_ROLES = EnumSet.of(RoleType.ORG_ADMIN, RoleType.ORG_BRANCH_ADMIN, RoleType.ORG_USER);

    private final UserAccessRepository accesses;
    private final UserAccessPermissionRepository permissionRepo;
    private final PersonRepository persons;
    private final CredentialRepository credentials;
    private final BranchRepository branches;
    private final OrganizationRepository organizations;
    private final AccessProvisioner provisioner;
    private final AccessLifecycle lifecycle;
    private final LicenseGuard licenseGuard;
    private final DelegationRules rules;
    private final PermissionProfileService profileService;
    private final AuthorizationService authorization;
    private final AccessStateCache accessState;
    private final AuditService audit;
    private final Clock clock;

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public PageResponse<AccessResponse> search(AccessSearchRequest req, AuthenticatedActor actor, AccessScope scope) {
        AccessSearchRequest.Filters f = req == null || req.filters() == null
                ? new AccessSearchRequest.Filters(null, null, null, null) : req.filters();
        RoleType roleFilter = f.role() == null || f.role().isBlank() ? null : parseEnum(RoleType.class, f.role());
        AccessStatus statusFilter = f.status() == null || f.status().isBlank() ? null : parseEnum(AccessStatus.class, f.status());

        Specification<UserAccess> spec = (root, query, cb) -> {
            Root<Person> p = query.from(Person.class);
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(p.get("id"), root.get("personId")));
            ps.add(cb.equal(root.get("organizationId"), scope.organizationId()));
            if (scope.role() == RoleType.ORG_ADMIN) {
                ps.add(root.get("role").in(TEAM_ROLES));
            } else {
                ps.add(cb.equal(root.get("role"), RoleType.ORG_USER));
                ps.add(root.get("branchId").in(scope.branchIds().isEmpty() ? Set.of(new UUID(0, 0)) : scope.branchIds()));
            }
            if (roleFilter != null) {
                ps.add(cb.equal(root.get("role"), roleFilter));
            }
            if (statusFilter != null) {
                ps.add(cb.equal(root.get("status"), statusFilter));
            }
            if (f.branchId() != null) {
                ps.add(cb.equal(root.get("branchId"), f.branchId()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(p.get("firstName")), like),
                        cb.like(cb.lower(p.get("lastName")), like),
                        cb.like(cb.lower(cb.concat(cb.concat(p.get("firstName"), " "), p.get("lastName"))), like),
                        cb.like(cb.lower(cb.coalesce(p.get("email"), "")), like),
                        cb.like(cb.lower(p.get("docNumber")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<SortRequest> sorts = req == null ? null : req.sorts();
        if (sorts == null || sorts.isEmpty()) {
            SortRequest byCreated = new SortRequest();
            byCreated.setKey("createdAt");
            byCreated.setDirection("DESC");
            sorts = List.of(byCreated);
        }
        Pageable pageable = PageableUtil.buildPageable(req == null ? null : req.pagination(), sorts,
                field -> switch (field) { case "role", "status", "validFrom", "validTo", "createdAt" -> field; default -> "createdAt"; });
        Page<UserAccess> page = accesses.findAll(spec, pageable);
        return new PageResponse<>(toResponses(page.getContent(), actor, scope), new PaginationResponse(
                (int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public AccessResponse get(UUID id, AuthenticatedActor actor, AccessScope scope) {
        return toResponses(List.of(visible(id, scope)), actor, scope).get(0);
    }

    /** Personas de la organización que se pueden elegir (búsqueda mínima; M06 traerá la completa). */
    @Transactional(readOnly = true)
    public List<PersonCandidate> candidates(String q, AccessScope scope) {
        if (q == null || q.trim().length() < 2) {
            return List.of();
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return persons.searchMinimal(scope.organizationId(), like, PageRequest.of(0, 10)).stream()
                .map(p -> new PersonCandidate(p.getId(), p.fullName(), p.getDocType(), p.getDocNumber(), p.getEmail(),
                        credentials.findByPersonId(p.getId()).isPresent(),
                        accesses.findByPersonIdAndOrganizationId(p.getId(), scope.organizationId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DelegableModule> delegableModules(AuthenticatedActor actor) {
        return rules.delegable(actor);
    }

    // ---------------------------------------------------------------- alta

    @Transactional
    public AccessResponse create(AccessCreateRequest req, AuthenticatedActor actor, AccessScope scope) {
        Organization org = lockOrganization(scope.organizationId());
        RoleType role = req.role();
        assertMayAssign(actor, scope, role);

        UUID branchId = req.branchId();
        if (role == RoleType.ORG_ADMIN) {                                                              // [V10]
            if (branchId != null) {
                throw new Exceptions("error.access.branchForbidden", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } else {
            if (branchId == null) {
                throw new Exceptions("error.access.branchRequired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            Branch b = branches.findByIdAndOrganizationId(branchId, org.getId())
                    .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
            if (!scope.canSeeBranch(b.getId())) {
                throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
            }
            if (b.getStatus() != StatusType.ACTIVE) {
                throw new Exceptions("error.access.branchInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        LocalDate today = today(org);
        LocalDate validFrom = req.validFrom() == null ? today : req.validFrom();
        LocalDate validTo = req.validTo();
        assertValidity(validFrom, validTo, today);                                                      // [V15]

        Map<String, List<String>> delegated = delegation(actor, scope, role, req.profileId(), req.permissions());

        AccessProvisioner.PersonInput in = new AccessProvisioner.PersonInput(req.personId(), req.docType(), req.docNumber(),
                req.firstName(), req.lastName(), req.email(), req.phone());
        Person person = provisioner.resolvePerson(org.getId(), in);
        if (person.getId().equals(scope.personId())) {                                                 // [V14]
            throw new Exceptions("error.access.selfChange", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        licenseGuard.assertAvailable(org.getId(), branchId);                                            // [V11] (organización bloqueada)
        AccessProvisioner.Provisioned pr = provisioner.provision(org,
                new AccessProvisioner.PersonInput(person.getId(), null, null, null, null, null, null), role, branchId, validFrom, validTo);

        savePermissions(pr.access().getId(), delegated);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("personId", person.getId().toString());
        diff.put("role", role.name());
        diff.put("status", pr.access().getStatus().name());
        if (branchId != null) {
            diff.put("branchId", branchId.toString());
        }
        if (!delegated.isEmpty()) {
            diff.put("permissions", summary(delegated));
        }
        diff.put("invited", pr.invited());
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, pr.access().getId(), org.getId(), branchId, diff));
        accessState.invalidate(pr.access().getId());
        return toResponses(List.of(pr.access()), actor, scope).get(0);
    }

    // ---------------------------------------------------------------- edición

    @Transactional
    public AccessResponse update(UUID id, AccessUpdateRequest req, AuthenticatedActor actor, AccessScope scope) {
        Organization org = lockOrganization(scope.organizationId());
        UserAccess a = manageable(id, actor, scope);
        if (req.version() != null && !req.version().equals(a.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        LocalDate today = today(org);
        LocalDate newFrom = req.validFrom() != null ? req.validFrom() : a.getValidFrom();
        LocalDate newTo = req.clearValidTo() ? null : req.validTo() != null ? req.validTo() : a.getValidTo();
        Map<String, Object> diff = new LinkedHashMap<>();
        if (!Objects.equals(newFrom, a.getValidFrom()) || !Objects.equals(newTo, a.getValidTo())) {
            if (a.getStatus() != AccessStatus.INACTIVE) {
                assertValidity(newFrom, newTo, today);                                                  // [V15]
            } else if (newTo != null && newTo.isBefore(newFrom)) {
                throw new Exceptions("error.common.dateRange", HttpStatus.BAD_REQUEST);
            }
            track(diff, "validFrom", a.getValidFrom(), newFrom);
            track(diff, "validTo", a.getValidTo(), newTo);
            a.setValidFrom(newFrom);
            a.setValidTo(newTo);
        }

        if (req.permissions() != null) {
            if (a.getRole() != RoleType.ORG_USER) {
                throw new Exceptions("error.access.permissionsOnlyUser", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            Map<String, List<String>> before = currentPermissions(a.getId());
            Map<String, List<String>> after = rules.normalize(actor, req.permissions());              // [V12][V13]
            if (!summary(before).equals(summary(after))) {
                diff.put("permissions", Map.of("from", summary(before), "to", summary(after)));
            }
            permissionRepo.deleteByAccessId(a.getId());
            savePermissions(a.getId(), after);
        }

        boolean resend = false;
        if (req.email() != null && !req.email().isBlank()) {
            resend = changeEmail(org, a, req.email(), diff);
        }
        a = accesses.saveAndFlush(a);
        accessState.invalidate(a.getId());
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, a.getId(), org.getId(), a.getBranchId(), diff));
        if (resend) {
            Person p = persons.findByIdAndOrganizationId(a.getPersonId(), org.getId()).orElseThrow();
            provisioner.sendInvite(org, p, credentials.findByPersonId(p.getId()).orElseThrow());
        }
        return toResponses(List.of(a), actor, scope).get(0);
    }

    /** El correo solo se corrige mientras la invitación no se acepta; cambia el usuario de ingreso. */
    boolean changeEmail(Organization org, UserAccess a, String rawEmail, Map<String, Object> diff) {
        Person p = persons.findByIdAndOrganizationId(a.getPersonId(), org.getId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        String email = ContactValidator.normalizeEmail(rawEmail);
        if (email.equalsIgnoreCase(p.getEmail())) {
            return false;
        }
        Credential c = credentials.findByPersonId(p.getId()).orElse(null);
        if (c == null || c.getPasswordHash() != null) {
            throw new Exceptions("error.access.emailLocked", HttpStatus.CONFLICT);
        }
        if (!ContactValidator.isValidEmail(email)) {
            throw new Exceptions("error.common.emailInvalid", HttpStatus.BAD_REQUEST);
        }
        credentials.findPersonByUsername(org.getId(), email).filter(o -> !o.getId().equals(c.getId())).ifPresent(o -> {
            throw new Exceptions("error.access.emailDuplicate", HttpStatus.CONFLICT);
        });
        track(diff, "email", p.getEmail(), email);
        p.setEmail(email);
        persons.saveAndFlush(p);
        c.setUsername(email.toLowerCase(Locale.ROOT));
        credentials.saveAndFlush(c);
        return true;
    }

    // ---------------------------------------------------------------- estado / invitación

    @Transactional
    public AccessResponse changeStatus(UUID id, AccessStatusRequest req, AuthenticatedActor actor, AccessScope scope) {
        Organization org = lockOrganization(scope.organizationId());
        UserAccess a = manageable(id, actor, scope);
        lifecycle.changeStatus(a, req.status(), req.reason(), org, MODULE);
        return toResponses(List.of(a), actor, scope).get(0);
    }

    @Transactional
    public void resendInvite(UUID id, AuthenticatedActor actor, AccessScope scope) {
        Organization org = lockOrganization(scope.organizationId());
        UserAccess a = manageable(id, actor, scope);
        Credential c = credentials.findByPersonId(a.getPersonId()).orElse(null);
        if (a.getStatus() != AccessStatus.INVITED || c == null || c.getPasswordHash() != null) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.getStatus());
        }
        Person p = persons.findByIdAndOrganizationId(a.getPersonId(), org.getId()).orElseThrow();
        provisioner.sendInvite(org, p, c);
        audit.record(new AuditService.Command(MODULE, "INVITE_RESENT", ENTITY, a.getId(), org.getId(), a.getBranchId(),
                Map.of("role", a.getRole().name())));
    }

    // ---------------------------------------------------------------- reglas

    /** Quién puede asignar qué rol: ORG_ADMIN los tres (ORG_BRANCH_ADMIN y ORG_ADMIN con su módulo); ORG_BRANCH_ADMIN solo ORG_USER. */
    private void assertMayAssign(AuthenticatedActor actor, AccessScope scope, RoleType role) {
        if (role == RoleType.MEMBER || role.isStaff()) {
            throw new Exceptions("error.access.roleInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (scope.role() != RoleType.ORG_ADMIN && role != RoleType.ORG_USER) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (role == RoleType.ORG_BRANCH_ADMIN && !authorization.effectiveActions(actor, "BRANCH_ADMINS").contains(Action.C.name())) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
    }

    /** Acceso visible para quien consulta (404 si no) y que además puede gestionar (403; el propio → 422). */
    private UserAccess manageable(UUID id, AuthenticatedActor actor, AccessScope scope) {
        UserAccess a = visible(id, scope);
        if (a.getPersonId().equals(scope.personId())) {                                               // [V14]
            throw new Exceptions("error.access.selfChange", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!canManage(a, scope, actor)) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        return a;
    }

    private boolean canManage(UserAccess a, AccessScope scope, AuthenticatedActor actor) {
        if (a.getPersonId().equals(scope.personId())) {
            return false;
        }
        if (scope.role() == RoleType.ORG_ADMIN) {
            return a.getRole() != RoleType.ORG_BRANCH_ADMIN
                    || authorization.effectiveActions(actor, "BRANCH_ADMINS").contains(Action.E.name());
        }
        return a.getRole() == RoleType.ORG_USER && scope.canSeeBranch(a.getBranchId());
    }

    private UserAccess visible(UUID id, AccessScope scope) {
        UserAccess a = accesses.findByIdAndOrganizationId(id, scope.organizationId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        boolean ok = scope.role() == RoleType.ORG_ADMIN
                ? TEAM_ROLES.contains(a.getRole())
                : a.getRole() == RoleType.ORG_USER && scope.canSeeBranch(a.getBranchId());
        if (!ok) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        return a;
    }

    private Map<String, List<String>> delegation(AuthenticatedActor actor, AccessScope scope, RoleType role, UUID profileId,
                                                 List<PermissionItemDto> explicit) {
        boolean any = profileId != null || (explicit != null && !explicit.isEmpty());
        if (role != RoleType.ORG_USER) {
            if (any) {
                throw new Exceptions("error.access.permissionsOnlyUser", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return Map.of();
        }
        List<PermissionItemDto> items = new ArrayList<>();
        if (profileId != null) {
            PermissionProfile p = profileService.activeProfile(profileId, scope.organizationId());
            p.getItems().forEach(i -> items.add(new PermissionItemDto(i.getModuleCode(), i.actionList())));
        }
        if (explicit != null) {
            items.addAll(explicit);
        }
        return rules.normalize(actor, items);                                                          // [V12][V13]
    }

    private void assertValidity(LocalDate from, LocalDate to, LocalDate today) {
        if (to != null && to.isBefore(from)) {
            throw new Exceptions("error.common.dateRange", HttpStatus.BAD_REQUEST);
        }
        if (to != null && to.isBefore(today)) {
            throw new Exceptions("error.access.expired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private Organization lockOrganization(UUID orgId) {
        Organization o = organizations.lockById(orgId).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (o.getStatus() == OrganizationStatus.CLOSED) {
            throw new Exceptions("error.access.orgClosed", HttpStatus.CONFLICT);
        }
        return o;
    }

    private LocalDate today(Organization org) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(org.getTimezone()));
    }

    // ---------------------------------------------------------------- permisos

    private void savePermissions(UUID accessId, Map<String, List<String>> delegated) {
        delegated.forEach((code, acts) -> {
            UserAccessPermission row = new UserAccessPermission();
            row.setAccessId(accessId);
            row.setModuleCode(code);
            row.setActions(acts.toArray(new String[0]));
            permissionRepo.save(row);
        });
        permissionRepo.flush();
    }

    private Map<String, List<String>> currentPermissions(UUID accessId) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        permissionRepo.findByAccessId(accessId).stream().sorted(Comparator.comparing(UserAccessPermission::getModuleCode))
                .forEach(r -> m.put(r.getModuleCode(), r.actionList()));
        return m;
    }

    private static String summary(Map<String, List<String>> m) {
        return m.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + String.join("", e.getValue())).collect(Collectors.joining(","));
    }

    // ---------------------------------------------------------------- respuesta

    private List<AccessResponse> toResponses(Collection<UserAccess> list, AuthenticatedActor actor, AccessScope scope) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<UUID> personIds = list.stream().map(UserAccess::getPersonId).collect(Collectors.toSet());
        Map<UUID, Person> people = persons.findAllById(personIds).stream().collect(Collectors.toMap(Person::getId, Function.identity()));
        Map<UUID, Credential> creds = new HashMap<>();
        for (UUID pid : personIds) {
            credentials.findByPersonId(pid).ifPresent(c -> creds.put(pid, c));
        }
        Set<UUID> branchIds = list.stream().map(UserAccess::getBranchId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> branchNames = branches.findAllById(branchIds).stream().collect(Collectors.toMap(Branch::getId, Branch::getName));
        Map<UUID, List<UserAccessPermission>> perms = permissionRepo.findByAccessIdIn(list.stream().map(UserAccess::getId).toList())
                .stream().collect(Collectors.groupingBy(UserAccessPermission::getAccessId));
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(
                organizations.findById(scope.organizationId()).map(Organization::getTimezone).orElse("America/Lima")));
        List<AccessResponse> out = new ArrayList<>();
        for (UserAccess a : list) {
            Person p = people.get(a.getPersonId());
            Credential c = creds.get(a.getPersonId());
            List<PermissionItemDto> items = perms.getOrDefault(a.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(UserAccessPermission::getModuleCode))
                    .map(r -> new PermissionItemDto(r.getModuleCode(), r.actionList())).toList();
            out.add(new AccessResponse(a.getId(), a.getPersonId(), p == null ? null : p.fullName(),
                    p == null ? null : p.getDocType(), p == null ? null : p.getDocNumber(), p == null ? null : p.getEmail(),
                    p == null ? null : p.getPhone(), a.getRole(), a.getBranchId(), branchNames.get(a.getBranchId()),
                    a.getStatus(), a.getStatusReason(), a.getValidFrom(), a.getValidTo(), a.isEffective(today),
                    c != null && c.getPasswordHash() != null, c == null ? null : c.getLastLoginAt(), items,
                    a.getPersonId().equals(scope.personId()), canManage(a, scope, actor), a.getCreatedAt(), a.getUpdatedAt(), a.getVersion()));
        }
        return out;
    }

    private static void track(Map<String, Object> diff, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("from", before == null ? null : before.toString());
            pair.put("to", after == null ? null : after.toString());
            diff.put(field, pair);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, value);
        }
    }
}
