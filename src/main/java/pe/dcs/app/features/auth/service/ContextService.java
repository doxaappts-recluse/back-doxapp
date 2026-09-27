package pe.dcs.app.features.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.PlatformStaff;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.repo.PlatformStaffRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.auth.dto.ContextInfo;
import pe.dcs.app.features.auth.dto.UserInfo;
import pe.dcs.app.features.branch.domain.Branch;
import pe.dcs.app.features.branch.domain.BranchRepository;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.person.domain.Person;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.ContractState;
import pe.dcs.app.security.TokenType;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contextos de una credencial (spec 00 §5): un acceso (rol + sede) de una persona en su organización, o el propio
 * personal de plataforma. Resuelve, con datos frescos de BD, el actor que va en el JWT.
 */
@Service
@RequiredArgsConstructor
public class ContextService {

    /** Clave de la única opción de una organización que todavía no tiene sedes (solo ORG_ADMIN). */
    private static final UUID NO_BRANCH = new UUID(0L, 0L);

    private final UserAccessRepository accesses;
    private final BranchRepository branches;
    private final OrganizationRepository organizations;
    private final PersonRepository persons;
    private final PlatformStaffRepository staffRepository;
    private final ContractGate contractGate;
    private final Clock clock;

    /** Actor ya validado contra la BD + datos para la respuesta. */
    public record Resolved(AuthenticatedActor actor, ContextInfo context, ContractState contractState) {
    }

    /** Titular de una credencial (nombre y correo) para respuestas y notificaciones. */
    public record Owner(UUID id, String name, String email, boolean active) {
    }

    public Owner owner(Credential c) {
        if (c.getActorType() == ActorType.STAFF) {
            return staffRepository.findById(c.getStaffId())
                    .map(s -> new Owner(s.getId(), s.fullName(), s.getEmail(), s.getStatus() == AccessStatus.ACTIVE))
                    .orElse(null);
        }
        return persons.findByIdAndOrganizationId(c.getPersonId(), c.getOrganizationId())
                .map(p -> new Owner(p.getId(), p.fullName(), p.getEmail(), "ACTIVE".equals(p.getStatus())))
                .orElse(null);
    }

    public UserInfo userInfo(Credential c) {
        Owner o = owner(c);
        return new UserInfo(c.ownerId(), o == null ? c.getUsername() : o.name(), c.getUsername(),
                c.getActorType().name(), c.isMfaEnabled());
    }

    /**
     * Sedes con las que la persona puede trabajar hoy, cada una con el rol más alto que tiene en ella (vacío si la
     * organización no permite ingresar). ORG_ADMIN cubre todas las sedes activas de la organización; los demás roles,
     * la suya. {@code id} = acceso que otorga el rol; junto con {@code branchId} identifica el contexto.
     */
    public List<ContextInfo> effectiveContexts(Credential c) {
        if (c.getActorType() != ActorType.PERSON) {
            return List.of();
        }
        Organization org = organizations.findById(c.getOrganizationId()).orElse(null);
        if (org == null || !org.getStatus().allowsLogin()) {
            return List.of();
        }
        LocalDate today = today(org);
        // Sin contrato ACTIVE solo ORG_ADMIN puede entrar (modo limitado); el resto queda bloqueado (spec 00 §6.4)
        boolean contract = contractGate.hasActiveContract(org.getId());
        List<UserAccess> list = accesses.findByPersonIdAndOrganizationIdAndStatus(
                c.getPersonId(), c.getOrganizationId(), AccessStatus.ACTIVE).stream()
                .filter(a -> a.isEffective(today))
                .filter(a -> contract || a.getRole() == RoleType.ORG_ADMIN)
                .sorted(Comparator.comparing((UserAccess a) -> a.getRole().level())) // el rol más alto gana en cada sede
                .toList();
        Map<UUID, Branch> active = activeBranches(org.getId());
        Map<UUID, ContextInfo> byBranch = new LinkedHashMap<>();
        for (UserAccess a : list) {
            if (a.getBranchId() == null) {
                if (active.isEmpty()) {
                    byBranch.putIfAbsent(NO_BRANCH, info(a, org, null)); // organización aún sin sedes
                }
                active.values().forEach(b -> byBranch.putIfAbsent(b.getId(), info(a, org, b)));
            } else if (active.containsKey(a.getBranchId())) {
                byBranch.putIfAbsent(a.getBranchId(), info(a, org, active.get(a.getBranchId())));
            }
        }
        return byBranch.values().stream()
                .sorted(Comparator.comparing((ContextInfo i) -> i.branchName() == null ? "" : i.branchName().toLowerCase()))
                .toList();
    }

    public Resolved resolve(Credential c, UUID contextId) {
        return resolve(c, contextId, null);
    }

