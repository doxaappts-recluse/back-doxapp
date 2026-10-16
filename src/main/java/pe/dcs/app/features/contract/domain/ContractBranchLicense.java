package pe.dcs.app.features.contract.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** M03 · Reparto de licencias por sede (solo contratos ORGANIZATION + ALLOCATED). */
@Getter
@Setter
@Entity
@Table(name = "contract_branch_license")
public class ContractBranchLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "allocated_licenses", nullable = false)
    private int allocatedLicenses;
}
