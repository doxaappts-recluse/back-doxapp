package pe.dcs.app.features.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.features.auth.domain.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now, String reason);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.credentialId = :credentialId and t.revokedAt is null")
    int revokeAllOfCredential(UUID credentialId, Instant now, String reason);

    /** Cierra las sesiones de UN contexto (acceso) de la credencial; las de otros accesos de la persona siguen. */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.credentialId = :credentialId and t.contextId = :contextId and t.revokedAt is null")
    int revokeAllOfContext(UUID credentialId, UUID contextId, Instant now, String reason);

    /** Un renglón por sesión: el token vigente de cada familia (sin usar, sin revocar, no vencido). */
    @Query("select t from RefreshToken t where t.credentialId = :credentialId and t.usedAt is null and t.revokedAt is null and t.expiresAt > :now order by t.issuedAt desc")
    List<RefreshToken> findLiveSessions(UUID credentialId, Instant now);
}