    /** Construye el actor de ACCESO para el contexto pedido, o 403 si ya no es válido/vigente. */
    public Resolved resolve(Credential c, UUID contextId, UUID branchId) {
        if (c.getActorType() == ActorType.STAFF) {
            PlatformStaff s = staffRepository.findById(c.getStaffId()).orElse(null);
            if (s == null || s.getStatus() != AccessStatus.ACTIVE || !s.getId().equals(contextId)) {
                throw new Exceptions("error.auth.contextInvalid", HttpStatus.FORBIDDEN);
            }
            AuthenticatedActor actor = new AuthenticatedActor(c.getId(), ActorType.STAFF, s.getId(), s.getId(), null,
                    Set.of(), null, s.getStaffRole().toRoleType(), ContractState.NONE, TokenType.ACCESS, null);
            return new Resolved(actor, staffContext(s), ContractState.NONE);
        }
        Organization org = organizations.findById(c.getOrganizationId()).orElse(null);
        UserAccess a = accesses.findByIdAndOrganizationId(contextId, c.getOrganizationId()).orElse(null);
        if (org == null || a == null || !org.getStatus().allowsLogin()
                || !a.getPersonId().equals(c.getPersonId()) || !a.isEffective(today(org))) {
            throw new Exceptions("error.auth.contextInvalid", HttpStatus.FORBIDDEN);
        }
        Map<UUID, Branch> active = activeBranches(org.getId());
        Branch branch;
        if (a.getBranchId() == null) { // ORG_ADMIN: la sede de trabajo la elige él (obligatoria si hay sedes)
            branch = branchId == null ? null : active.get(branchId);
            if (branchId == null ? !active.isEmpty() : branch == null) {
                throw new Exceptions("error.auth.contextInvalid", HttpStatus.FORBIDDEN);
            }
        } else { // rol de sede: la suya, y debe seguir activa
            branch = active.get(a.getBranchId());
            if (branch == null || (branchId != null && !branchId.equals(a.getBranchId()))) {
                throw new Exceptions("error.auth.contextInvalid", HttpStatus.FORBIDDEN);
            }
        }
        boolean contract = contractGate.hasActiveContract(org.getId());
        if (!contract && a.getRole() != RoleType.ORG_ADMIN) {
            throw new Exceptions("error.auth.orgNoContract", HttpStatus.FORBIDDEN);
        }
        ContractState cs = contract ? ContractState.ACTIVE : ContractState.NONE;
        Set<UUID> branchIds = a.getBranchId() == null ? Set.of() : Set.of(a.getBranchId());
        AuthenticatedActor actor = new AuthenticatedActor(c.getId(), ActorType.PERSON, c.getPersonId(), a.getId(),
                org.getId(), branchIds, branch == null ? null : branch.getId(), a.getRole(), cs, TokenType.ACCESS, null);
        return new Resolved(actor, info(a, org, branch), cs);
    }

    /** Actor de un token intermedio (PRE_AUTH / SETUP / CONTEXT): sin contexto, sin permisos sobre la API. */
    public AuthenticatedActor intermediate(Credential c, TokenType type) {
        if (c.getActorType() == ActorType.STAFF) {
            PlatformStaff s = staffRepository.findById(c.getStaffId()).orElseThrow(ContextService::noAccess);
            return new AuthenticatedActor(c.getId(), ActorType.STAFF, s.getId(), null, null, Set.of(), null,
                    s.getStaffRole().toRoleType(), ContractState.NONE, type, null);
        }
        return new AuthenticatedActor(c.getId(), ActorType.PERSON, c.getPersonId(), null, c.getOrganizationId(),
                Set.of(), null, RoleType.MEMBER, ContractState.NONE, type, null);
    }

    /** Token de contexto con varias iglesias: las credenciales "hermanas" viajan en el claim de sedes (sin uso en este tipo de token). */
    public AuthenticatedActor intermediateWithSiblings(Credential c, Set<UUID> siblingCredentialIds) {
        AuthenticatedActor a = intermediate(c, TokenType.CONTEXT);
        return new AuthenticatedActor(a.credentialId(), a.actorType(), a.ownerId(), a.contextId(), a.organizationId(),
                Set.copyOf(siblingCredentialIds), a.activeBranchId(), a.role(), a.contractState(), a.tokenType(), a.assistedGrantId());
    }

    /** Persona dueña de un acceso (para saber a qué credencial pertenece el contexto elegido). */
    public java.util.Optional<UUID> personOfAccess(UUID accessId) {
        return accesses.findById(accessId).map(UserAccess::getPersonId);
    }

    public boolean hasActiveContract(UUID organizationId) {
        return contractGate.hasActiveContract(organizationId);
    }

    /** Fecha calendario de la organización (D9). */
    public LocalDate today(Organization org) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(org.getTimezone()));
    }

    private Map<UUID, Branch> activeBranches(UUID orgId) {
        return branches.findByOrganizationId(orgId).stream()
                .filter(b -> b.getStatus() == StatusType.ACTIVE)
                .collect(Collectors.toMap(Branch::getId, Function.identity(), (x, y) -> x, LinkedHashMap::new));
    }

    private static ContextInfo info(UserAccess a, Organization org, Branch branch) {
        return new ContextInfo(a.getId(), org.getId(), org.getName(), org.getSlug(),
                branch == null ? null : branch.getId(), branch == null ? null : branch.getName(), a.getRole().name());
    }

    private static ContextInfo staffContext(PlatformStaff s) {
        return new ContextInfo(s.getId(), null, null, null, null, null, s.getStaffRole().name());
    }

    private static Exceptions noAccess() {
        return new Exceptions("error.auth.noAccess", HttpStatus.FORBIDDEN);
    }
}
