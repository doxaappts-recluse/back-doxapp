package pe.dcs.app.features.announcement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.Auditable;

import java.time.Instant;
import java.util.UUID;

/** M22 · anuncio de la plataforma hacia las organizaciones (solo lo administra N1). */
@Getter
@Setter
@Entity
@Table(name = "platform_announcement")
public class PlatformAnnouncement extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    /** INFO | MAINTENANCE | INCIDENT */
    @Column(name = "severity", nullable = false, length = 12)
    private String severity;

    /** ALL | ORGS | PLANS */
    @Column(name = "audience_type", nullable = false, length = 10)
    private String audienceType = "ALL";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "audience_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] audienceIds = new UUID[0];

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "dismissible", nullable = false)
    private boolean dismissible = true;

    @Column(name = "portal_visible", nullable = false)
    private boolean portalVisible;

    /** DRAFT | PUBLISHED | CANCELLED */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "DRAFT";

    @Column(name = "published_at")
    private Instant publishedAt;
}
