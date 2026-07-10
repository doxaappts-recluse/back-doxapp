package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.util.UUID;

@Entity
@Table(
        name = "contract_module_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contract_module_permission",
                        columnNames = {
                                "contract_module_id",
                                "permission_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_cmp_contract_module",
                        columnList = "contract_module_id"
                ),
                @Index(
                        name = "idx_cmp_permission",
                        columnList = "permission_id"
                )
        }
)
@Getter
@Setter
public class ContractModulePermission extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // =========================
    // CONTRACT MODULE
    // =========================

    /**
     * Módulo habilitado dentro
     * del contrato de la sede.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "contract_module_id",
            nullable = false
    )
    private ContractModule contractModule;

    // =========================
    // PERMISSION
    // =========================

    /**
     * Permiso disponible
     * para ese módulo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permission_id",
            nullable = false
    )
    private Permission permission;

}