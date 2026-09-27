package pe.dcs.app.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Núcleo 01 §1 · Tenant + sede. */
@Getter
@Setter
@MappedSuperclass
public abstract class BranchScoped extends TenantScoped {

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;
}
