package pe.dcs.app.features.support.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.TenantScoped;

import java.time.Instant;
import java.util.UUID;

/**
 * M22 · acceso asistido (D3): único mecanismo por el que personal de plataforma entra temporalmente a los módulos
 * de configuración de una organización. Nace de un caso de soporte abierto, lo aprueba/rechaza/revoca el ORG_ADMIN
 * de esa organización, y expira solo ({@code expiresAt}, máximo 4 h desde {@code startsAt}). Ver
 * {@code AssistedAccessService} para el ciclo de vida completo y {@code AuthorizationService} para cómo el alcance
 * ({@code scope}, códigos de módulo) reemplaza temporalmente el chequeo normal de nivel para el personal de
 * plataforma mientras el grant está ACTIVE.
 */
@Getter
@Setter
@Entity
@Table(name = "assisted_access_grant")
public class AssistedAccessGrant extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scope", nullable = false, updatable = false, columnDefinition = "text[]")
    private String[] scope = new String[0];

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "denied_by")
    private UUID deniedBy;

    @Column(name = "denied_reason", length = 500)
    private String deniedReason;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    /** PENDING | ACTIVE | EXPIRED | REVOKED | DENIED */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "PENDING";

    public boolean isLiveActive(Instant now) {
        return "ACTIVE".equals(status) && expiresAt != null && now.isBefore(expiresAt);
    }
}
