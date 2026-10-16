package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.auth.service.RefreshTokenService;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.security.authz.AccessStateCache;
import pe.dcs.app.shared.audit.AuditService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * [T17] Pasa a INACTIVE los accesos cuya vigencia ({@code validTo}) ya terminó, con el "hoy" de la zona horaria de cada
 * organización, y cierra sus sesiones. Aunque el job no haya corrido, un acceso vencido ya no vale
 * ({@code UserAccess.isEffective}); el job solo deja el estado al día y libera la licencia.
 */
@Service
@RequiredArgsConstructor
public class AccessExpiryService {

    private final UserAccessRepository accesses;
    private final OrganizationRepository organizations;
    private final CredentialRepository credentials;
    private final RefreshTokenService refreshTokens;
    private final AccessStateCache accessState;
    private final AuditService audit;
    private final Clock clock;

    /** Devuelve cuántos accesos se inactivaron. */
    @Transactional
    public int run() {
        Map<UUID, Organization> orgs = new HashMap<>();
        int n = 0;
        for (UserAccess a : accesses.findExpiryCandidates()) {
            Organization o = orgs.computeIfAbsent(a.getOrganizationId(), id -> organizations.findById(id).orElse(null));
            if (o == null) {
                continue;
            }
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(o.getTimezone()));
            if (a.getValidTo() == null || !a.getValidTo().isBefore(today)) {
                continue;
            }
            a.setStatus(AccessStatus.INACTIVE);
            a.setStatusReason("Vigencia vencida");
            accesses.save(a);
            List<Credential> cs = credentials.findByPersonId(a.getPersonId()).stream().toList();
            cs.forEach(c -> refreshTokens.revokeContext(c.getId(), a.getId(), "ACCESS_EXPIRED"));
            accessState.invalidate(a.getId());
            audit.record(new AuditService.Command("SUPPORT_TEAM", "EXPIRE", "UserAccess", a.getId(), a.getOrganizationId(), a.getBranchId(),
                    Map.of("role", a.getRole().name(), "validTo", a.getValidTo().toString())));
            n++;
        }
        return n;
    }
}
