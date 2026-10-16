package pe.dcs.app.features.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.features.auth.domain.OneTimeToken;
import pe.dcs.app.features.auth.domain.TokenPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    Optional<OneTimeToken> findByTokenHash(String tokenHash);

    /** Invalida los tokens pendientes del mismo propósito antes de emitir uno nuevo. */
    @Modifying
    @Query("update OneTimeToken t set t.usedAt = :now where t.credentialId = :credentialId and t.purpose = :purpose and t.usedAt is null")
    int invalidatePending(UUID credentialId, TokenPurpose purpose, Instant now);
}
