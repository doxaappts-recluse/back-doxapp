package pe.dcs.app.features.access.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.shared.domain.Auditable;

import java.time.Instant;
import java.util.UUID;

/**
 * Spec 00 §5 · credencial. Exactamente un titular (CHECK en BD): staff (sin organización) o persona (con organización).
 * password_hash es nulo mientras la invitación no se acepta (no existe contraseña inicial fija).
 */
@Getter
@Setter
@Entity
@Table(name = "credential")
public class Credential extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "username", nullable = false, length = 160)
    private String username;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccessStatus status = AccessStatus.INVITED;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "mfa_secret_enc", length = 255)
    private String mfaSecretEnc;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** Id del titular: staffId o personId (es el "actor" que se audita). */
    public UUID ownerId() {
        return actorType == ActorType.STAFF ? staffId : personId;
    }

    public boolean isLockedNow(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
