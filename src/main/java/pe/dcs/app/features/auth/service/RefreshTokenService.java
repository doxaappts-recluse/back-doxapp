package pe.dcs.app.features.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.auth.domain.RefreshToken;
import pe.dcs.app.features.auth.repo.RefreshTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh rotativo con detección de reúso (spec 00 §5): cada refresh se usa una sola vez y entrega otro de la misma
 * familia. Presentar uno ya usado/revocado revoca TODA la familia (el token pudo ser robado).
 * No abre transacciones propias: se ejecuta dentro de la de AuthenticationService.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock,
                               @Value("${app.jwt.refresh-days:7}") long refreshDays) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = Duration.ofDays(refreshDays);
    }

    /** Refresh emitido: valor en claro (se entrega una vez) y familia. */
    public record Issued(String rawToken, UUID familyId, Instant expiresAt) {
    }

    public enum RotationStatus { OK, REUSED, INVALID }

    public record Rotation(RotationStatus status, Issued issued, UUID credentialId, UUID contextId, UUID branchId, UUID familyId) {
        static Rotation invalid() {
            return new Rotation(RotationStatus.INVALID, null, null, null, null, null);
        }
    }

    /** Abre una sesión nueva (familia nueva). */
    public Issued startSession(UUID credentialId, UUID contextId, UUID branchId, ClientInfo client) {
        return persist(UUID.randomUUID(), credentialId, contextId, branchId, client);
    }

    /** Usa un refresh: lo marca usado y emite el siguiente de la misma familia; detecta reúso. */
    public Rotation rotate(String rawToken, ClientInfo client) {
        if (rawToken == null || rawToken.isBlank()) {
            return Rotation.invalid();
        }
        Instant now = clock.instant();
        Optional<RefreshToken> found = repository.findByTokenHash(TokenCodec.sha256Hex(rawToken));
        if (found.isEmpty()) {
            return Rotation.invalid();
        }
        RefreshToken current = found.get();
        if (current.getUsedAt() != null) {
            // reúso de un token ya consumido: pudo ser robado → se quema toda la familia
            repository.revokeFamily(current.getFamilyId(), now, "REUSE");
            return new Rotation(RotationStatus.REUSED, null, current.getCredentialId(), current.getContextId(), current.getBranchId(), current.getFamilyId());
        }
        if (current.getRevokedAt() != null) {
            // sesión ya cerrada (logout, cambio de clave, reúso previo): simplemente no sirve
            return Rotation.invalid();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            return Rotation.invalid();
        }
        current.setUsedAt(now);
        repository.save(current);
        Issued next = persist(current.getFamilyId(), current.getCredentialId(), current.getContextId(), current.getBranchId(), client);
        return new Rotation(RotationStatus.OK, next, current.getCredentialId(), current.getContextId(), current.getBranchId(), current.getFamilyId());
    }

    /** Revoca la sesión (familia) a la que pertenece el refresh presentado. Idempotente. */
    public Optional<UUID> revokeByToken(String rawToken, UUID onlyIfCredentialId, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(TokenCodec.sha256Hex(rawToken))
                .filter(t -> onlyIfCredentialId == null || t.getCredentialId().equals(onlyIfCredentialId))
                .map(t -> {
                    repository.revokeFamily(t.getFamilyId(), clock.instant(), reason);
                    return t.getFamilyId();
                });
    }

    public void revokeFamily(UUID familyId, String reason) {
        repository.revokeFamily(familyId, clock.instant(), reason);
    }

    /** Cierra las sesiones de un solo acceso (contexto) de la persona. */
    public int revokeContext(UUID credentialId, UUID contextId, String reason) {
        return repository.revokeAllOfContext(credentialId, contextId, clock.instant(), reason);
    }

    /** Revoca todas las sesiones de una credencial (cambio de contraseña, desactivación). */
    public int revokeAll(UUID credentialId, String reason) {
        return repository.revokeAllOfCredential(credentialId, clock.instant(), reason);
    }

    /** Sesiones abiertas: el refresh vigente de cada familia. */
    public List<RefreshToken> liveSessions(UUID credentialId) {
        return repository.findLiveSessions(credentialId, clock.instant());
    }

    private Issued persist(UUID familyId, UUID credentialId, UUID contextId, UUID branchId, ClientInfo client) {
        Instant now = clock.instant();
        String raw = TokenCodec.newOpaqueToken();
        RefreshToken t = new RefreshToken();
        t.setFamilyId(familyId);
        t.setCredentialId(credentialId);
        t.setContextId(contextId);
        t.setBranchId(branchId);
        t.setTokenHash(TokenCodec.sha256Hex(raw));
        t.setIssuedAt(now);
        t.setExpiresAt(now.plus(ttl));
        t.setIp(client == null ? null : client.ip());
        t.setUserAgent(client == null ? null : client.userAgent());
        repository.save(t);
        return new Issued(raw, familyId, t.getExpiresAt());
    }
}
