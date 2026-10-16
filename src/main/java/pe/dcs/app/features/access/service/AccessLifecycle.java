package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.auth.service.RefreshTokenService;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.person.domain.PersonRepository;
import pe.dcs.app.security.authz.AccessStateCache;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cambio de estado de un acceso (inactivar con motivo / reactivar). Inactivar cierra las sesiones de ESE acceso y su
 * efecto es inmediato (caché invalidada); reactivar vuelve a pedir licencia y respeta la vigencia.
 * [V8] la organización conserva al menos un ORG_ADMIN.
 */
@Component
@RequiredArgsConstructor
public class AccessLifecycle {

    private final UserAccessRepository accesses;
    private final CredentialRepository credentials;
    private final PersonRepository persons;
    private final LicenseGuard licenses;
    private final RefreshTokenService refreshTokens;
    private final AccessStateCache accessState;
    private final AccessProvisioner provisioner;
    private final AuditService audit;
    private final Clock clock;

    UserAccess changeStatus(UserAccess a, AccessStatus to, String reason, Organization org, String module) {
        AccessStatus from = a.getStatus();
        if (to != AccessStatus.ACTIVE && to != AccessStatus.INACTIVE) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);      // INVITED/LOCKED nunca a mano
        }
        Credential c = credentials.findByPersonId(a.getPersonId()).orElse(null);
        if (to == AccessStatus.INACTIVE) {
            if (from == AccessStatus.INACTIVE) {
                throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
            }
            if (reason == null || reason.isBlank()) {
                throw new Exceptions("error.common.reasonRequired", HttpStatus.BAD_REQUEST);
            }
            if (a.getRole() == RoleType.ORG_ADMIN && from == AccessStatus.ACTIVE
                    && accesses.otherOrgAdmins(org.getId(), a.getId()) == 0) {                                   // [V8] ≥1 ORG_ADMIN activo
                throw new Exceptions("error.access.lastOrgAdmin", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            a.setStatus(AccessStatus.INACTIVE);
            a.setStatusReason(reason.trim());
            accesses.saveAndFlush(a);
            if (c != null) {
                refreshTokens.revokeContext(c.getId(), a.getId(), "ACCESS_INACTIVATED");
            }
        } else {
            if (from != AccessStatus.INACTIVE) {
                throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, from);
            }
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(org.getTimezone()));
            if (a.getValidTo() != null && a.getValidTo().isBefore(today)) {
                throw new Exceptions("error.access.expired", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            licenses.assertAvailable(org.getId(), a.getBranchId());                             // [V11]
            boolean neverAccepted = c == null || c.getPasswordHash() == null;
            a.setStatus(neverAccepted ? AccessStatus.INVITED : AccessStatus.ACTIVE);
            a.setStatusReason(null);
            accesses.saveAndFlush(a);
            if (c != null && !neverAccepted && c.getStatus() == AccessStatus.INACTIVE) {
                c.setStatus(AccessStatus.ACTIVE);               // la credencial de una persona ya con contraseña vuelve a valer
                credentials.saveAndFlush(c);
            }
            if (neverAccepted && c != null) {
                persons.findByIdAndOrganizationId(a.getPersonId(), org.getId()).ifPresent(p -> provisioner.sendInvite(org, p, c));
            }
        }
        accessState.invalidate(a.getId());
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("from", from.name());
        diff.put("to", a.getStatus().name());
        diff.put("role", a.getRole().name());
        if (reason != null && !reason.isBlank()) {
            diff.put("reason", reason.trim());
        }
        audit.record(new AuditService.Command(module, "STATUS_CHANGE", "UserAccess", a.getId(), org.getId(), a.getBranchId(), diff));
        return a;
    }

    /**
     * M24 (portal del miembro) · único punto de entrada público de esta clase para paquetes fuera de {@code access.service}:
     * inactiva o reactiva un acceso MEMBER (revocar/rehabilitar el portal de una persona). Reutiliza {@link #changeStatus}
     * tal cual, con lo que el revocado invalida sesión y caché de inmediato (V19) sin duplicar esa lógica en el portal.
     */
    public UserAccess setMemberAccessStatus(UserAccess access, boolean enable, String reason, Organization org) {
        return changeStatus(access, enable ? AccessStatus.ACTIVE : AccessStatus.INACTIVE, reason, org, "PORTAL");
    }
}
