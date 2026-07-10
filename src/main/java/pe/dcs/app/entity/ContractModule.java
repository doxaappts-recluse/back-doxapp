package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "contract_modules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contract_module",
                        columnNames = {
                                "contract_id",
                                "module_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_cm_contract",
                        columnList = "contract_id"
                ),
                @Index(
                        name = "idx_cm_module",
                        columnList = "module_id"
                )
        }
)
@Getter
@Setter
public class ContractModule extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // =========================
    // CONTRACT
    // =========================

    /**
     * Contrato activo de una sede.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "contract_id",
            nullable = false
    )
    private Contract contract;

    // =========================
    // MODULE
    // =========================

    /**
     * Módulo habilitado por contrato.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "module_id",
            nullable = false
    )
    private Module module;

    // =========================
    // STATUS
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    private Instant enabledAt;

    private Instant disabledAt;

    // =========================
    // DOMAIN METHODS
    // =========================

    public boolean isActive(){
        return status == StatusType.ACTIVE;
    }

    public void enable(){
        status = StatusType.ACTIVE;
        enabledAt = Instant .now();
    }

    public void disable(){
        status = StatusType.INACTIVE;
        disabledAt = Instant .now();
    }

}