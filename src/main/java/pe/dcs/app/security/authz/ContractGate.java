package pe.dcs.app.security.authz;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.contract.domain.ContractRepository;
import pe.dcs.app.features.dashboard.service.DashboardCache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Núcleo 01 §2 · gate de contrato: {@code enabled(org, moduleCode)} con caché de 60 s por organización.
 * Los módulos BASE siempre están habilitados; los CONTRACTABLE requieren estar en un contrato ACTIVE.
 * M03 debe llamar {@link #invalidate(UUID)} en cada cambio de estado o de módulos del contrato.
 */
@Component
@RequiredArgsConstructor
public class ContractGate {

    static final Duration TTL = Duration.ofSeconds(60);

    private record Snapshot(boolean hasActiveContract, Set<String> modules, Instant loadedAt) {
    }

    private final ContractRepository contracts;
    private final DashboardCache dashboardCache;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Snapshot> cache = new ConcurrentHashMap<>();

    public boolean hasActiveContract(UUID orgId) {
        return snapshot(orgId).hasActiveContract();
    }

    /** ¿Está el módulo CONTRATABLE incluido en el contrato ACTIVE de la organización? */
    public boolean enabled(UUID orgId, String moduleCode) {
        return snapshot(orgId).modules().contains(moduleCode);
    }

    public void invalidate(UUID orgId) {
        cache.remove(orgId);
        dashboardCache.clear();
    }

    private Snapshot snapshot(UUID orgId) {
        Instant now = clock.instant();
        Snapshot s = cache.get(orgId);
        if (s != null && s.loadedAt().plus(TTL).isAfter(now)) {
            return s;
        }
        Snapshot fresh = new Snapshot(contracts.existsActive(orgId), Set.copyOf(contracts.findActiveModuleCodes(orgId)), now);
        cache.put(orgId, fresh);
        return fresh;
    }
}
