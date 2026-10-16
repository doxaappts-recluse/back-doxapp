package pe.dcs.app.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Núcleo 01 §1 · Toda tabla de tenant lleva organization_id (NOT NULL, indexado). */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantScoped extends Auditable {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
}
