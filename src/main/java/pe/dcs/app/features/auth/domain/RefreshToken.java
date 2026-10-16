package pe.dcs.app.features.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token rotativo. Cada uso emite otro token de la misma familia; presentar un token ya usado o
 * revocado revoca TODA la familia (detección de reúso, spec 00 §5). Solo se guarda el hash.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    /** UserAccess.id (personas) o PlatformStaff.id (staff): el contexto con el que se emitió. */
    @Column(name = "context_id")
    private UUID contextId;

    /** Sede de trabajo con la que se emitió (ORG_ADMIN elige una; los roles de sede llevan la suya). */
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 30)
    private String revokeReason;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    public boolean isActive(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
}
