package features.portal.service;

import features.organization.domain.Organization;
import features.organization.domain.OrganizationRepository;
import features.portal.dto.PortalDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import features.access.domain.AccessStatus;
import features.access.domain.UserAccess;
import features.access.repo.UserAccessRepository;
import features.access.service.AccessLifecycle;
import features.access.service.AccessProvisioner;
import features.auth.service.InvitationService;
import features.person.service.ConsentService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * M24 N2/N3 · alta del acceso MEMBER (invitación), reenvío, activar/inactivar y listado por sede. La invitación en sí
 * (correo, token de 72h, activación) es la de M05 sin cambios — {@link AccessProvisioner#provisionMember} es el único
 * punto nuevo, y solo porque {@code provision(...)} es de paquete (ver su Javadoc).
 */
@Service
@RequiredArgsConstructor
public class PortalAccessAdminService {

    private static final String MODULE = PortalSettingsService.MODULE;

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessProvisioner provisioner;
    private final AccessLifecycle lifecycle;
    private final UserAccessRepository accesses;
    private final OrganizationRepository organizations;
    private final InvitationService invitations;
    private final ConsentService consents;
    private final AuditService audit;

    @Transactional
    public PortalDtos.AccessRow invite(AuthenticatedActor actor, AccessScope scope, PortalDtos.InviteRequest r) {
        if (r.branchId() == null || !scope.canSeeBranch(r.branchId())) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        // AccessScope.canSeeBranch() devuelve true incondicionalmente para allBranches=true (ORG_ADMIN), sin comprobar
        // que la sede sea realmente de su organización [HALLAZGO M24-2]. Se valida aquí explícitamente para no dejar
        // pasar un branchId de otra organización hasta chocar con la FK y devolver un 409 engañoso.
        Boolean sameOrg = jdbc.queryForObject("select exists(select 1 from branch where id = :b and organization_id = :o)",
                new MapSqlParameterSource("b", r.branchId()).addValue("o", scope.organizationId()), Boolean.class);
        if (!Boolean.TRUE.equals(sameOrg)) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "sede");
        }
        Organization org = organizations.findById(scope.organizationId()).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        AccessProvisioner.PersonInput input = new AccessProvisioner.PersonInput(r.personId(), r.docType(), r.docNumber(), r.firstName(), r.lastName(),
                r.email(), r.phone());
        AccessProvisioner.Provisioned p;
        try {
            p = provisioner.provisionMember(org, input, r.branchId());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new Exceptions("error.access.duplicate", HttpStatus.CONFLICT);
        }
        consents.grantDefaults(scope.organizationId(), p.person().getId(), "PORTAL", actor.ownerId());
        audit.record(new AuditService.Command(MODULE, "INVITE", "UserAccess", p.access().getId(), scope.organizationId(), r.branchId(),
                Map.of("person", p.person().getId().toString())));
        return row(scope.organizationId(), p.person().getId());
    }

    @Transactional
    public void resend(AuthenticatedActor actor, AccessScope scope, UUID personId) {
        UserAccess a = memberAccess(scope.organizationId(), personId);
        if (a.getStatus() != AccessStatus.INVITED) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, a.getStatus());
        }
        List<Map<String, Object>> c = jdbc.queryForList("select id, username from credential where person_id = :p",
                new MapSqlParameterSource("p", personId));
        if (c.isEmpty()) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        Organization org = organizations.findById(scope.organizationId()).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        String fullName = jdbc.queryForObject("select first_name || ' ' || last_name from person where id = :p", new MapSqlParameterSource("p", personId), String.class);
        invitations.sendInvite((UUID) c.get(0).get("id"), (String) c.get(0).get("username"), fullName, org.getSlug(), Locale.getDefault().getLanguage());
        audit.record(new AuditService.Command(MODULE, "INVITE_RESEND", "UserAccess", a.getId(), scope.organizationId(), a.getBranchId(), Map.of()));
    }

    @Transactional
    public PortalDtos.AccessRow setEnabled(AuthenticatedActor actor, AccessScope scope, UUID personId, boolean enable, String reason) {
        UserAccess a = memberAccess(scope.organizationId(), personId);
        Organization org = organizations.findById(scope.organizationId()).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        lifecycle.setMemberAccessStatus(a, enable, reason, org);
        return row(scope.organizationId(), personId);
    }

    @Transactional(readOnly = true)
    public List<PortalDtos.AccessRow> list(AccessScope scope, UUID branchIdFilter) {
        MapSqlParameterSource ps = new MapSqlParameterSource("o", scope.organizationId());
        StringBuilder w = new StringBuilder("a.organization_id = :o and a.role = 'MEMBER'");
        if (branchIdFilter != null) {
            w.append(" and a.branch_id = :b");
            ps.addValue("b", branchIdFilter);
        } else if (!scope.allBranches()) {
            w.append(" and a.branch_id in (:branches)");
            ps.addValue("branches", scope.branchIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(scope.branchIds()));
        }
        return jdbc.query("""
                select p.id as person_id, p.first_name, p.last_name, p.doc_number, a.branch_id, b.name as branch_name, a.status, a.id as access_id
                from user_access a join person p on p.id = a.person_id left join branch b on b.id = a.branch_id
                where """ + " " + w, ps, (rs, i) -> new PortalDtos.AccessRow((UUID) rs.getObject("person_id"), rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("doc_number"), (UUID) rs.getObject("branch_id"), rs.getString("branch_name"), rs.getString("status"),
                (UUID) rs.getObject("access_id")));
    }

    private UserAccess memberAccess(UUID orgId, UUID personId) {
        return accesses.findByPersonIdAndOrganizationId(personId, orgId).stream().filter(a -> a.getRole() == RoleType.MEMBER).findFirst()
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private PortalDtos.AccessRow row(UUID orgId, UUID personId) {
        return list(new AccessScope(orgId, true, java.util.Set.of(), null, null, null), null).stream()
                .filter(x -> x.personId().equals(personId)).findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }
}
