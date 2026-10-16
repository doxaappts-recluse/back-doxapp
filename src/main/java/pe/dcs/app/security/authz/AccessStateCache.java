package pe.dcs.app.security.authz;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.PlatformStaff;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.repo.PlatformStaffRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.security.AuthenticatedActor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ¿Sigue vigente el acceso con el que se emitió el token? Se consulta en cada petición con una caché de 15 s,
 * de modo que desactivar un acceso o suspender la organización surte efecto en segundos, no hasta que venza el JWT (15 min).
 * M05 llama {@link #invalidate(UUID)} / {@link #invalidateStaff(UUID)} al cambiar el estado, rol o vigencia de un acceso.
 */
@Component
@RequiredArgsConstructor
public class AccessStateCache {

    static final Duration TTL = Duration.ofSeconds(15);

    private record Entry(boolean effective, Instant loadedAt) {
    }

    private final UserAccessRepository accesses;
    private final PlatformStaffRepository staffRepository;
    private final OrganizationRepository organizations;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

    public boolean isEffective(AuthenticatedActor actor) {
        if (actor.contextId() == null || (!actor.isStaff() && actor.organizationId() == null)) {
            return false;
        }
        Instant now = clock.instant();
        Entry e = cache.get(actor.contextId());
        if (e != null && e.loadedAt().plus(TTL).isAfter(now)) {
            return e.effective();
        }
        boolean effective = actor.isStaff() ? loadStaff(actor) : load(actor, now);
        cache.put(actor.contextId(), new Entry(effective, now));
        return effective;
    }

    public void invalidate(UUID accessId) {
        cache.remove(accessId);
    }

    /** M05: inactivar o cambiar el rol de una persona del personal de plataforma (ctxId de su token = staffId). */
    public void invalidateStaff(UUID staffId) {
        cache.remove(staffId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    /** Personal de plataforma: sigue ACTIVE y con el mismo rol con el que se emitió el token (un cambio surte efecto en ≤15 s). */
    private boolean loadStaff(AuthenticatedActor actor) {
        PlatformStaff s = staffRepository.findById(actor.contextId()).orElse(null);
        return s != null && s.getStatus() == AccessStatus.ACTIVE && s.getStaffRole().toRoleType() == actor.role();
    }

    private boolean load(AuthenticatedActor actor, Instant now) {
        UserAccess access = accesses.findByIdAndOrganizationId(actor.contextId(), actor.organizationId()).orElse(null);
        Organization org = organizations.findById(actor.organizationId()).orElse(null);
        if (access == null || org == null || !org.getStatus().allowsLogin()) {
            return false;
        }
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of(org.getTimezone()));
        return access.isEffective(today);
    }
}
