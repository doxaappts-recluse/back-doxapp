package pe.dcs.app.features.integration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.Auditable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Integración de una organización. Los secretos viven cifrados (AES-256-GCM) en {@code secretsEnc} y no se devuelven nunca. */
@Getter
@Setter
@Entity
@Table(name = "integration_config")
public class IntegrationConfig extends Auditable {

    public static final String UNCONFIGURED = "UNCONFIGURED";
    public static final String TESTING = "TESTING";
    public static final String ACTIVE = "ACTIVE";
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private IntegrationProvider provider;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settings = new HashMap<>();

    @Column(name = "secrets_enc")
    private String secretsEnc;

    @Column(name = "status", nullable = false, length = 20)
    private String status = TESTING;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;
}
