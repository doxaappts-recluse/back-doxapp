package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.http.HttpStatus;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.auditable.Auditable;

import java.util.UUID;

@Entity
@Table(
        name = "contract_branch_licenses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contract_branch_license",
                        columnNames = {
                                "contract_id",
                                "branch_id"
                        }
                )
        },
        indexes = {
                @Index(name = "idx_cbl_contract", columnList = "contract_id"),
                @Index(name = "idx_cbl_branch", columnList = "branch_id"),
                @Index(name = "idx_cbl_contract_branch", columnList = "contract_id,branch_id")
        }
)
@Getter
@Setter
public class ContractBranchLicense extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /**
     * Contrato de organización.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "contract_id",
            nullable = false
    )
    private Contract contract;

    /**
     * Sede a la que se asignan
     * las licencias.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false
    )
    private Branch branch;

    /**
     * Cantidad de licencias
     * reservadas para la sede.
     */
    @Column(nullable = false)
    private Integer allocatedLicenses;

    // =========================================================
    // HELPERS
    // =========================================================

    public boolean hasLicenses() {
        return allocatedLicenses > 0;
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    @PrePersist
    @PreUpdate
    private void validate() {

        if (allocatedLicenses == null || allocatedLicenses < 0) {
            throw new Exceptions(
                    "error.licenciasAsignadasMayorIgualCero",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!contract.isOrganizationScope()) {
            throw new Exceptions(
                    "error.distribucionLicenciasSoloContratoOrganizacion",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!branch.getOrganization().getId()
                .equals(contract.getOrganization().getId())) {

            throw new Exceptions(
                    "error.sedeNoPerteneceOrganizacion",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

}