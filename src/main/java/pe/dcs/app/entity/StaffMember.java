package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.hr.HrContractType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ficha de empleado (catálogo por sede, no delegable — ver
 * HrAccessGuard.assertCanCreateStaff, mismo criterio que
 * InventoryItem/ReservableSpace). Vinculada a una Person existente
 * (buscada por DNI, ver HrServiceImpl.findPersonByDni), no crea una
 * Person nueva.
 *
 * Se permiten varios registros por persona a lo largo del tiempo
 * (recontrataciones) — a diferencia de Membership, acá no hay
 * concepto de "vigente" que resolver: cada fila es un periodo
 * laboral independiente con su propia hireDate/terminationDate.
 */
@Entity
@Table(
        name = "staff_members",
        indexes = {
                @Index(name = "idx_staff_member_branch", columnList = "branch_id"),
                @Index(name = "idx_staff_member_person", columnList = "person_id"),
                @Index(name = "idx_staff_member_status", columnList = "status")
        }
)
@Getter
@Setter
public class StaffMember extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false)
    private HrContractType contractType;

    @Column(name = "base_salary", precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;
}
