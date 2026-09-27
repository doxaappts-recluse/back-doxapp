package pe.dcs.app.features.organization.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** M02 [V8] · Slug anterior de una organización: durante 90 días redirige (301) al slug vigente. */
@Getter
@Setter
@Entity
@Table(name = "slug_redirect")
public class SlugRedirect {

    @Id
    @Column(name = "old_slug", length = 30)
    private String oldSlug;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "until_at", nullable = false)
    private Instant untilAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
